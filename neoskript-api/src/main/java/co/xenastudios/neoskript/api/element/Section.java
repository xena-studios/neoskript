/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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

    /** Runs the section; returns a value or an {@code Object[]} of values for its code, or {@code null}. */
    Object run(TriggerContext context, Object[] arguments);
}
