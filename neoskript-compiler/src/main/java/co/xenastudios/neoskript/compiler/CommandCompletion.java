/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent;
import co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.FilteringMode;
import co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.Suggestion;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tab completion for a script command: what can be typed at the word being typed, for each route that fits what
 * was typed before it. An argument whose suggestions a {@code suggestions:} entry works out gets what its code sets
 * them to, filtered as it says (by default, those starting with what's typed), as Skript's ScriptSuggestionProvider;
 * other arguments get their type's suggestions.
 *
 * <p>Platforms complete the word being typed, so for the last text argument, which takes the rest of what's
 * typed, a suggestion that starts with its words before the one being typed only gives the rest.
 */
public final class CommandCompletion {

    /**
     * What can be typed at one word of a pattern.
     *
     * @param words the words, if it isn't an argument
     * @param argument the argument, or {@code null}
     * @param required whether it's a word that has to be typed there (not in an optional part)
     */
    private record Slot(List<String> words, CommandPattern.Argument argument, boolean required) {}

    private CommandCompletion() {}

    /**
     * The suggestions for the last of {@code args}.
     *
     * @param label what the command was typed as, without the slash
     * @param args the words typed after it; the last is the one being typed, maybe empty
     * @param owner the trigger context's owner for {@code suggestions:} code: the player typing, or {@code null}
     * @param allowed whether the sender can run a route
     * @param defaults a type's suggestions: the online players' names for players
     */
    public static List<Suggestion> complete(CompiledCommand command, Object sender, Object owner, String label,
            String[] args, Predicate<CompiledCommand.Route> allowed, Function<String, List<String>> defaults) {
        int position = args.length - 1;
        if (position < 0) {
            return List.of();
        }
        String typed = args[position];
        Map<String, Suggestion> suggestions = new LinkedHashMap<>();
        Map<CommandPattern.Argument, List<Suggestion>> done = new IdentityHashMap<>();
        for (CompiledCommand.Route route : command.routes()) {
            if (!allowed.test(route)) {
                continue;
            }
            List<Slot> slots = new ArrayList<>();
            slots(route.pattern(), true, slots);
            int at = position;
            if (at >= slots.size()) {
                // The last text argument takes the rest of what's typed.
                if (slots.isEmpty() || !greedy(slots.getLast())) {
                    continue;
                }
                at = slots.size() - 1;
            }
            if (!fits(slots, args, at)) {
                continue;
            }
            Slot slot = slots.get(at);
            if (slot.argument() == null) {
                for (String word : slot.words()) {
                    if (word.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
                        suggestions.putIfAbsent(word, new Suggestion(word, null));
                    }
                }
                continue;
            }
            int slotAt = at;
            List<Suggestion> list = done.computeIfAbsent(slot.argument(),
                    argument -> argument(command, route, slots, slotAt, sender, owner, label, args, defaults));
            for (Suggestion suggestion : list) {
                suggestions.putIfAbsent(suggestion.text(), suggestion);
            }
        }
        return List.copyOf(suggestions.values());
    }

    /** Whether the words typed before {@code at} fit the words the route needs there. */
    private static boolean fits(List<Slot> slots, String[] args, int at) {
        for (int i = 0; i < at; i++) {
            Slot slot = slots.get(i);
            String word = args[i];
            if (slot.argument() == null && slot.required() && slot.words().stream().noneMatch(word::equalsIgnoreCase)) {
                return false;
            }
        }
        return true;
    }

    private static boolean greedy(Slot slot) {
        return slot.argument() != null && slot.argument().type().equals("string") && !slot.argument().plural();
    }

    /** The suggestions for the argument at slot {@code at}, filtered against what's typed of it. */
    private static List<Suggestion> argument(CompiledCommand command, CompiledCommand.Route route, List<Slot> slots,
            int at, Object sender, Object owner, String label, String[] args, Function<String, List<String>> defaults) {
        CommandPattern.Argument argument = slots.get(at).argument();
        String input = String.join(" ", java.util.Arrays.copyOfRange(args, at, args.length));
        List<Suggestion> list = null;
        FilteringMode mode = FilteringMode.STARTS_WITH;
        CompiledCommand.Suggester suggester = command.suggesters().get(argument);
        if (suggester != null) {
            Object[] values = new Object[suggester.arguments().size()];
            for (int i = 0; i < at; i++) {
                int index = indexOf(suggester.arguments(), slots.get(i).argument());
                if (index >= 0) {
                    values[index] = route.matcher().value(slots.get(i).argument(), args[i]);
                }
            }
            // Where the argument starts, counting the slash, as Skript's.
            int start = 1 + label.length() + 1;
            for (int i = 0; i < at; i++) {
                start += args[i].length() + 1;
            }
            CommandSuggestionEvent event = new CommandSuggestionEvent(sender, label, values,
                    new String[suggester.choices()], indexOf(suggester.arguments(), argument),
                    label + " " + String.join(" ", args), input, start);
            suggester.trigger().execute(new SimpleTriggerContext(event, owner));
            list = event.suggestions(event.current());
            mode = event.filteringMode();
        }
        if (list == null) {
            list = defaults.apply(argument.type()).stream().map(text -> new Suggestion(text, null)).toList();
        }
        // Only the word being typed is replaced.
        int space = input.lastIndexOf(' ');
        String before = space < 0 ? "" : input.substring(0, space + 1);
        List<Suggestion> filtered = new ArrayList<>();
        for (Suggestion suggestion : list) {
            if (suggestion == null || !matches(suggestion.text(), input, mode)) {
                continue;
            }
            String text = suggestion.text();
            if (!before.isEmpty() && text.regionMatches(true, 0, before, 0, before.length())) {
                text = text.substring(before.length());
            }
            filtered.add(text.equals(suggestion.text()) ? suggestion : new Suggestion(text, suggestion.tooltip()));
        }
        return filtered;
    }

    /**
     * Whether {@code suggestion} is shown for {@code input}, as Skript's: ignoring case and a starting quote, and
     * with spaces as underscores, so {@code foo b} matches {@code foo_bar}.
     */
    static boolean matches(String suggestion, String input, FilteringMode mode) {
        if (mode == FilteringMode.NONE) {
            return true;
        }
        String remaining = input.toLowerCase(Locale.ENGLISH);
        if (!remaining.isEmpty() && remaining.charAt(0) == '"') {
            remaining = remaining.substring(1);
        }
        remaining = remaining.replace(' ', '_');
        String lower = suggestion.toLowerCase(Locale.ENGLISH);
        if (!lower.isEmpty() && lower.charAt(0) == '"') {
            lower = lower.substring(1);
        }
        lower = lower.replace(' ', '_');
        return mode == FilteringMode.STARTS_WITH ? lower.startsWith(remaining) : lower.contains(remaining);
    }

    private static int indexOf(List<CommandPattern.Argument> arguments, CommandPattern.Argument argument) {
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i) == argument) {
                return i;
            }
        }
        return -1;
    }

    /**
     * What can be typed at each word of a pattern, roughly: optional parts and choices are laid out as if
     * they were all typed, which is right for the usual patterns.
     */
    private static void slots(List<CommandPattern.Part> parts, boolean required, List<Slot> slots) {
        for (CommandPattern.Part part : parts) {
            switch (part) {
                case CommandPattern.Word word -> slots.add(new Slot(List.of(word.text()), null, required));
                case CommandPattern.Argument argument -> slots.add(new Slot(List.of(), argument, required));
                case CommandPattern.Optional optional -> slots(optional.parts(), false, slots);
                case CommandPattern.Choice choice -> {
                    List<String> first = new ArrayList<>();
                    CommandPattern.Argument firstArgument = null;
                    for (List<CommandPattern.Part> alternative : choice.alternatives()) {
                        List<Slot> inner = new ArrayList<>();
                        slots(alternative, false, inner);
                        if (!inner.isEmpty()) {
                            first.addAll(inner.getFirst().words());
                            if (firstArgument == null) {
                                firstArgument = inner.getFirst().argument();
                            }
                        }
                    }
                    slots.add(first.isEmpty() && firstArgument != null
                            ? new Slot(List.of(), firstArgument, false)
                            : new Slot(first, null, required && !choice.optional()));
                }
            }
        }
    }
}
