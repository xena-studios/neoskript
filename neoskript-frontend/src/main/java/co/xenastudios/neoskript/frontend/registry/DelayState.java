/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

/**
 * Whether a trigger may have waited by some point in its code. Skript tracks this while parsing, because
 * some things can't be done after a delay (changing an event's outcome, for one).
 */
public enum DelayState {
    NOT_DELAYED,
    MAYBE_DELAYED,
    DELAYED;

    /**
     * The state after a set of paths that start in {@code this} state and end in {@code ends}: delayed if
     * already delayed or every path delays, not delayed if no path does, and maybe otherwise.
     */
    public DelayState merge(Iterable<DelayState> ends) {
        if (this == DELAYED) {
            return DELAYED;
        }
        boolean all = true;
        boolean none = true;
        for (DelayState end : ends) {
            all &= end == DELAYED;
            none &= end == NOT_DELAYED;
        }
        return all ? DELAYED : none ? NOT_DELAYED : MAYBE_DELAYED;
    }
}
