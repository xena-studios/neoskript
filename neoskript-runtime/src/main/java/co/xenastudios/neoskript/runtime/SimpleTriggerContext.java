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
     * A context for one run of a trigger or one call of a function, made as it starts: a new object with the same
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
