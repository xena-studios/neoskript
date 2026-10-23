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

    private static final double EPSILON = 1e-10;

    private record Equality(Class<?> left, Class<?> right, java.util.function.BiPredicate<Object, Object> test) {}

    private static final java.util.List<Equality> EQUALITIES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers what {@code is} means between a value of {@code left} and one of {@code right}, either way
     * round: {@code test} gets the {@code left} value first.
     */
    @SuppressWarnings("unchecked")
    public static <A, B> void registerEquality(Class<A> left, Class<B> right, java.util.function.BiPredicate<? super A, ? super B> test) {
        EQUALITIES.add(new Equality(left, right, (java.util.function.BiPredicate<Object, Object>) test));
    }

    private static boolean isWhole(Number value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }

    public static boolean equal(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        for (Equality equality : EQUALITIES) {
            if (equality.left().isInstance(a) && equality.right().isInstance(b)) {
                return equality.test().test(a, b);
            }
            if (equality.left().isInstance(b) && equality.right().isInstance(a)) {
                return equality.test().test(b, a);
            }
        }
        if (a instanceof Number x && b instanceof Number y) {
            if (isWhole(x) && isWhole(y)) {
                return x.longValue() == y.longValue();
            }
            // A float (a quaternion's part) is compared as a float, as Skript reads the other side as one.
            if (x instanceof Float || y instanceof Float) {
                return x.floatValue() == y.floatValue();
            }
            // As in Skript, decimals within 1e-10 of each other are equal, and NaN equals nothing, not even NaN.
            double dx = x.doubleValue();
            double dy = y.doubleValue();
            return dx == dy || Math.abs(dx - dy) < EPSILON;
        }
        if (a instanceof String x && b instanceof String y) {
            return x.equalsIgnoreCase(y);
        }
        return a.equals(b);
    }

    /**
     * How {@code a} orders against {@code b}: negative, zero or positive, or {@code null} if they can't be
     * ordered (numbers, texts, and values of the same comparable type, like dates and timespans, can).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Integer order(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a instanceof String x && b instanceof String y) {
            return String.CASE_INSENSITIVE_ORDER.compare(x, y);
        }
        if (a instanceof Comparable x && b != null && a.getClass() == b.getClass()) {
            return x.compareTo(b);
        }
        return null;
    }
}
