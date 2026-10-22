/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Trigger;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A compiled {@code command /name ...:} structure, for the platform to register.
 *
 * @param name its name, without the slash
 * @param aliases its other names, without slashes
 * @param permission the permission it needs, or {@code null}
 * @param permissionMessage what people without the permission are told: a trigger whose
 *     {@link co.xenastudios.neoskript.runtime.command.CommandEvent#result()} is the message, or {@code null}
 * @param cooldown how long a player has to wait between uses, or {@code null}
 * @param cooldownMessage what a player who has to wait is told, like {@code permissionMessage}, or {@code null}
 * @param routes what can be typed after the name, most specific first: each subcommand, then the command
 */
public record CompiledCommand(String script, int line, String name, List<String> aliases, String description,
        String usage, String permission, Trigger permissionMessage, Timespan cooldown, String cooldownBypass,
        Trigger cooldownMessage, String prefix, List<Route> routes) {

    public CompiledCommand {
        aliases = List.copyOf(aliases);
        routes = List.copyOf(routes);
    }

    /**
     * A way to run the command: its pattern (the command's, plus a subcommand's), who can run it, and the
     * trigger that runs.
     *
     * @param executors who can run it ({@code players}, {@code console}, {@code operators},
     *     {@code command blocks}, {@code entities}), or empty for anyone
     * @param permission the permission it needs, or {@code null}
     */
    public record Route(List<CommandPattern.Part> pattern, Set<String> executors, String permission, Trigger trigger,
            Matcher matcher) {

        public Route {
            pattern = List.copyOf(pattern);
            executors = Set.copyOf(executors);
        }

        /** The arguments and choices typed, or {@code null} if {@code input} doesn't fit the pattern. */
        public Matcher.Match match(String input) {
            return matcher.match(input);
        }
    }

    /** Matches what's typed after a command's name against a route's pattern. */
    public static final class Matcher {

        /** The values of a route's arguments and choices, by number. */
        public record Match(Object[] arguments, String[] choices) {}

        private static final java.util.regex.Pattern LIST_SEPARATOR =
                java.util.regex.Pattern.compile("\\s*,\\s*(?:and\\s+|or\\s+)?|\\s+and\\s+|\\s+or\\s+");

        private final List<CommandPattern.Part> pattern;
        private final Map<CommandPattern.Part, Integer> argumentNumbers = new IdentityHashMap<>();
        private final Map<CommandPattern.Part, Integer> choiceNumbers = new IdentityHashMap<>();
        private final Map<CommandPattern.Part, Function<String, ?>> parsers = new IdentityHashMap<>();
        private final List<CommandPattern.Argument> arguments;
        private final int choices;

        Matcher(List<CommandPattern.Part> pattern, List<CommandPattern.Argument> arguments,
                List<CommandPattern.Choice> choices, Function<String, Function<String, ?>> parsers) {
            this.pattern = List.copyOf(pattern);
            this.arguments = List.copyOf(arguments);
            this.choices = choices.size();
            for (int i = 0; i < arguments.size(); i++) {
                argumentNumbers.put(arguments.get(i), i);
                this.parsers.put(arguments.get(i), parsers.apply(arguments.get(i).type()));
            }
            for (int i = 0; i < choices.size(); i++) {
                choiceNumbers.put(choices.get(i), i);
            }
        }

        /** The parts still to match, as a linked list so backtracking can share tails. */
        private record Rest(Object part, Rest next) {}

        /** Marks where a choice's alternative ends, to record the text it matched. */
        private record ChoiceEnd(CommandPattern.Choice choice, int start) {}

        private static Rest prepend(List<?> parts, Rest rest) {
            for (int i = parts.size() - 1; i >= 0; i--) {
                rest = new Rest(parts.get(i), rest);
            }
            return rest;
        }

        Match match(String input) {
            String stripped = input.strip();
            String[] words = stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
            Object[] values = new Object[arguments.size()];
            String[] chosen = new String[choices];
            if (!match(prepend(pattern, null), 0, words, values, chosen)) {
                return null;
            }
            for (int i = 0; i < values.length; i++) {
                CommandPattern.Argument argument = arguments.get(i);
                if (values[i] == null && argument.defaultValue() == null && argument.defaultText() != null) {
                    values[i] = parse(argument, argument.defaultText());
                }
            }
            return new Match(values, chosen);
        }

        /**
         * Whether {@code rest} matches {@code words} from {@code at} to the end. Optional parts are tried
         * with, then without; arguments take as few words as they can.
         */
        private boolean match(Rest rest, int at, String[] words, Object[] values, String[] chosen) {
            if (rest == null) {
                return at == words.length;
            }
            switch (rest.part()) {
                case CommandPattern.Word word -> {
                    return at < words.length && words[at].equalsIgnoreCase(word.text())
                            && match(rest.next(), at + 1, words, values, chosen);
                }
                case CommandPattern.Optional optional -> {
                    return match(prepend(optional.parts(), rest.next()), at, words, values, chosen)
                            || match(rest.next(), at, words, values, chosen);
                }
                case CommandPattern.Choice choice -> {
                    for (List<CommandPattern.Part> alternative : choice.alternatives()) {
                        Rest then = prepend(alternative, new Rest(new ChoiceEnd(choice, at), rest.next()));
                        if (match(then, at, words, values, chosen)) {
                            return true;
                        }
                    }
                    if (choice.optional()) {
                        chosen[choiceNumbers.get(choice)] = null;
                        return match(rest.next(), at, words, values, chosen);
                    }
                    return false;
                }
                case ChoiceEnd end -> {
                    int number = choiceNumbers.get(end.choice());
                    String previous = chosen[number];
                    chosen[number] = String.join(" ", java.util.Arrays.copyOfRange(words, end.start(), at));
                    if (match(rest.next(), at, words, values, chosen)) {
                        return true;
                    }
                    chosen[number] = previous;
                    return false;
                }
                case CommandPattern.Argument argument -> {
                    int number = argumentNumbers.get(argument);
                    for (int end = at + 1; end <= words.length; end++) {
                        Object value = parse(argument, String.join(" ", java.util.Arrays.copyOfRange(words, at, end)));
                        if (value == null) {
                            continue;
                        }
                        values[number] = value;
                        if (match(rest.next(), end, words, values, chosen)) {
                            return true;
                        }
                        values[number] = null;
                    }
                    return false;
                }
                default -> throw new IllegalStateException("Unknown command pattern part " + rest.part());
            }
        }

        /** The argument's value for {@code text}, or {@code null} if it isn't one. */
        private Object parse(CommandPattern.Argument argument, String text) {
            if (!argument.plural()) {
                return parseOne(argument, text);
            }
            String[] items = LIST_SEPARATOR.split(text);
            Object[] values = new Object[items.length];
            for (int i = 0; i < items.length; i++) {
                values[i] = parseOne(argument, items[i]);
                if (values[i] == null) {
                    return null;
                }
            }
            return values;
        }

        private Object parseOne(CommandPattern.Argument argument, String text) {
            if (argument.type().equals("string")) {
                // Quotes around text are left out: "a b" is a b.
                return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")
                        ? text.substring(1, text.length() - 1) : text;
            }
            Function<String, ?> parser = parsers.get(argument);
            Object value = parser == null ? null : parser.apply(text);
            if (value instanceof Number number && (argument.min() != null || argument.max() != null)) {
                double d = number.doubleValue();
                if ((argument.min() != null && d < argument.min()) || (argument.max() != null && d > argument.max())) {
                    return null;
                }
            }
            return value;
        }
    }
}
