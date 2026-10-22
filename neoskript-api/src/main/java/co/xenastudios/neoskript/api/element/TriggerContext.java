/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/** What a running trigger knows: the event that started it. */
public interface TriggerContext {

    /** The event that started the trigger, e.g. a Bukkit event, or {@code null} for a script load. */
    Object event();
}
