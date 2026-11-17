/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/**
 * @param owner what the event happened to, so a {@code wait} carries on on its thread on Folia: an entity,
 *     a location, or {@code null} for the global thread
 * @param caller for a function, the context of what called it; {@code null} for a trigger's own run
 */
public record SimpleTriggerContext(Object event, Object owner, TriggerContext caller) implements TriggerContext {

    public SimpleTriggerContext(Object event) {
        this(event, null, null);
    }

    public SimpleTriggerContext(Object event, Object owner) {
        this(event, owner, null);
    }

    /**
     * The context a function is called with when nothing called it (an addon, the console): {@link #call} doesn't
     * link a call's context to it, so it's never a run's, and nothing kept by run outlives the call.
     */
    public static final SimpleTriggerContext NOWHERE = new SimpleTriggerContext(null);

    /** The run that called this, through every function between, or this itself for a trigger's run. */
    @Override
    public TriggerContext outermostRun() {
        return caller == null ? this : caller.outermostRun();
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
     * The only context a call makes: the caller hands its own straight through. It's linked to the caller's, so
     * {@link #outermostRun} finds the run that called it.
     *
     * @param caller the context the function was called from, or {@code null}
     * @return a context of its own
     */
    public static TriggerContext call(TriggerContext caller) {
        return new SimpleTriggerContext(null, caller instanceof SimpleTriggerContext simple ? simple.owner() : null,
                caller == NOWHERE ? null : caller);
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
