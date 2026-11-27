/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * One use of syntax a script declared ({@link co.xenastudios.neoskript.api.registration.ScriptSyntax}), as its code
 * runs: what it was given where it was used. Its code finds it with {@link TriggerContext#scriptSyntaxCall()}.
 */
@Experimental
public interface ScriptSyntaxCall {

    /**
     * The values of the use's arguments, by placeholder, from 0: one value for a single placeholder, an
     * {@code Object[]} for a plural one, and {@code null} for one in an optional part that wasn't written.
     *
     * @return the arguments
     */
    Object[] arguments();

    /**
     * Which of the syntax's patterns the use matched, from 0.
     *
     * @return the pattern's index
     */
    int pattern();

    /**
     * The parse mark the use matched, as Skript's {@code parseResult.mark}.
     *
     * @return the mark
     */
    int mark();

    /**
     * The parse tags the use matched.
     *
     * @return the tags
     */
    java.util.Set<String> tags();

    /** Marks the use as having passed, for a condition: skript-reflect's {@code continue}. */
    void proceed();

    /**
     * Whether the code has {@linkplain #proceed() proceeded}.
     *
     * @return whether it has
     */
    boolean proceeded();
}
