/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/** What a running trigger knows: the event that started it. Addons are given one; they don't implement it. */
public interface TriggerContext {

    /**
     * The event that started the trigger.
     *
     * @return a Bukkit event, or for NeoSkript's own (a script load, a script command, an {@code every} period) an
     *     object of its own, or {@code null}
     */
    Object event();
}
