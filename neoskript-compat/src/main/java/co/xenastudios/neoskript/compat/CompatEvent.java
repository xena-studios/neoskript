/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** The event an addon's element is given where a trigger runs with no Bukkit event: a function, a command. */
public final class CompatEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    static final CompatEvent NONE = new CompatEvent();

    private CompatEvent() {}

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    static Event of(Object event) {
        return event instanceof Event bukkit ? bukkit : NONE;
    }
}
