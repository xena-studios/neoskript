/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The order Skript's parser tries syntax in ({@code SyntaxRegister.SET_COMPARATOR}): by priority level, then by the
 * complexity score of its most complex pattern, from {@code skript-syntax-order.txt}. Skript breaks ties by identity
 * hash codes, which change from run to run; NeoSkript keeps the order its syntax was registered in for those.
 * Syntax Skript doesn't have (or has under another name) goes with the syntax registered before it.
 */
final class SkriptOrder {

    private record Rank(int level, int score, boolean anyType) {}

    private static final Map<String, Rank> RANKS = load();

    private SkriptOrder() {}

    /** {@code syntaxes}, in the order they were registered, in the order Skript tries them. */
    static List<SyntaxInfo> sort(SyntaxKind kind, List<SyntaxInfo> syntaxes) {
        String prefix = kind.name().toLowerCase(Locale.ROOT) + "\t";
        List<Map.Entry<SyntaxInfo, Rank>> ranked = new ArrayList<>(syntaxes.size());
        Rank previous = null;
        for (SyntaxInfo syntax : syntaxes) {
            Rank rank = rank(prefix, syntax.id());
            if (rank == null) {
                rank = previous;
            }
            previous = rank;
            ranked.add(Map.entry(syntax, rank == null ? new Rank(-1, 0, false) : rank));
        }
        if (ranked.stream().noneMatch(entry -> entry.getValue().level() >= 0)) {
            return syntaxes;
        }
        ranked.sort(Comparator.comparingInt((Map.Entry<SyntaxInfo, Rank> entry) -> entry.getValue().level())
                .thenComparingInt(entry -> entry.getValue().score()));
        return ranked.stream().map(Map.Entry::getKey).toList();
    }

    /** NeoSkript's syntax that's Skript's under another name or kind, as Skript's kind and class. */
    private static final Map<String, String> SKRIPT_NAMES = Map.ofEntries(
            Map.entry("condition\tCondContains", "condition\tPropCondContains"),
            Map.entry("condition\tCondIsEmpty", "condition\tPropCondIsEmpty"),
            Map.entry("expression\tExprAmount", "expression\tPropExprAmount"),
            Map.entry("expression\tExprAmount-recursive", "expression\tExprRecursiveSize"),
            Map.entry("expression\tExprTypedArgument", "expression\tExprArgument"));

    /**
     * NeoSkript's event values, which stand for Skript's many event value expressions and its {@code event-x} and
     * {@code the <entity>}: as they match any type's name, they're tried after everything else.
     */
    private static final java.util.Set<String> LAST = java.util.Set.of("expression\tExprEventValue",
            "expression\tExprEventEntity", "expression\tExprEventEntityBare");

    /**
     * Skript's syntax whose init refuses it outside some events, which NeoSkript only finds when compiling: it's tried
     * just after Skript's event value expressions, which read the same words where it can't be, as Skript then reads
     * them ('inventory action' outside an inventory click).
     */
    private static final java.util.Set<String> AFTER_EVENT_VALUES = java.util.Set.of("expression\tExprClicked");

    /**
     * Whether Skript registers expression {@code id} as returning any type: it's tried wherever a value goes, and once
     * set up says what it gives, which may then not be what's wanted.
     */
    static boolean anyType(String id) {
        Rank rank = rank("expression\t", id);
        return rank != null && rank.anyType();
    }

    /** The rank of Skript's syntax with this id, or of what it's split from ({@code PropExprName-display}). */
    private static Rank rank(String prefix, String id) {
        if (LAST.contains(prefix + id)) {
            return new Rank(Integer.MAX_VALUE, 0, false);
        }
        if (AFTER_EVENT_VALUES.contains(prefix + id)) {
            return new Rank(1, Integer.MAX_VALUE, true);
        }
        // Skript's effect sections ('spawn 10 xp at X'), which NeoSkript has as effects too: Skript tries them before
        // any effect, in their order as sections.
        Rank section = prefix.equals("effect\t") ? RANKS.get("section\t" + id) : null;
        if (section != null) {
            return new Rank(section.level() - 100, section.score(), false);
        }
        String skript = SKRIPT_NAMES.get(prefix + id);
        if (skript != null) {
            return RANKS.get(skript);
        }
        Rank rank = RANKS.get(prefix + id);
        int dash = id.indexOf('-');
        if (rank == null && dash > 0) {
            rank = RANKS.get(prefix + id.substring(0, dash));
        }
        return rank;
    }

    /** Skript's code names for its types that NeoSkript calls otherwise. */
    private static final Map<String, String> TYPE_NAMES = Map.of("entitydata", "entitytype", "entitytype",
            "countedentitytype");

    /**
     * Tells {@code builder} which of NeoSkript's types {@code types} are Skript's and which of those Skript's parser
     * takes as which, from {@code skript-conversions.txt}.
     */
    static void conversions(java.util.Set<String> types, co.xenastudios.neoskript.frontend.registry.SyntaxRegistry.Builder builder) {
        try (InputStream in = SkriptOrder.class.getResourceAsStream("skript-conversions.txt")) {
            if (in == null) {
                return;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.strip().split("\t");
                String from = TYPE_NAMES.getOrDefault(columns[1], columns[1]);
                if (columns[0].equals("type")) {
                    if (types.contains(from) && !from.equals("object")) {
                        builder.skriptType(from);
                    }
                    continue;
                }
                String to = TYPE_NAMES.getOrDefault(columns[2], columns[2]);
                if (types.contains(from) && types.contains(to)) {
                    builder.skriptConversion(from, to);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Rank> load() {
        Map<String, Rank> ranks = new HashMap<>();
        try (InputStream in = SkriptOrder.class.getResourceAsStream("skript-syntax-order.txt")) {
            if (in == null) {
                return ranks;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.strip().split("\t");
                ranks.put(columns[0] + "\t" + columns[1], new Rank(Integer.parseInt(columns[2]), Integer.parseInt(columns[3]),
                        columns.length > 4 && columns[4].equals("object")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ranks;
    }
}
