/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * A section whose code doesn't run where it's written but later, for an event the addon fires itself, as
 * {@code make gui slot 0 with dirt:} keeps its code for when the slot is clicked. Written without code (no
 * {@code :}), it's an effect, and gets no code.
 */
@FunctionalInterface
public interface LaterSection {

    /**
     * Runs the section's line.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @param code the code under it, to run later, or {@code null} if it's written without code
     */
    void run(TriggerContext context, Object[] arguments, SectionCode code);
}
