/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoSkript's own values that NeoSkript's API has no type for yet: dates and timespans (records with
 * {@code millis()}), and list variables passed with their indices ({@code KeyedValues}, a record with
 * {@code values()} and {@code keys()}). Read by their accessors; anything else isn't one.
 */
final class ScriptValues {

    private static final String RUNTIME = "co.xenastudios.neoskript.runtime.";
    /** Each accessor by its class and name, or an empty optional where there's none. */
    private static final Map<String, java.util.Optional<MethodHandle>> ACCESSORS = new ConcurrentHashMap<>();

    private ScriptValues() {}

    /**
     * A date's milliseconds since 1970.
     *
     * @param value a value
     * @return the milliseconds, or {@link Long#MIN_VALUE} if the value isn't a date
     */
    static long dateMillis(Object value) {
        return millis(value, RUNTIME + "Date");
    }

    /**
     * A timespan's milliseconds.
     *
     * @param value a value
     * @return the milliseconds, or {@link Long#MIN_VALUE} if the value isn't a timespan
     */
    static long timespanMillis(Object value) {
        return millis(value, RUNTIME + "Timespan");
    }

    private static long millis(Object value, String className) {
        if (value == null || !value.getClass().getName().equals(className)) {
            return Long.MIN_VALUE;
        }
        Object millis = call(value, "millis");
        return millis instanceof Long number ? number : Long.MIN_VALUE;
    }

    /**
     * A list variable passed with its indices: its values and their indices, or {@code null} if {@code value}
     * isn't one.
     *
     * @param value an argument
     * @return {values, keys}, or {@code null}
     */
    static Object[][] keyed(Object value) {
        if (value == null || !value.getClass().getName().equals(RUNTIME + "KeyedValues")) {
            return null;
        }
        Object values = call(value, "values");
        Object keys = call(value, "keys");
        return values instanceof Object[] v && keys instanceof Object[] k && v.length == k.length
                ? new Object[][] {v, k} : null;
    }

    /**
     * An argument's values: those of a list passed with its indices, an array's, or the value alone.
     *
     * @param value an argument
     * @return its values, without nulls
     */
    static Object[] values(Object value) {
        Object[][] keyed = keyed(value);
        Object[] values = keyed != null ? keyed[0] : value instanceof Object[] array ? array
                : value == null ? new Object[0] : new Object[] {value};
        return java.util.Arrays.stream(values).filter(java.util.Objects::nonNull).toArray();
    }

    private static Object call(Object value, String accessor) {
        java.util.Optional<MethodHandle> handle = ACCESSORS.computeIfAbsent(value.getClass().getName() + "#" + accessor,
                key -> {
                    try {
                        return java.util.Optional.of(MethodHandles.publicLookup().findVirtual(value.getClass(),
                                accessor, MethodType.methodType(accessorType(accessor))));
                    } catch (ReflectiveOperationException | RuntimeException notThere) {
                        return java.util.Optional.empty();
                    }
                });
        if (handle.isEmpty()) {
            return null;
        }
        try {
            return handle.get().invoke(value);
        } catch (Throwable failed) {
            return null;
        }
    }

    private static Class<?> accessorType(String accessor) {
        return switch (accessor) {
            case "millis" -> long.class;
            case "values" -> Object[].class;
            default -> String[].class;
        };
    }
}
