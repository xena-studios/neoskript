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

    /**
     * The run of a trigger this context is part of, outermost: this context itself for a trigger's run, and for a
     * function's call the run that called it, however many functions deep. Compare it by identity: what an addon keeps
     * for one run (skript-reflect's last Java error) is kept by it, so the functions a run calls share it, and no
     * other run sees it.
     *
     * @return the outermost run's context
     */
    @co.xenastudios.neoskript.api.annotation.Experimental
    default TriggerContext outermostRun() {
        return this;
    }

    /**
     * The use of script-declared syntax whose code this is, if it is: its arguments, pattern and mark. {@code null}
     * elsewhere, including in a function that code calls.
     *
     * @return the use, or {@code null}
     */
    @co.xenastudios.neoskript.api.annotation.Experimental
    default ScriptSyntaxCall scriptSyntaxCall() {
        return null;
    }
}
