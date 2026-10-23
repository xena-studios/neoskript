/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * Arithmetic as scripts see it. Whole numbers stay whole ({@code 2 + 3} is {@code 5}, not {@code 5.0})
 * unless they overflow; division and powers give decimals. Adding two texts joins them. Other types add
 * their own operations ({@link #register}), like timespans and vectors. Anything else gives {@code null}
 * ("nothing"), as in Skript.
 */
public final class Arithmetic {

    private Arithmetic() {}

    private record Operation(char operator, Class<?> left, Class<?> right,
            java.util.function.BiFunction<Object, Object, Object> apply) {}

    private static final java.util.List<Operation> OPERATIONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Map<Class<?>, java.util.function.Supplier<?>> DEFAULTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static {
        register('+', Timespan.class, Timespan.class, (x, y) -> {
            try {
                return new Timespan(Math.addExact(x.millis(), y.millis()));
            } catch (ArithmeticException overflow) {
                return new Timespan(Long.MAX_VALUE);
            }
        });
        register('-', Timespan.class, Timespan.class, (x, y) -> new Timespan(Math.max(0, x.millis() - y.millis())));
        register('*', Timespan.class, Number.class, Arithmetic::scale);
        register('*', Number.class, Timespan.class, (x, y) -> scale(y, x));
        register('/', Timespan.class, Number.class, (x, y) -> {
            double scalar = y.doubleValue();
            return scalar <= 0 || !Double.isFinite(scalar) ? null : new Timespan((long) (x.millis() / scalar));
        });
        register('/', Timespan.class, Timespan.class, (x, y) -> (double) x.millis() / y.millis());
        register('+', Date.class, Timespan.class, (x, y) -> new Date(x.millis() + y.millis()));
        register('-', Date.class, Timespan.class, (x, y) -> new Date(x.millis() - y.millis()));
        registerDefault(Timespan.class, () -> new Timespan(0));
    }

    private static Timespan scale(Timespan timespan, Number by) {
        double scalar = by.doubleValue();
        if (scalar < 0 || !Double.isFinite(scalar)) {
            return null;
        }
        return new Timespan((long) Math.min(timespan.millis() * scalar, Long.MAX_VALUE));
    }

    /**
     * Registers what {@code operator} ({@code + - * / ^}) gives between a value of {@code left} and one of
     * {@code right}, in that order. Returning {@code null} means nothing.
     */
    @SuppressWarnings("unchecked")
    public static <A, B> void register(char operator, Class<A> left, Class<B> right,
            java.util.function.BiFunction<? super A, ? super B, ?> apply) {
        OPERATIONS.add(new Operation(operator, left, right, (java.util.function.BiFunction<Object, Object, Object>) apply));
    }

    /** What a missing operand counts as next to a value of {@code type}, as 0 does for numbers. */
    public static <T> void registerDefault(Class<T> type, java.util.function.Supplier<? extends T> value) {
        DEFAULTS.put(type, value);
    }

    /** A registered operation's result, or {@code null} if none applies. */
    private static Object other(char operator, Object a, Object b) {
        if (a == null && b != null) {
            a = defaultFor(b);
        } else if (b == null && a != null) {
            b = defaultFor(a);
        }
        if (a == null || b == null) {
            return null;
        }
        for (Operation operation : OPERATIONS) {
            if (operation.operator() == operator && operation.left().isInstance(a) && operation.right().isInstance(b)) {
                return operation.apply().apply(a, b);
            }
        }
        return null;
    }

    private static Object defaultFor(Object value) {
        for (java.util.Map.Entry<Class<?>, java.util.function.Supplier<?>> entry : DEFAULTS.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                return entry.getValue().get();
            }
        }
        return null;
    }

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
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() + y.doubleValue()) : other('+', a, b);
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
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() - y.doubleValue()) : other('-', a, b);
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
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() * y.doubleValue()) : other('*', a, b);
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
        return a instanceof Number x && b instanceof Number y ? (Object) (x.doubleValue() / y.doubleValue()) : other('/', a, b);
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
        return a instanceof Number x && b instanceof Number y ? (Object) Math.pow(x.doubleValue(), y.doubleValue())
                : other('^', a, b);
    }

    private static boolean isWhole(Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }
}
