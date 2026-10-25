/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.command;

import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A script command being typed: what its {@code suggestions:} entry runs for, as Skript's
 * CommandSuggestionEvent. Only the arguments before the one being suggested for have values; the code
 * changes {@code the suggestions for arg-2} and how they're filtered, by argument number.
 */
public final class CommandSuggestionEvent extends CommandEvent {

    /**
     * A suggestion.
     *
     * @param text what's suggested
     * @param tooltip what's shown when hovering over it (a text component on Paper), or {@code null}
     */
    public record Suggestion(String text, Object tooltip) {}

    /** How suggestions are filtered against what's typed, as Skript's FilteringMode. */
    public enum FilteringMode {
        /** All of them are shown, as they are. */
        NONE,
        /** Only those that start with what's typed. */
        STARTS_WITH,
        /** Only those that contain what's typed. */
        CONTAINS
    }

    /**
     * Turns a value given to {@code set the suggestions for ...} into a suggestion. The platform replaces it to
     * turn text components into their plain text with their hover text as the tooltip, as Skript does.
     */
    public static volatile Function<Object, Suggestion> toSuggestion =
            value -> new Suggestion(value instanceof String text ? text : Values.text(value), null);
    /** Turns a suggestion back into a value for {@code the suggestions for ...}: a text component on Paper. */
    public static volatile Function<Suggestion, Object> fromSuggestion = Suggestion::text;

    private final int current;
    private final String fullInput;
    private final String input;
    private final int inputStart;
    private final Map<Integer, List<Suggestion>> suggestions = new HashMap<>();
    private FilteringMode filteringMode = FilteringMode.STARTS_WITH;

    /**
     * @param arguments the values of the arguments before the one being suggested for, by number, {@code null}
     *     for the rest
     * @param current the number of the argument being suggested for
     * @param fullInput everything typed so far, without the slash
     * @param input what's typed of the argument being suggested for
     * @param inputStart where {@code input} starts, counting the slash, from 0
     */
    public CommandSuggestionEvent(Object sender, String label, Object[] arguments, String[] choices, int current,
            String fullInput, String input, int inputStart) {
        super(sender, label, fullInput, arguments, choices);
        this.current = current;
        this.fullInput = fullInput;
        this.input = input;
        this.inputStart = inputStart;
    }

    /** The number of the argument being suggested for. */
    public int current() {
        return current;
    }

    public String fullInput() {
        return fullInput;
    }

    public String input() {
        return input;
    }

    public int inputStart() {
        return inputStart;
    }

    /** The suggestions set for argument {@code argument}, or {@code null} if none were. */
    public List<Suggestion> suggestions(int argument) {
        return suggestions.get(argument);
    }

    public FilteringMode filteringMode() {
        return filteringMode;
    }

    private static CommandSuggestionEvent suggestionEvent(TriggerContext context) {
        return context.event() instanceof CommandSuggestionEvent event ? event : null;
    }

    /** {@code the suggestions for arg-2}. */
    public static Object[] suggestions(TriggerContext context, int argument) {
        CommandSuggestionEvent event = suggestionEvent(context);
        List<Suggestion> list = event == null ? null : event.suggestions.get(argument);
        if (list == null) {
            return new Object[0];
        }
        return list.stream().map(fromSuggestion).toArray();
    }

    /** {@code set/add/remove/delete/reset the suggestions for arg-2}: {@code mode} as EffChange's patterns. */
    public static void changeSuggestions(TriggerContext context, int argument, String mode, Object[] delta) {
        CommandSuggestionEvent event = suggestionEvent(context);
        if (event == null) {
            return;
        }
        if (mode.equals("reset")) {
            event.suggestions.remove(argument);
            return;
        }
        List<Suggestion> current = event.suggestions.computeIfAbsent(argument, ignored -> new ArrayList<>());
        switch (mode) {
            case "set" -> {
                current.clear();
                add(current, delta);
            }
            case "add" -> add(current, delta);
            case "remove" -> {
                for (Object value : delta == null ? new Object[0] : delta) {
                    current.remove(toSuggestion.apply(value));
                }
            }
            default -> current.clear();
        }
    }

    private static void add(List<Suggestion> list, Object[] delta) {
        for (Object value : delta == null ? new Object[0] : delta) {
            list.add(toSuggestion.apply(value));
        }
    }

    /** {@code disable filtering for arg-2's suggestions}: only while suggesting for that argument. */
    public static void filtering(TriggerContext context, int argument, int mode) {
        CommandSuggestionEvent event = suggestionEvent(context);
        if (event != null && event.current == argument) {
            event.filteringMode = FilteringMode.values()[mode];
        }
    }

    /** {@code the full input}, {@code the current input} or {@code the current input position}. */
    public static Object input(TriggerContext context, int which) {
        CommandSuggestionEvent event = suggestionEvent(context);
        if (event == null) {
            return null;
        }
        return switch (which) {
            case 0 -> event.fullInput;
            case 1 -> event.input;
            default -> (long) event.inputStart + 1;
        };
    }
}
