/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import ch.njol.skript.lang.SkriptParser.ParseResult;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.structure.Structure;

/**
 * An event, as {@code on wave}: set up with its literals ({@code init}), then asked whether each event that fires is
 * one for the trigger ({@code check}). NeoSkript listens to the event classes and runs the trigger.
 */
public abstract class SkriptEvent extends Structure {

    public static final Priority PRIORITY = new Priority(600);

    protected EventPriority eventPriority;
    protected ListeningBehavior listeningBehavior;
    protected boolean supportsListeningBehavior;

    public SkriptEvent() {}

    @Override
    public final boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult,
            EntryContainer entryContainer) {
        return init(args, matchedPattern, parseResult);
    }

    public abstract boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult);

    @Override
    public boolean load() {
        return true;
    }

    @Override
    public Priority getPriority() {
        return PRIORITY;
    }

    public abstract boolean check(Event event);

    public boolean shouldLoadEvent() {
        return true;
    }

    public Class<? extends Event>[] getEventClasses() {
        return null;
    }

    public EventPriority getEventPriority() {
        return eventPriority != null ? eventPriority : EventPriority.NORMAL;
    }

    public boolean isEventPrioritySupported() {
        return true;
    }

    public ListeningBehavior getListeningBehavior() {
        return listeningBehavior != null ? listeningBehavior : ListeningBehavior.UNCANCELLED;
    }

    public boolean isListeningBehaviorSupported() {
        return supportsListeningBehavior;
    }

    public boolean canExecuteAsynchronously() {
        return false;
    }

    public static String fixPattern(String pattern) {
        return pattern;
    }

    @Override
    public String getSyntaxTypeName() {
        return "event";
    }

    /** Whether a trigger runs for cancelled events, uncancelled ones, or both. */
    public enum ListeningBehavior {
        UNCANCELLED, CANCELLED, ANY;

        public boolean matches(boolean cancelled) {
            return switch (this) {
                case UNCANCELLED -> !cancelled;
                case CANCELLED -> cancelled;
                case ANY -> true;
            };
        }
    }
}
