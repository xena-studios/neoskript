/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * A section, like {@code spawn a pig at %location%:}. {@link #run} does what the section does and returns
 * the values its code then runs for, once each: inside the code, each one is the value of the section's
 * type, as {@code entity} is the spawned pig. Written without code (no {@code :}), it's an effect that
 * just runs.
 */
@FunctionalInterface
public interface Section {

    /**
     * Runs the section.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @return a value or an {@code Object[]} of values for its code to run for, or {@code null} for none
     */
    Object run(TriggerContext context, Object[] arguments);
}
