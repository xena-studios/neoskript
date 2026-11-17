/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.element.Condition;

/**
 * A loaded script's trigger of one of an addon's events, as {@link Registrar#eventTriggers} gives them: for an event
 * the addon runs itself, rather than through Bukkit, like a placeholder asked for by another plugin. Addons are
 * given these; they don't implement them.
 */
public interface LoadedTrigger {

    /**
     * The script the trigger is in.
     *
     * @return its name, as {@code quests/main.sk}
     */
    String script();

    /**
     * The line the trigger starts on.
     *
     * @return the line's number, from 1
     */
    int line();

    /**
     * The filter the event's factory made for this trigger, which knows what was written in the event's line, as
     * {@code "myserver"} in {@code placeholderapi placeholder with the prefix "myserver"}.
     *
     * @return the filter, or {@code null} if the event has none
     */
    Condition filter();

    /**
     * Runs the trigger now, on this thread, for {@code event}: its filter first, then its code, until it ends or
     * waits. A runtime error in it is logged as any trigger's is, and doesn't reach the caller.
     *
     * @param event the event, which the trigger's event values and syntax read
     * @param owner the entity, block or location whose thread the trigger carries on on after a {@code wait} (the
     *     region's on Folia), or {@code null} for the global thread
     */
    void run(Object event, Object owner);
}
