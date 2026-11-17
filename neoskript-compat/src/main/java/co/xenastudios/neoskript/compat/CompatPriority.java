/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.Collection;
import java.util.Set;
import org.skriptlang.skript.util.Priority;

/** Skript's priorities: base, or just before or after another. */
public record CompatPriority(Collection<Priority> before, Collection<Priority> after) implements Priority {

    private static final Priority BASE = new CompatPriority(Set.of(), Set.of());

    public static Priority base() {
        return BASE;
    }

    public static Priority before(Priority priority) {
        return new CompatPriority(Set.of(priority), Set.of());
    }

    public static Priority after(Priority priority) {
        return new CompatPriority(Set.of(), Set.of(priority));
    }

    @Override
    public Collection<Priority> after() {
        return after;
    }

    @Override
    public Collection<Priority> before() {
        return before;
    }

    /** Lower is tried first: one before another comes before it, one after it after. */
    public int rank() {
        if (this == BASE) {
            return 0;
        }
        for (Priority other : before) {
            return (other instanceof CompatPriority p ? p.rank() : 0) - 1;
        }
        for (Priority other : after) {
            return (other instanceof CompatPriority p ? p.rank() : 0) + 1;
        }
        return 0;
    }

    @Override
    public int compareTo(Priority other) {
        return Integer.compare(rank(), other instanceof CompatPriority p ? p.rank() : 0);
    }
}
