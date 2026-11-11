/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.EventValues;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.testing.TestEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the parser which event values a trigger's code can use, from the library's event values, as the
 * {@link TriggerCompiler} resolves them. Where the compiler would say there's no such value, the parser tries
 * another reading first, as Skript's event value expressions fail to initialize and its parser backtracks.
 *
 * <p>It errs on the side of allowing: it doesn't know what a filter narrows an event to (which only takes values
 * away), and in a section that may give values of its own, the parser allows any.
 */
final class LibraryEventValues implements EventValues {

    /** Core sections whose code has only the event values of the code around them. */
    private static final Set<String> KEEPING = Set.of(CoreSyntax.CONDITIONAL, CoreSyntax.LOOP, CoreSyntax.FOR,
            CoreSyntax.WHILE, CoreSyntax.CATCH_ERRORS, CoreSyntax.RUN_ON, CoreSyntax.SUPPRESS_HINTS, CoreSyntax.SUPPRESS_WARNINGS);
    /** The expressions that are the event's value of a type. */
    private static final Set<String> EVENT_VALUES = Set.of(CoreSyntax.EVENT_VALUE, CoreSyntax.EVENT_VALUE_ENTITY,
            CoreSyntax.EVENT_ENTITY_BARE);

    private final Library library;
    /** A scope for each set of event classes, which caches what it found. */
    private final Map<List<Class<?>>, Scope> scopes = new ConcurrentHashMap<>();

    LibraryEventValues(Library library) {
        this.library = library;
    }

    @Override
    public Scope event(SyntaxInfo event) {
        List<Class<?>> classes = eventClasses(event);
        if (classes == null) {
            return null;
        }
        String name = library.eventName(event.id());
        return name == null ? scope(classes)
                : named.computeIfAbsent(List.copyOf(classes) + "/" + name, k -> new EventScope(List.copyOf(classes), name));
    }

    /** The scopes of events Skript has a name for, which can say what it says when a value isn't there. */
    private final Map<String, Scope> named = new ConcurrentHashMap<>();

    /** The classes of the events code under {@code event} runs for, or {@code null} if unknown. */
    List<Class<?>> eventClasses(SyntaxInfo event) {
        return switch (library.binding(event)) {
            case Library.ServerEvent server -> server.classes();
            case ScriptEvent script -> List.of(co.xenastudios.neoskript.runtime.ScriptLoadEvent.class);
            case Library.Core core when core.id().equals(CoreSyntax.PERIODICAL)
                    -> List.of(co.xenastudios.neoskript.runtime.PeriodicalEvent.class);
            case null, default -> null;
        };
    }

    @Override
    public Scope function() {
        return scope(List.of());
    }

    @Override
    public Scope command() {
        return scope(commandClasses());
    }

    /** The classes of the events a command's code runs for. */
    List<Class<?>> commandClasses() {
        return List.of(CommandEvent.class);
    }

    /** Whether {@code syntax} is the event's value of a type, which {@link Scope#allows} checks. */
    boolean isEventValue(SyntaxInfo syntax) {
        return EVENT_VALUES.contains(syntax.id()) && library.binding(syntax) instanceof Library.Core;
    }

    /**
     * What code in events of {@code classes} can use, written down for the syntax export: for each type, whether
     * {@link Scope#allows} takes its value, single and plural (for a kind of entity it can't be written down), and
     * whether {@link Scope#allowsLeftOut} finds the event's value of it.
     */
    co.xenastudios.neoskript.frontend.registry.SyntaxExport.Scope export(List<Class<?>> classes) {
        List<String> values = new java.util.ArrayList<>();
        List<String> plurals = new java.util.ArrayList<>();
        List<String> defaults = new java.util.ArrayList<>();
        for (co.xenastudios.neoskript.frontend.registry.TypeInfo type : library.registry().types()) {
            if (has(classes, new TypeRef(type, false))) {
                values.add(type.id());
            }
            if (has(classes, new TypeRef(type, true))) {
                plurals.add(type.id());
            }
            if (library.hasEventDefault(type.id()) && library.eventValue(classes, type.id()) != null) {
                defaults.add(type.id());
            }
        }
        return new co.xenastudios.neoskript.frontend.registry.SyntaxExport.Scope(values, plurals, defaults);
    }

    /** Whether events of {@code classes} have the value {@code type} asks for, as {@link EventScope#allows}. */
    private boolean has(List<Class<?>> classes, TypeRef type) {
        if (classes.isEmpty()) {
            return false;
        }
        Library.EventValueRequest request = library.request(type, null);
        return library.eventValue(classes, request.valueClass(), request.type(), 0, null).getter() != null;
    }

    @Override
    public Scope structure(SyntaxInfo structure) {
        return library.binding(structure) instanceof Library.Core core && core.id().equals(CoreSyntax.TEST)
                ? scope(List.of(TestEvent.class)) : null;
    }

    @Override
    public boolean keepsEventValues(SyntaxInfo header) {
        return library.binding(header) instanceof Library.Core core && KEEPING.contains(core.id());
    }

    /** What code in events of {@code classes} can use. */
    Scope scope(List<Class<?>> classes) {
        return scopes.computeIfAbsent(List.copyOf(classes), c -> new EventScope(c, null));
    }

    /** The event values of events of some classes (none outside events). */
    private final class EventScope implements Scope {

        private final List<Class<?>> classes;
        /** Skript's name for the event, as its errors call it, or {@code null} if unknown. */
        private final String name;
        /** Whether each value asked for, by type, plurality and kind, is had. */
        private final Map<String, Boolean> known = new ConcurrentHashMap<>();

        EventScope(List<Class<?>> classes, String name) {
            this.classes = classes;
            this.name = name;
        }

        @Override
        public String missing(SyntaxInfo syntax, TypeRef type, String words) {
            if (name == null || !isEventValue(syntax) || classes.isEmpty()) {
                return null;
            }
            // As the compiler says it, without using up a value's own error ('Use 'attacker' and/or 'victim'...').
            Library.EventValueRequest request = library.request(type, words);
            Library.EventValueLookup lookup = library.eventValue(classes, request.valueClass(), request.type(), 0, null);
            if (lookup.getter() != null || "multiple".equals(lookup.error())) {
                return null;
            }
            if (!"none".equals(lookup.error())) {
                // Skript's event value uses up its error when it's set up, whatever else is tried after: the parser
                // does now, and the compiler, asking for it next, still gives it.
                library.eventValue(classes, request.valueClass(), request.type(), 0, "");
                library.parserAsked(classes, request.valueClass(), request.type(), lookup.error());
                return lookup.error();
            }
            String description = TriggerCompiler.article(name) + " " + name + " event";
            return request.words() != null ? "There's no " + request.words() + " in " + description + "."
                    : library.eventValue(classes, request.valueClass(), request.type(), 0, description).error();
        }

        @Override
        public boolean allows(SyntaxInfo syntax, TypeRef type, String words) {
            if (!isEventValue(syntax)) {
                return true;
            }
            if (classes.isEmpty()) {
                return false;
            }
            // As the compiler resolves it, now: Skript's event value expressions initialize before a time state.
            Library.EventValueRequest request = library.request(type, words);
            String key = request.valueClass().getName() + "/" + request.type();
            return known.computeIfAbsent(key, k -> library.eventValue(classes, request.valueClass(), request.type(),
                    0, null).getter() != null);
        }

        @Override
        public boolean allowsLeftOut(SyntaxInfo syntax, PlaceholderSpec spec) {
            // As the compiler fills in a library element's: unless nothing but the event's value can, and the event
            // (or function) has none.
            if (spec.nullable() || spec.types().isEmpty()) {
                return true;
            }
            SyntaxRegistry registry = library.registry();
            for (String name : spec.types()) {
                TypeRef ref = registry.resolveType(name);
                if (ref == null || !library.hasEventDefault(ref.type().id())) {
                    return true;
                }
            }
            if (spec.types().contains("script") || !(library.binding(syntax) instanceof ElementFactory<?>)) {
                return true;
            }
            if (spec.types().size() == 1 && library.defaultValue(registry.resolveType(spec.types().getFirst())
                    .type().id()) != null) {
                return true;
            }
            for (String name : spec.types()) {
                String type = registry.resolveType(name).type().id();
                if (known.computeIfAbsent(type + "=", k -> library.eventValue(classes, type) != null)) {
                    return true;
                }
            }
            return false;
        }
    }
}
