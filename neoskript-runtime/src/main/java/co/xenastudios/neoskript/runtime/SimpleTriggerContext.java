/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/**
 * @param owner what the event happened to, so a {@code wait} carries on on its thread on Folia: an entity,
 *     a location, or {@code null} for the global thread
 */
public record SimpleTriggerContext(Object event, Object owner) implements TriggerContext {

    public SimpleTriggerContext(Object event) {
        this(event, null);
    }

    /**
     * A context for one run of a trigger, made as it starts: a new object with the same
     * event and owner, so what keeps state by run (pausing sections, an addon's transactions) can tell runs apart
     * by identity, though an event's triggers, and calls from nowhere, share the context they're given. A wait or
     * a pause carries on with the same one.
     *
     * @param given the context the trigger or function was called with
     * @return a context of its own
     */
    public static TriggerContext run(TriggerContext given) {
        if (given instanceof SimpleTriggerContext simple) {
            return new SimpleTriggerContext(simple.event(), simple.owner());
        }
        return new SimpleTriggerContext(given == null ? null : given.event());
    }

    /**
     * A context for one call of a function, made as it starts: no event, as Skript's functions have none of the
     * caller's, but the caller's owner, so a wait in it carries on where the caller runs (its region, on Folia).
     * The only context a call makes: the caller hands its own straight through.
     *
     * @param caller the context the function was called from, or {@code null}
     * @return a context of its own
     */
    public static TriggerContext call(TriggerContext caller) {
        return new SimpleTriggerContext(null, caller instanceof SimpleTriggerContext simple ? simple.owner() : null);
    }

    // A run's context is itself, not its contents: two runs of the same event are two runs.
    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
