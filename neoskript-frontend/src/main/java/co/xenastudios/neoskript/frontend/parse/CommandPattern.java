/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.List;

/**
 * What a command or subcommand expects after its name: {@code <player> [<text>]}, words, optional parts and
 * choices such as {@code (give|take)}.
 */
public final class CommandPattern {

    private CommandPattern() {}

    public sealed interface Part {}

    /** A word typed as is, like {@code help} in {@code /kit help}. */
    public record Word(String text) implements Part {}

    /**
     * An argument, {@code <type>}.
     *
     * @param name its name in {@code <name: type>}, which makes it the local variable {@code {_name}}, or
     *     {@code null}
     * @param type the id of its type
     * @param defaultValue the value it has when it's left out, as an expression ({@code <player = %player%>}),
     *     or {@code null}
     * @param defaultText the value it has when it's left out, as text to parse ({@code <text = home>}); or
     *     the default value expression's source, which its positions refer to; or {@code null}
     * @param min the smallest number it accepts, or {@code null}
     * @param max the largest number it accepts, or {@code null}
     */
    public record Argument(String name, String type, boolean plural, Expr defaultValue, String defaultText,
            Double min, Double max) implements Part {}

    /** {@code [...]}: parts that can be left out. */
    public record Optional(List<Part> parts) implements Part {

        public Optional {
            parts = List.copyOf(parts);
        }
    }

    /**
     * {@code (a|b)} or {@code [a|b]}: one of several alternatives. Its value, a choice argument, is the text
     * typed for the alternative used.
     */
    public record Choice(List<List<Part>> alternatives, boolean optional) implements Part {

        public Choice {
            alternatives = alternatives.stream().map(List::copyOf).toList();
        }
    }

    /** Whether nothing at all matches {@code parts}, as in an optional subcommand {@code subcommand [help]}. */
    public static boolean canBeEmpty(List<Part> parts) {
        for (Part part : parts) {
            boolean empty = switch (part) {
                case Optional o -> true;
                case Choice c -> c.optional() || c.alternatives().stream().anyMatch(CommandPattern::canBeEmpty);
                case Argument a -> false;
                case Word w -> false;
            };
            if (!empty) {
                return false;
            }
        }
        return true;
    }

    /** How the pattern reads in a usage message: {@code <player> [<text>]}. */
    public static String usage(List<Part> parts) {
        StringBuilder usage = new StringBuilder();
        for (Part part : parts) {
            if (!usage.isEmpty()) {
                usage.append(' ');
            }
            switch (part) {
                case Word w -> usage.append(w.text());
                case Argument a -> usage.append('<').append(a.name() != null ? a.name() : a.type())
                        .append(a.plural() ? "s" : "").append('>');
                case Optional o -> usage.append('[').append(usage(o.parts())).append(']');
                case Choice c -> usage.append(c.optional() ? '[' : '(')
                        .append(String.join("|", c.alternatives().stream().map(CommandPattern::usage).toList()))
                        .append(c.optional() ? ']' : ')');
            }
        }
        return usage.toString();
    }

    /** The arguments in {@code parts}, in order, as {@code arg-1}, {@code arg-2}... number them. */
    public static void arguments(List<Part> parts, List<Argument> into) {
        for (Part part : parts) {
            switch (part) {
                case Argument a -> into.add(a);
                case Optional o -> arguments(o.parts(), into);
                case Choice c -> c.alternatives().forEach(alternative -> arguments(alternative, into));
                case Word w -> {}
            }
        }
    }

    /** The choices in {@code parts}, in order, as {@code choice argument 1}... number them. */
    public static void choices(List<Part> parts, List<Choice> into) {
        for (Part part : parts) {
            switch (part) {
                case Choice c -> {
                    into.add(c);
                    c.alternatives().forEach(alternative -> choices(alternative, into));
                }
                case Optional o -> choices(o.parts(), into);
                case Argument a -> {}
                case Word w -> {}
            }
        }
    }
}
