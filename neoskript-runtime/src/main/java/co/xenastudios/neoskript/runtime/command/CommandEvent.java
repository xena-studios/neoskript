/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.command;

import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.List;

/**
 * A script command being run: the event its triggers run for (and, as a {@link CommandSuggestionEvent}, its
 * {@code suggestions:} entry's). The generated code reads arguments
 * through the static helpers.
 */
public class CommandEvent {

    private final Object sender;
    private final String label;
    private final String fullCommand;
    private final Object[] arguments;
    private final String[] choices;
    private Timespan cooldown;
    private Timespan elapsed;
    private String cooldownBypass;
    private boolean cooldownCancelled;
    private Object result;
    private Object failure;

    /**
     * @param sender who ran it
     * @param label the name or alias it was run with, without the slash
     * @param fullCommand everything typed, without the slash: {@code home bed}
     * @param arguments its arguments' values by number, {@code null} where one was left out
     * @param choices the text of each choice used, by number
     */
    public CommandEvent(Object sender, String label, String fullCommand, Object[] arguments, String[] choices) {
        this.sender = sender;
        this.label = label;
        this.fullCommand = fullCommand;
        this.arguments = arguments;
        this.choices = choices;
    }

    public Object sender() {
        return sender;
    }

    public String label() {
        return label;
    }

    public String fullCommand() {
        return fullCommand;
    }

    /** The command's cooldown, how long ago the sender last used it, and who can skip it. */
    public void cooldown(Timespan cooldown, Timespan elapsed, String bypass) {
        this.cooldown = cooldown;
        this.elapsed = elapsed;
        this.cooldownBypass = bypass;
    }

    public Timespan cooldown() {
        return cooldown;
    }

    public Timespan elapsed() {
        return elapsed;
    }

    /** How long until the sender can use the command again, or {@code null} without a cooldown. */
    public Timespan remaining() {
        if (cooldown == null) {
            return null;
        }
        return new Timespan(Math.max(0, cooldown.millis() - (elapsed == null ? 0 : elapsed.millis())));
    }

    public String cooldownBypass() {
        return cooldownBypass;
    }

    /** Whether the trigger cancelled the cooldown it started, as {@code cancel the cooldown} does. */
    public boolean cooldownCancelled() {
        return cooldownCancelled;
    }

    public void cancelCooldown(boolean cancel) {
        cooldownCancelled = cancel;
    }

    /** Why the trigger failed the command, as {@code fail the command execution with ...}, or {@code null}. */
    public Object failure() {
        return failure;
    }

    /**
     * Fails the command with {@code message}, which its sender is shown, and stops the trigger by throwing
     * {@link Failed}, as Skript's EffFailExecution ends the trigger.
     */
    public void fail(Object message) {
        failure = message;
        throw new Failed();
    }

    /** Thrown to stop a command's trigger that failed the command: not an error. */
    public static final class Failed extends RuntimeException {
        Failed() {
            super("The command execution failed", null, false, false);
        }
    }

    /** The value a message or default value trigger worked out. */
    public Object result() {
        return result;
    }

    static CommandEvent of(TriggerContext context) {
        return context.event() instanceof CommandEvent event ? event : null;
    }

    /** Argument {@code index} (from 0), or {@code null}: {@code arg-1}. */
    public static Object argument(TriggerContext context, int index) {
        CommandEvent event = of(context);
        return event == null || index >= event.arguments.length ? null : event.arguments[index];
    }

    /** Every argument's values, in order: {@code the arguments}. */
    public static Object[] arguments(TriggerContext context) {
        CommandEvent event = of(context);
        List<Object> values = new ArrayList<>();
        if (event != null) {
            for (Object argument : event.arguments) {
                if (argument instanceof Object[] array) {
                    values.addAll(List.of(array));
                } else if (argument != null) {
                    values.add(argument);
                }
            }
        }
        return values.toArray();
    }

    /** Gives a left-out argument its default value. */
    public static void defaultArgument(TriggerContext context, int index, Object value) {
        CommandEvent event = of(context);
        if (event != null && event.arguments[index] == null) {
            event.arguments[index] = value;
        }
    }

    /** The text typed for choice {@code index} (from 0), or {@code null}. */
    public static Object choice(TriggerContext context, int index) {
        CommandEvent event = of(context);
        return event == null || index >= event.choices.length ? null : event.choices[index];
    }

    /** Stores what a message or default value trigger worked out. */
    public static void result(TriggerContext context, Object value) {
        CommandEvent event = of(context);
        if (event != null) {
            event.result = value;
        }
    }
}
