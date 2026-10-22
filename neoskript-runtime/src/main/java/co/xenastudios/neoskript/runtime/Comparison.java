/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * Comparing values as scripts see them: numbers by value whatever their type ({@code 2 = 2.0}), text
 * ignoring case, and anything else with {@code equals}.
 */
public final class Comparison {

    private Comparison() {}

    public static boolean equal(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0 || x.equals(y);
        }
        if (a instanceof String x && b instanceof String y) {
            return x.equalsIgnoreCase(y);
        }
        return a.equals(b);
    }

    /**
     * How {@code a} orders against {@code b}: negative, zero or positive, or {@code null} if they can't be
     * ordered (only numbers and texts can).
     */
    public static Integer order(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a instanceof String x && b instanceof String y) {
            return String.CASE_INSENSITIVE_ORDER.compare(x, y);
        }
        return null;
    }
}
