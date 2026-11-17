/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * An effect that pauses the trigger until what it started is done, as {@code wait} does: a database query, a web
 * request. It starts the work and returns a {@link CompletionStage}; the trigger stops there, and carries on from
 * the next line once the stage completes, with its local variables, on the thread it ran on (see
 * {@link co.xenastudios.neoskript.api.registration.Registrar#pausingEffect}).
 *
 * <p>Do the slow part off the server's threads, with your own executor or
 * {@link co.xenastudios.neoskript.api.Threads#runAsync}, and complete the stage from there. Never block in
 * {@code start}: it runs on the trigger's thread.
 *
 * <p>A stage that completes exceptionally, or a {@code start} that throws, is a runtime error on the line, with the
 * exception's message, and the trigger carries on, as after Skript's other runtime errors. So is one that takes longer than the element's
 * timeout ({@link co.xenastudios.neoskript.api.registration.Registrar#timeout}); NeoSkript then tries to cancel it.
 *
 * <p>With a {@link #target()}, the stage's value is stored in that placeholder, as {@code set} would, once it
 * completes: {@code execute %text% in %database% and store the result in %-objects%}. A {@link java.util.Map} sets
 * a list variable's indices: each key is an index, and a value that's a map or a list makes a list under it
 * ({@code {_rows::name::1}}). A {@link java.util.Collection} or an array is several values, numbered from 1.
 */
@FunctionalInterface
public interface PausingEffect {

    /**
     * What a stage completes with when the work went wrong in a way the addon reports itself, as skript-db keeps
     * the last error for {@code the last sql error}: nothing is stored in the target, which keeps its value, and
     * there's no runtime error. The trigger carries on.
     */
    Object UNCHANGED = new Object() {
        @Override
        public String toString() {
            return "unchanged";
        }
    };

    /**
     * Starts the work.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @return what the trigger waits for, or {@code null} if there's nothing to wait for (it carries straight on)
     */
    CompletionStage<?> start(TriggerContext context, Object[] arguments);

    /**
     * The placeholder the stage's value is stored in, which has to be one that can be set, like a variable: the
     * {@code %-objects%} of {@code and store the result in %-objects%}. Where it's left out, or the stage fails,
     * nothing is stored.
     *
     * @return its index, or -1 to store nothing
     */
    default int target() {
        return -1;
    }

    /**
     * A pausing effect that stores its stage's value in placeholder {@code target}.
     *
     * @param target the index of the placeholder the value is stored in
     * @param start starts the work, given the trigger and every placeholder's value
     * @return the effect
     */
    static PausingEffect storing(int target,
            BiFunction<TriggerContext, Object[], ? extends CompletionStage<?>> start) {
        Objects.requireNonNull(start, "start");
        if (target < 0) {
            throw new IllegalArgumentException("A target is a placeholder's index, 0 or more: " + target);
        }
        return new PausingEffect() {
            @Override
            public CompletionStage<?> start(TriggerContext context, Object[] arguments) {
                return start.apply(context, arguments);
            }

            @Override
            public int target() {
                return target;
            }
        };
    }
}
