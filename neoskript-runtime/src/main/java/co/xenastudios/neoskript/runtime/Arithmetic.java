/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * Arithmetic as scripts see it. Whole numbers stay whole ({@code 2 + 3} is {@code 5}, not {@code 5.0})
 * unless they overflow; division and powers give decimals. Adding two texts joins them. Anything else
 * gives {@code null} ("nothing"), as in Skript.
 */
public final class Arithmetic {

    private Arithmetic() {}

    public static Object add(Object a, Object b) {
        a = Values.single(a);
        b = Values.single(b);
        // A missing number is 0, as in Skript: {_x} + 1 is 1 when {_x} isn't set.
        if (a == null && b instanceof Number) {
            a = 0L;
        } else if (b == null && a instanceof Number) {
            b = 0L;
        }
        if (a instanceof String x && b instanceof String y) {
            return x + y;
        }
        if (isWhole(a) && isWhole(b)) {
            long x = ((Number) a).longValue();
            long y = ((Number) b).longValue();
            long sum = x + y;
            if (((x ^ sum) & (y ^ sum)) >= 0) {
                return sum;
            }
        }
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() + y.doubleValue()) : null;
    }

    public static Object subtract(Object a, Object b) {
        a = Values.single(a);
        b = Values.single(b);
        // A missing number is 0, as in Skript: {_x} + 1 is 1 when {_x} isn't set.
        if (a == null && b instanceof Number) {
            a = 0L;
        } else if (b == null && a instanceof Number) {
            b = 0L;
        }
        if (isWhole(a) && isWhole(b)) {
            long x = ((Number) a).longValue();
            long y = ((Number) b).longValue();
            long difference = x - y;
            if (((x ^ y) & (x ^ difference)) >= 0) {
                return difference;
            }
        }
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() - y.doubleValue()) : null;
    }

    public static Object multiply(Object a, Object b) {
        a = Values.single(a);
        b = Values.single(b);
        // A missing number is 0, as in Skript: {_x} + 1 is 1 when {_x} isn't set.
        if (a == null && b instanceof Number) {
            a = 0L;
        } else if (b == null && a instanceof Number) {
            b = 0L;
        }
        if (isWhole(a) && isWhole(b)) {
            try {
                return Math.multiplyExact(((Number) a).longValue(), ((Number) b).longValue());
            } catch (ArithmeticException overflow) {
                // Fall through to decimals.
            }
        }
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() * y.doubleValue()) : null;
    }

    public static Object divide(Object a, Object b) {
        a = Values.single(a);
        b = Values.single(b);
        // A missing number is 0, as in Skript: {_x} + 1 is 1 when {_x} isn't set.
        if (a == null && b instanceof Number) {
            a = 0L;
        } else if (b == null && a instanceof Number) {
            b = 0L;
        }
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() / y.doubleValue()) : null;
    }

    public static Object power(Object a, Object b) {
        a = Values.single(a);
        b = Values.single(b);
        // A missing number is 0, as in Skript: {_x} + 1 is 1 when {_x} isn't set.
        if (a == null && b instanceof Number) {
            a = 0L;
        } else if (b == null && a instanceof Number) {
            b = 0L;
        }
        return a instanceof Number x && b instanceof Number y ? (Object) Math.pow(x.doubleValue(), y.doubleValue()) : null;
    }

    private static boolean isWhole(Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }
}
