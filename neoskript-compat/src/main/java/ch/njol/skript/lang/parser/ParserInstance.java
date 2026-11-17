/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.parser;

import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

/**
 * What's being parsed: the events and sections around the line. NeoSkriptCompat fills it in while an addon's element
 * is set up ({@code init}), from how NeoSkript matched the line.
 */
public final class ParserInstance {

    private static final ThreadLocal<ParserInstance> CURRENT = ThreadLocal.withInitial(ParserInstance::new);

    private Class<? extends Event>[] currentEvents;
    private String currentEventName;
    private Kleenean hasDelayBefore = Kleenean.FALSE;
    private ch.njol.skript.config.Node node;
    private String indentation = "";

    private final ParsingStack parsingStack = new ParsingStack();

    public ParsingStack getParsingStack() {
        return parsingStack;
    }

    public static ParserInstance get() {
        return CURRENT.get();
    }

    public boolean isActive() {
        return currentEvents != null;
    }

    public void reset() {
        currentEvents = null;
        currentEventName = null;
        hasDelayBefore = Kleenean.FALSE;
        node = null;
    }

    public void setCurrentEventName(String currentEventName) {
        this.currentEventName = currentEventName;
    }

    public String getCurrentEventName() {
        return currentEventName;
    }

    public void setCurrentEvents(Class<? extends Event>[] currentEvents) {
        this.currentEvents = currentEvents;
    }

    @SafeVarargs
    public final void setCurrentEvent(String name, Class<? extends Event>... events) {
        this.currentEventName = name;
        this.currentEvents = events;
    }

    public void deleteCurrentEvent() {
        currentEventName = null;
        currentEvents = null;
    }

    public Class<? extends Event>[] getCurrentEvents() {
        return currentEvents;
    }

    public boolean isCurrentEvent(Class<? extends Event> event) {
        if (currentEvents == null) {
            return false;
        }
        for (Class<? extends Event> current : currentEvents) {
            if (event.isAssignableFrom(current)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    public final boolean isCurrentEvent(Class<? extends Event>... events) {
        for (Class<? extends Event> event : events) {
            if (isCurrentEvent(event)) {
                return true;
            }
        }
        return false;
    }

    public void setHasDelayBefore(Kleenean hasDelayBefore) {
        this.hasDelayBefore = hasDelayBefore;
    }

    public Kleenean getHasDelayBefore() {
        return hasDelayBefore;
    }

    public void setNode(ch.njol.skript.config.Node node) {
        this.node = node;
    }

    public ch.njol.skript.config.Node getNode() {
        return node;
    }

    public void setIndentation(String indentation) {
        this.indentation = indentation;
    }

    public String getIndentation() {
        return indentation;
    }
}
