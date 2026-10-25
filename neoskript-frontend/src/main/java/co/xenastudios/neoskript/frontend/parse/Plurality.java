/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternElement;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether an expression can be several values, as Skript's {@code isSingle()} has it: list variables, and-lists,
 * functions returning several values, and syntax elements that always are (from Skript's own elements, in
 * {@code plurality.txt}) or are because a plural argument of theirs is ({@code names of {_l::*}}).
 */
final class Plurality {

    /** Element id to {@code S} (always one value), {@code P} (always several) or {@code A} (as its arguments). */
    private static final Map<String, Character> KNOWN = load();

    private Plurality() {}

    static boolean plural(Expr expr, SyntaxRegistry registry) {
        return switch (expr) {
            case Expr.Variable variable -> variable.list();
            case Expr.ListOf list -> list.and() ? list.items().size() > 1 || list.items().stream().anyMatch(i -> plural(i, registry))
                    : list.items().stream().anyMatch(i -> plural(i, registry));
            case Expr.FunctionCall call -> call.function() != null && call.function().returnsPlural();
            case Expr.Syntax syntax -> plural(syntax.syntax(), registry);
            case null, default -> false;
        };
    }

    private static boolean plural(ParsedSyntax syntax, SyntaxRegistry registry) {
        String id = syntax.syntax().id();
        int dash = id.indexOf('-');
        Character known = KNOWN.get(dash < 0 ? id : id.substring(0, dash));
        if (known == null || known != 'A') {
            // Elements Skript has no fixed answer for are taken as one value, so nothing is wrongly refused.
            return known != null && known == 'P';
        }
        // Several values when a plural argument is several values, as Skript's property expressions.
        boolean[] pluralSpecs = PLURAL_SPECS.computeIfAbsent(syntax.syntax().patterns().get(syntax.pattern()),
                pattern -> pluralSpecs(pattern, registry));
        List<Object> values = syntax.match().values();
        for (int i = 0; i < values.size() && i < pluralSpecs.length; i++) {
            if (pluralSpecs[i] && values.get(i) instanceof Expr argument && plural(argument, registry)) {
                return true;
            }
        }
        return false;
    }

    /** For each placeholder of a pattern, whether it takes several values. */
    private static final Map<CompiledPattern, boolean[]> PLURAL_SPECS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static boolean[] pluralSpecs(CompiledPattern pattern, SyntaxRegistry registry) {
        PlaceholderSpec[] specs = new PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        boolean[] plural = new boolean[specs.length];
        for (int i = 0; i < specs.length; i++) {
            plural[i] = specs[i] != null && pluralSpec(specs[i], registry);
        }
        return plural;
    }

    private static boolean pluralSpec(PlaceholderSpec spec, SyntaxRegistry registry) {
        for (String type : spec.types()) {
            TypeRef ref = registry.resolveType(type);
            if (ref != null && ref.plural()) {
                return true;
            }
        }
        return false;
    }

    private static void collect(PatternElement element, PlaceholderSpec[] specs) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> specs[placeholder.index()] = placeholder.spec();
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collect(e, specs));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body(), specs));
            case PatternElement.Optional optional -> collect(optional.choice(), specs);
            default -> {}
        }
    }

    private static Map<String, Character> load() {
        Map<String, Character> known = new HashMap<>();
        try (InputStream in = Plurality.class.getResourceAsStream("plurality.txt")) {
            if (in == null) {
                return known;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                int equals = line.indexOf('=');
                if (!line.startsWith("#") && equals > 0) {
                    known.put(line.substring(0, equals).strip(), line.substring(equals + 1).strip().charAt(0));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return known;
    }
}
