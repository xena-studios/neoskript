/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.Collection;
import java.util.Set;
import co.xenastudios.neoskript.api.registration.SyntaxPriority;
import org.skriptlang.skript.registration.SyntaxInfo;
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

    /**
     * Skript's level for a priority, as NeoSkript orders addon syntax ({@code Registrar.priority}): simple, event
     * values (just before combined), combined, properties (just before matching almost anything), and matching almost
     * anything. One just before or after another level is that level's neighbour.
     */
    public static SyntaxPriority level(Priority priority) {
        if (priority == null || priority.equals(SyntaxInfo.SIMPLE)) {
            return SyntaxPriority.SIMPLE;
        }
        if (priority.equals(SyntaxInfo.COMBINED)) {
            return SyntaxPriority.COMBINED;
        }
        if (priority.equals(SyntaxInfo.PATTERN_MATCHES_EVERYTHING)) {
            return SyntaxPriority.PATTERN_MATCHES_EVERYTHING;
        }
        SyntaxPriority[] levels = SyntaxPriority.values();
        for (Priority other : priority.before()) {
            return levels[Math.max(0, level(other).ordinal() - 1)];
        }
        for (Priority other : priority.after()) {
            return levels[Math.min(levels.length - 1, level(other).ordinal() + 1)];
        }
        return SyntaxPriority.SIMPLE;
    }

    @Override
    public int compareTo(Priority other) {
        int byLevel = Integer.compare(level(this).ordinal(), level(other).ordinal());
        return byLevel != 0 || equals(other) ? byLevel : Integer.compare(hashCode(), other.hashCode());
    }
}
