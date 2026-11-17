/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * What a running trigger knows: the event that started it. Addons are given one; they don't implement it.
 *
 * <p>Each run of a trigger, and each call of a function, has a context of its own: the same object across the run's
 * waits and pauses, and a different one for every other run, even of another trigger for the same event, or another
 * call from nowhere (the console, {@code on load}). So what a run opened, like a transaction, is kept by its context,
 * compared by identity, not by its event. Experimental: this may become a method of its own.
 */
public interface TriggerContext {

    /**
     * The event that started the trigger.
     *
     * @return a Bukkit event, or for NeoSkript's own (a script load, a script command, an {@code every} period) an
     *     object of its own, or {@code null}
     */
    Object event();
}
