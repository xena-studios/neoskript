/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import java.util.concurrent.CompletionStage;

/**
 * An expression whose value takes a while to arrive: {@code the response of a GET request to %text%}. Where a
 * line uses it, the trigger pauses before the line, as {@code wait} does, until the stage completes, then runs the
 * line with its value (see {@link co.xenastudios.neoskript.api.registration.Registrar#pausingExpression}).
 *
 * <p>It can be used in an effect, a condition on a line of its own, or a function call on a line of its own, not
 * in a section's header ({@code if}, {@code loop}, {@code while}): set a variable to it first. Each pausing
 * expression on a line is worked out before the rest of the line, in the order they're written, even where the
 * line wouldn't otherwise get to it ({@code a or b}).
 *
 * <p>The stage's value is one value, an {@code Object[]} of several, or {@code null} for none; a
 * {@link java.util.Collection} is several too. A stage that completes exceptionally, or takes longer than the
 * element's timeout, is a runtime error on the line, and the expression has no value.
 */
@FunctionalInterface
public interface PausingExpression {

    /**
     * Starts working out the value.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @return what the trigger waits for, whose value is the expression's, or {@code null} for no value now
     */
    CompletionStage<?> start(TriggerContext context, Object[] arguments);
}
