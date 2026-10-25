/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.registry.EventValues;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.testing.TestEvent;
import java.util.List;
import java.util.Locale;
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
            CoreSyntax.WHILE, CoreSyntax.CATCH_ERRORS, CoreSyntax.SUPPRESS_HINTS, CoreSyntax.SUPPRESS_WARNINGS);
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
        return switch (library.binding(event)) {
            case Library.ServerEvent server -> scope(server.classes());
            // Script events ('on load') and periodical ones have no event values, as the compiler has none.
            case ScriptEvent script -> scope(List.of());
            case Library.Core core when core.id().equals(CoreSyntax.PERIODICAL) -> scope(List.of());
            case null, default -> null;
        };
    }

    @Override
    public Scope function() {
        return scope(List.of());
    }

    @Override
    public Scope command() {
        return scope(List.of(CommandEvent.class));
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

    private Scope scope(List<Class<?>> classes) {
        return scopes.computeIfAbsent(List.copyOf(classes), EventScope::new);
    }

    /** The event values of events of some classes (none outside events). */
    private final class EventScope implements Scope {

        private final List<Class<?>> classes;
        /** Whether each value asked for, by type, plurality and kind, is had. */
        private final Map<String, Boolean> known = new ConcurrentHashMap<>();

        EventScope(List<Class<?>> classes) {
            this.classes = classes;
        }

        @Override
        public boolean allows(SyntaxInfo syntax, TypeRef type, String words) {
            if (!EVENT_VALUES.contains(syntax.id()) || !(library.binding(syntax) instanceof Library.Core)) {
                return true;
            }
            if (classes.isEmpty()) {
                return false;
            }
            Class<?> kind = kind(type, words);
            String key = type.type().id() + (type.plural() ? "*" : "") + (kind == null ? "" : "/" + kind.getName());
            return known.computeIfAbsent(key, k -> has(type.type().id(), kind));
        }

        /** Whether the events have a value of the type, now, before or after the event. */
        private boolean has(String typeId, Class<?> kind) {
            for (int time : new int[] {0, -1, 1}) {
                try {
                    if (library.eventValue(classes, typeId, kind, time) != null) {
                        return true;
                    }
                } catch (Library.ExcludedEventValue excluded) {
                    // Left out on purpose: no value, as Skript's error says if nothing else parses.
                }
            }
            return false;
        }

        /** The kind of value asked for, as {@code TriggerCompiler.kind} works it out. */
        private Class<?> kind(TypeRef ref, String words) {
            if (ref.plural()) {
                Class<?> single = library.javaClass(ref.type().id());
                return single == null || single.isArray() ? null : single.arrayType();
            }
            if (!ref.type().id().equals("entity")) {
                return null;
            }
            String name = words.toLowerCase(Locale.ROOT);
            for (String prefix : new String[] {"the ", "event-"}) {
                if (name.startsWith(prefix)) {
                    name = name.substring(prefix.length()).strip();
                }
            }
            if (library.registry().resolveType(name) != null) {
                return null;
            }
            Class<?> kind = library.kindClass("entitytype", name);
            return kind == null || kind == library.javaClass("entity") ? null : kind;
        }
    }
}
