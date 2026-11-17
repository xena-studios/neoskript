/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import java.util.concurrent.CompletionStage;

/**
 * A section that pauses the trigger before its code and again after it, as a database transaction does: skript-db's
 * {@code transaction on {db}:} opens the transaction, runs the code under it, then commits (see
 * {@link co.xenastudios.neoskript.api.registration.Registrar#pausingSection}).
 *
 * <p>Each pause is as a {@link PausingEffect}'s: the work runs off the server's threads, the trigger carries on
 * on its own thread once the stage completes, a failure or a timeout is a runtime error on the section's line,
 * and a stage that completes with {@link PausingEffect#UNCHANGED} is neither. The code under the section runs
 * once, after {@link #start}, unless that stage fails, times out, or completes with {@link Boolean#FALSE}.
 * {@link #end} runs whenever the trigger reaches the end of the section, whether or not its code ran; code that
 * leaves the section another way ({@code stop}, {@code return}, {@code exit}) skips it, so an addon should clean
 * up what such a section left open itself.
 *
 * <p>The element is shared by every run of the trigger, and by every trigger the section is in: keep what a run
 * opened by its {@link TriggerContext#event()}, not in the element.
 */
public interface PausingSection {

    /**
     * What a {@link PausingEffect}'s stage completes with to leave the pausing section it's in, as a failed
     * statement leaves skript-db's transaction: the rest of the section's code is skipped and its {@link #end}
     * runs. Nothing is stored and there's no runtime error. Outside a pausing section, it's
     * {@link PausingEffect#UNCHANGED}. Only the innermost pausing section is left, and not from inside a
     * {@code run on} section in it.
     */
    Object LEAVE = new Object() {
        @Override
        public String toString() {
            return "leave";
        }
    };

    /**
     * Starts the work before the section's code, on the trigger's thread: it mustn't block.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @return what the trigger waits for before the code, whose value {@link Boolean#FALSE} skips the code, or
     *     {@code null} to run the code straight away
     */
    CompletionStage<?> start(TriggerContext context, Object[] arguments);

    /**
     * Starts the work after the section's code, on the trigger's thread: it mustn't block.
     *
     * @param context the running trigger
     * @return what the trigger waits for before the code after the section, or {@code null} to carry straight on
     */
    default CompletionStage<?> end(TriggerContext context) {
        return null;
    }
}
