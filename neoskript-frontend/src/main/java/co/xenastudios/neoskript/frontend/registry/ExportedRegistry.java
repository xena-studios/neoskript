/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.source.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A registry built from a {@link SyntaxExport}, without the server that wrote it: what the in-browser parser checks
 * scripts against. It parses as the server's registry does, but for what the export can't hold.
 *
 * <ul>
 *   <li>Checks syntax makes while it's parsed. NeoSkript only registers those for Skript's test syntax.
 *   <li>Literal parsers. The export lists the words a type's parsers take among those it tried; other words where
 *       an item type, entity type or other open type is expected might be one, as in the frontend's tests, but
 *       aren't certainly one.
 *   <li>Event values of a kind of entity ({@code event-zombie}): those are allowed in any event.
 * </ul>
 */
public final class ExportedRegistry {

    /** What an open type's check throws for words the export doesn't say it takes, which might still be one. */
    private static final RuntimeException UNKNOWN = new RuntimeException("not in the export", null,
            false, false) {};

    private ExportedRegistry() {}

    public static SyntaxRegistry build(SyntaxExport export) {
        SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
        SyntaxExport.Parser parser = export.parser();

        Map<String, Pattern> experiments = new LinkedHashMap<>();
        parser.experiments().forEach((name, regex) -> experiments.put(name, Pattern.compile(regex)));
        builder.experimentNames(name -> experimentName(experiments, name));

        Set<String> eventDefaults = new HashSet<>();
        Set<String> defaultValues = new HashSet<>();
        for (SyntaxExport.Type type : export.types()) {
            SyntaxExport.TypeParser info = type.parser();
            builder.type(new TypeInfo(type.id(), type.name(), Set.copyOf(type.literals()),
                    info != null && info.openLiterals()));
        }
        for (SyntaxExport.Type type : export.types()) {
            SyntaxExport.TypeParser info = type.parser();
            if (info == null) {
                continue;
            }
            for (String name : info.names()) {
                builder.typeName(type.id(), name, false);
            }
            for (String name : info.pluralNames()) {
                builder.typeName(type.id(), name, true);
            }
            if (!info.supertypes().isEmpty()) {
                builder.assignable(type.id(), info.supertypes().toArray(String[]::new));
            }
            if (info.strict()) {
                builder.strict(type.id());
            }
            if (info.enumerable()) {
                builder.enumerable(type.id());
            }
            if (info.eventDefault()) {
                eventDefaults.add(type.id());
            }
            if (info.defaultValue()) {
                defaultValues.add(type.id());
            }
            if (info.parsesAnyWords()) {
                builder.parses(type.id(), text -> true);
            } else if (!info.parsedWords().isEmpty()) {
                Set<String> words = Set.copyOf(info.parsedWords());
                builder.parses(type.id(), text -> words.contains(SyntaxRegistry.normalize(text)));
            }
            if (!info.literalWords().isEmpty()) {
                Set<String> words = Set.copyOf(info.literalWords());
                builder.openLiterals(type.id(), text -> {
                    if (words.contains(SyntaxRegistry.normalize(text))) {
                        return true;
                    }
                    // Not known either way: as for a check that needs a server, the words might be one.
                    throw UNKNOWN;
                });
            }
        }
        for (SyntaxExport.Conversion conversion : parser.conversions()) {
            builder.convertible(conversion.from(), conversion.to());
            if (conversion.placeholderOnly()) {
                builder.placeholderConversion(conversion.from(), conversion.to());
            }
        }
        parser.typeProperties().forEach((property, types) -> builder.typeProperty(property,
                types.toArray(String[]::new)));

        Values values = new Values(parser, eventDefaults, defaultValues);
        for (SyntaxExport.Element element : export.elements()) {
            SyntaxKind kind = SyntaxKind.valueOf(element.kind().toUpperCase(Locale.ROOT));
            SyntaxExport.ElementParser info = element.parser();
            List<RegexRole> roles = new ArrayList<>();
            for (String role : info.regexRoles()) {
                roles.add(RegexRole.valueOf(role));
            }
            List<co.xenastudios.neoskript.frontend.pattern.CompiledPattern> patterns = new ArrayList<>();
            for (String pattern : element.patterns()) {
                patterns.add(PatternCompiler.compile(pattern));
            }
            builder.syntax(new SyntaxInfo(kind, element.id(), patterns, element.returnType(), roles));
            if (info.delays()) {
                builder.delays(kind, element.id());
            }
            for (int pattern : info.stops()) {
                builder.stops(kind, element.id(), pattern);
            }
            if (info.ends()) {
                builder.ends(kind, element.id());
            }
            if (info.suppressesWarnings()) {
                builder.suppressesWarnings(kind, element.id());
            }
            if (info.sectionOnly()) {
                builder.sectionOnly(element.id());
            }
            if (info.experiment() != null) {
                builder.experimental(element.id(), info.experiment(), info.experimentSilent());
            }
            if (info.property() != null) {
                SyntaxExport.Property property = info.property();
                builder.propertySyntax(element.id(), new SyntaxRegistry.PropertySyntax(property.argument(),
                        tags -> propertyName(property, tags), property.counts()));
            }
            values.add(kind, element.id(), info);
        }
        for (SyntaxExport.Function function : export.functions()) {
            builder.function(new FunctionInfo(function.name(), function.parameters(), function.returnType(),
                    function.returnsPlural()));
        }
        builder.eventValues(values);
        SyntaxRegistry registry = builder.build();
        values.registry = registry;
        return registry;
    }

    /** As NeoSkript's {@code ScriptFeatures.codeName}: the experiment a script means by {@code name}. */
    private static String experimentName(Map<String, Pattern> experiments, String name) {
        String normal = Text.lower(name.strip()).replaceAll("\\s+", " ");
        if (experiments.containsKey(normal)) {
            return normal;
        }
        for (Map.Entry<String, Pattern> experiment : experiments.entrySet()) {
            if (experiment.getValue().matcher(normal).matches()) {
                return experiment.getKey();
            }
        }
        return normal;
    }

    private static String propertyName(SyntaxExport.Property property, Set<String> tags) {
        for (Map.Entry<String, String> byTag : property.byTag().entrySet()) {
            if (tags.contains(byTag.getKey())) {
                return byTag.getValue();
            }
        }
        return property.otherwise();
    }

    /** The event values the export lists, as the server's registry tells the parser about them. */
    private static final class Values implements EventValues {

        private final List<ScopeData> scopes = new ArrayList<>();
        private final ScopeData command;
        private final ScopeData function;
        private final Set<String> eventDefaults;
        private final Set<String> defaultValues;
        private final Map<String, ScopeData> events = new HashMap<>();
        private final Set<String> eventValues = new HashSet<>();
        private final Set<String> keeping = new HashSet<>();
        /** Elements that are the compiler's own, or events: those don't fill left-out placeholders from events. */
        private final Set<String> notFactories = new HashSet<>();
        SyntaxRegistry registry;

        Values(SyntaxExport.Parser parser, Set<String> eventDefaults, Set<String> defaultValues) {
            for (SyntaxExport.Scope scope : parser.scopes()) {
                scopes.add(new ScopeData(scope));
            }
            this.command = scope(parser.commandScope());
            this.function = scope(parser.functionScope());
            this.eventDefaults = eventDefaults;
            this.defaultValues = defaultValues;
        }

        private ScopeData scope(int index) {
            return index < 0 || index >= scopes.size() ? null : scopes.get(index);
        }

        void add(SyntaxKind kind, String id, SyntaxExport.ElementParser info) {
            String key = kind + ":" + id;
            if (kind == SyntaxKind.EVENT) {
                ScopeData scope = scope(info.scope());
                if (scope != null) {
                    events.put(id, scope);
                }
            }
            if (info.eventValue()) {
                eventValues.add(key);
            }
            if (info.keepsEventValues()) {
                keeping.add(key);
            }
            if (info.core() || kind == SyntaxKind.EVENT || kind == SyntaxKind.STRUCTURE) {
                notFactories.add(key);
            }
        }

        @Override
        public Scope event(SyntaxInfo event) {
            return events.get(event.id());
        }

        @Override
        public Scope function() {
            return function;
        }

        @Override
        public Scope command() {
            return command;
        }

        @Override
        public Scope structure(SyntaxInfo structure) {
            return null;
        }

        @Override
        public boolean keepsEventValues(SyntaxInfo header) {
            return keeping.contains(header.kind() + ":" + header.id());
        }

        /** What code in some events can use, as the server's scope for their classes answers. */
        private final class ScopeData implements Scope {

            private final Set<String> values;
            private final Set<String> plurals;
            private final Set<String> defaults;

            ScopeData(SyntaxExport.Scope scope) {
                this.values = Set.copyOf(scope.values());
                this.plurals = Set.copyOf(scope.plurals());
                this.defaults = Set.copyOf(scope.defaults());
            }

            @Override
            public boolean allows(SyntaxInfo syntax, TypeRef type, String words) {
                if (!eventValues.contains(syntax.kind() + ":" + syntax.id())) {
                    return true;
                }
                if (values.isEmpty() && plurals.isEmpty()) {
                    return false;
                }
                String id = type.type().id();
                if (type.plural()) {
                    return plurals.contains(id);
                }
                if (id.equals("entity") && words != null && !namesType(words)) {
                    // Maybe a kind of entity, whose class the server finds with the entity type parser.
                    return true;
                }
                return values.contains(id);
            }

            /** Whether {@code words}, past {@code the} and {@code event-}, name a type, as the server checks. */
            private boolean namesType(String words) {
                String name = Text.lower(words.strip());
                for (String prefix : new String[] {"the ", "event-"}) {
                    if (name.startsWith(prefix)) {
                        name = name.substring(prefix.length()).strip();
                    }
                }
                return registry.resolveType(name) != null;
            }

            @Override
            public boolean allowsLeftOut(SyntaxInfo syntax, PlaceholderSpec spec) {
                if (spec.nullable() || spec.types().isEmpty()) {
                    return true;
                }
                List<String> ids = new ArrayList<>();
                for (String name : spec.types()) {
                    TypeRef ref = registry.resolveType(name);
                    if (ref == null || !eventDefaults.contains(ref.type().id())) {
                        return true;
                    }
                    ids.add(ref.type().id());
                }
                if (spec.types().contains("script") || notFactories.contains(syntax.kind() + ":" + syntax.id())) {
                    return true;
                }
                if (ids.size() == 1 && defaultValues.contains(ids.getFirst())) {
                    return true;
                }
                for (String id : ids) {
                    if (defaults.contains(id)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
