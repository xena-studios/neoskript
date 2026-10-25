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
        // Timespans saturate at forever, which stays forever, as in Skript.
        register('+', Timespan.class, Timespan.class, (x, y) -> {
            try {
                return new Timespan(Math.addExact(x.millis(), y.millis()));
            } catch (ArithmeticException overflow) {
                return Timespan.FOREVER;
            }
        });
        register('-', Timespan.class, Timespan.class, (x, y) -> x.isInfinite() ? x
                : y.isInfinite() ? new Timespan(0) : new Timespan(Math.max(0, x.millis() - y.millis())));
        register('*', Timespan.class, Number.class, Arithmetic::scale);
        register('*', Number.class, Timespan.class, (x, y) -> scale(y, x));
        register('/', Timespan.class, Number.class, (x, y) -> {
            double scalar = y.doubleValue();
            if (scalar <= 0 || Double.isNaN(scalar)) {
                return null;
            }
            return x.isInfinite() ? x : new Timespan((long) (x.millis() / scalar));
        });
        register('/', Timespan.class, Timespan.class, (x, y) -> {
            if (x.isInfinite() || y.isInfinite()) {
                return x.isInfinite() && y.isInfinite() ? Double.NaN : x.isInfinite() ? Double.POSITIVE_INFINITY : 0.0;
            }
            return (double) x.millis() / y.millis();
        });
        register('+', Date.class, Timespan.class, (x, y) -> new Date(x.millis() + y.millis()));
        register('-', Date.class, Timespan.class, (x, y) -> new Date(x.millis() - y.millis()));
        // A time plus a timespan moves on by its game ticks; plus another time, by how long after midnight that is.
        register('+', TimeOfDay.class, Timespan.class, (x, y) -> new TimeOfDay((int) ((x.ticks() + y.ticks()) % 24000)));
        register('-', TimeOfDay.class, Timespan.class, (x, y) -> new TimeOfDay((int) ((x.ticks() - y.ticks()) % 24000)));
        register('+', TimeOfDay.class, TimeOfDay.class, (x, y) -> new TimeOfDay(x.ticks() + y.ticksAfterMidnight()));
        register('-', TimeOfDay.class, TimeOfDay.class, (x, y) -> new TimeOfDay(x.ticks() - y.ticksAfterMidnight()));
        registerDefault(Timespan.class, () -> new Timespan(0));
    }

    private static Timespan scale(Timespan timespan, Number by) {
        double scalar = by.doubleValue();
        if (scalar < 0 || Double.isNaN(scalar)) {
            return null;
        }
        if (timespan.isInfinite() || Double.isInfinite(scalar)) {
            return scalar == 0 || timespan.millis() == 0 ? new Timespan(0) : Timespan.FOREVER;
        }
        double millis = timespan.millis() * scalar;
        return millis >= Long.MAX_VALUE ? Timespan.FOREVER : new Timespan((long) millis);
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

    private record CustomOperation(String sign, Class<?> left, Class<?> right,
            java.util.function.BiFunction<Object, Object, Object> apply) {}

    private static final java.util.List<CustomOperation> CUSTOM = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an operator written as a word or sign of its own, as Skript's arithmetic lets addons add. */
    @SuppressWarnings("unchecked")
    public static <A, B> void registerCustom(String sign, Class<A> left, Class<B> right,
            java.util.function.BiFunction<? super A, ? super B, ?> apply) {
        CUSTOM.add(new CustomOperation(sign, left, right, (java.util.function.BiFunction<Object, Object, Object>) apply));
    }

    /** {@code a <sign> b} for a custom operator, or {@code null} if it doesn't apply to them. */
    public static Object custom(Object a, Object b, String sign) {
        if (a == null || b == null) {
            return null;
        }
        for (CustomOperation operation : CUSTOM) {
            if (operation.sign().equals(sign) && operation.left().isInstance(a) && operation.right().isInstance(b)) {
                return operation.apply().apply(a, b);
            }
        }
        return null;
    }

    /** What a missing operand counts as next to a value of {@code type}, as 0 does for numbers. */
    public static <T> void registerDefault(Class<T> type, java.util.function.Supplier<? extends T> value) {
        DEFAULTS.put(type, value);
    }

    /**
     * What can go on the right of {@code operator} with a value of {@code left} on its left, as Skript's
     * arithmetic change types are: numbers for numbers, texts added to texts, timespans for dates.
     */
    public static java.util.List<Class<?>> rightTypes(char operator, Class<?> left) {
        java.util.List<Class<?>> types = new java.util.ArrayList<>();
        if (Number.class.isAssignableFrom(left) && (operator == '+' || operator == '-')) {
            types.add(Number.class);
        }
        if (left == String.class && operator == '+') {
            types.add(String.class);
        }
        for (Operation operation : OPERATIONS) {
            if (operation.operator() == operator && operation.left().isAssignableFrom(left)
                    && !types.contains(operation.right())) {
                types.add(operation.right());
            }
        }
        return types;
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

    /** What {@link #ownOperation} gives when no operation of a number type of its own applies. */
    private static final Object NONE = new Object();

    /**
     * The result of an operation registered between two numbers of a type of their own, like money, which comes
     * before plain number arithmetic, as Skript's {@code money + money} is money; a missing operand is the type's
     * default. {@link #NONE} if there's no such operation.
     */
    private static Object ownOperation(char operator, Object a, Object b) {
        if (!ownNumber(a) && !ownNumber(b)) {
            return NONE;
        }
        if (a == null) {
            a = defaultFor(b);
        } else if (b == null) {
            b = defaultFor(a);
        }
        if (a == null || b == null) {
            return NONE;
        }
        for (Operation operation : OPERATIONS) {
            if (operation.operator() == operator && operation.left() != Number.class && operation.right() != Number.class
                    && operation.left().isInstance(a) && operation.right().isInstance(b)) {
                return operation.apply().apply(a, b);
            }
        }
        return NONE;
    }

    /** Whether the value is a number of a type of its own, not one of Java's. */
    private static boolean ownNumber(Object value) {
        return value instanceof Number && !value.getClass().getName().startsWith("java.");
    }

    private record Difference(Class<?> type, java.util.function.BiFunction<Object, Object, Object> difference) {}

    private static final java.util.List<Difference> DIFFERENCES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers what {@code difference between a and b} is for two values of {@code type}, as Skript's arithmetic. */
    @SuppressWarnings("unchecked")
    public static <T> void registerDifference(Class<T> type,
            java.util.function.BiFunction<? super T, ? super T, ?> difference) {
        DIFFERENCES.add(new Difference(type, (java.util.function.BiFunction<Object, Object, Object>) difference));
    }

    /**
     * {@code difference between a and b} for two values of a type that registered one ({@link #registerDifference}),
     * or else {@code otherwise}'s.
     */
    public static Object difference(Object a, Object b, java.util.function.BinaryOperator<Object> otherwise) {
        for (Difference difference : DIFFERENCES) {
            if (difference.type().isInstance(a) && difference.type().isInstance(b)) {
                return difference.difference().apply(a, b);
            }
        }
        return otherwise.apply(a, b);
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
        Object own = ownOperation('+', a, b);
        if (own != NONE) {
            return own;
        }
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
        Object own = ownOperation('-', a, b);
        if (own != NONE) {
            return own;
        }
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
        Object own = ownOperation('*', a, b);
        if (own != NONE) {
            return own;
        }
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
        Object own = ownOperation('/', a, b);
        if (own != NONE) {
            return own;
        }
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
        Object own = ownOperation('^', a, b);
        if (own != NONE) {
            return own;
        }
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
