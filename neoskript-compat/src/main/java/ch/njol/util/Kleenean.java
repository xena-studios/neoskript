/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.util;

/** Skript's three-valued logic: false, unknown, true. NeoSkriptCompat's own, with Skript's API. */
public enum Kleenean {
    FALSE, UNKNOWN, TRUE;

    @Override
    public final String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public final Kleenean is(final Kleenean other) {
        return other == this ? TRUE : this == UNKNOWN || other == UNKNOWN ? UNKNOWN : FALSE;
    }

    public final Kleenean and(final Kleenean other) {
        return values()[Math.min(ordinal(), other.ordinal())];
    }

    public final Kleenean or(final Kleenean other) {
        return values()[Math.max(ordinal(), other.ordinal())];
    }

    public final Kleenean not() {
        return values()[2 - ordinal()];
    }

    public final Kleenean implies(final Kleenean other) {
        return not().or(other);
    }

    public final boolean isTrue() {
        return this == TRUE;
    }

    public final boolean isUnknown() {
        return this == UNKNOWN;
    }

    public final boolean isFalse() {
        return this == FALSE;
    }

    public static Kleenean get(final boolean b) {
        return b ? TRUE : FALSE;
    }

    public static Kleenean get(final int i) {
        return i > 0 ? TRUE : i < 0 ? FALSE : UNKNOWN;
    }

    public static Kleenean get(final double d) {
        return d > 0 ? TRUE : d < 0 ? FALSE : UNKNOWN;
    }
}
