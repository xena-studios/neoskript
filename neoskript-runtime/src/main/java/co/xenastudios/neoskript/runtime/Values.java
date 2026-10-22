/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Helpers the generated code calls to convert values. */
public final class Values {

    private static final Object[] EMPTY = new Object[0];

    private Values() {}

    /** A value as a plural argument: an array as is, {@code null} as empty, anything else as one element. */
    public static Object[] array(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        return value == null ? EMPTY : new Object[] {value};
    }

    /** A value as a singular argument: the first element of an array, or the value itself. */
    public static Object single(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0 ? null : array[0];
        }
        return value;
    }

    /** The values that are instances of one of {@code classes}: what a changed expression accepts. */
    public static Object[] narrow(Object value, Class<?>[] classes) {
        List<Object> values = new ArrayList<>();
        for (Object element : array(value)) {
            for (Class<?> type : classes) {
                if (type.isInstance(element)) {
                    values.add(element);
                    break;
                }
            }
        }
        return values.toArray();
    }

    /** {@code "5" parsed as integer}: the text through {@code parser}, or {@code null} if it isn't one. */
    @SuppressWarnings("unchecked")
    public static Object parse(Function<?, ?> parser, Object text) {
        Object value = single(text);
        return value == null ? null : ((Function<String, ?>) parser).apply(text(value).strip());
    }

    /** The values that are instances of {@code type}, for {@code number within {_x}}. */
    public static Object[] cast(Object value, Class<?> type) {
        List<Object> values = new ArrayList<>();
        for (Object element : array(value)) {
            if (type.isInstance(element)) {
                values.add(element);
            }
        }
        return values.toArray();
    }

    /** {@code array[index + offset]}, or {@code null} if that's outside the array: for next/previous loop values. */
    public static Object element(Object[] array, int index, int offset) {
        int i = index + offset;
        return i >= 0 && i < array.length ? array[i] : null;
    }

    /** The values of a list expression, with nested lists flattened. */
    public static Object[] list(Object[] items) {
        List<Object> values = new ArrayList<>(items.length);
        for (Object item : items) {
            if (item instanceof Object[] array) {
                for (Object element : array) {
                    if (element != null) {
                        values.add(element);
                    }
                }
            } else if (item != null) {
                values.add(item);
            }
        }
        return values.toArray();
    }

    /** How a value reads in text, as when embedded in a string with {@code %...%}. */
    public static String text(Object value) {
        if (value == null || (value instanceof Object[] empty && empty.length == 0)) {
            return "<none>";
        }
        if (value instanceof Object[] array) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    text.append(i == array.length - 1 ? " and " : ", ");
                }
                text.append(text(array[i]));
            }
            return text.toString();
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Double || value instanceof Float) {
            return decimal(((Number) value).doubleValue());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        Function<Object, String> converter = converter(value.getClass());
        return converter != null ? converter.apply(value) : value.toString();
    }

    /** Decimals as Skript shows them: at most two decimal places, without trailing zeros. */
    public static String decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
            return Long.toString((long) rounded);
        }
        return java.math.BigDecimal.valueOf(rounded).stripTrailingZeros().toPlainString();
    }

    private record Conversion(Class<?> from, Function<Object, Object> converter) {}

    private static final Map<Class<?>, List<Conversion>> CONVERSIONS = new ConcurrentHashMap<>();

    /** Registers how values of {@code from} (and its subtypes) become a {@code to}. */
    @SuppressWarnings("unchecked")
    public static <F, T> void registerConverter(Class<F> from, Class<T> to, Function<? super F, ? extends T> converter) {
        CONVERSIONS.computeIfAbsent(to, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(new Conversion(from, value -> ((Function<Object, Object>) converter).apply(value)));
    }

    /** Whether anything converts to {@code to}, so placeholders of that type need {@link #convert}. */
    public static boolean convertsTo(Class<?> to) {
        return CONVERSIONS.containsKey(to);
    }

    /**
     * Converts what isn't already a {@code to} into one where a converter can, as when a player is used
     * where a location is expected. Values nothing converts are kept, for the element to deal with.
     */
    public static Object convert(Object value, Class<?> to) {
        if (value instanceof Object[] array) {
            Object[] converted = null;
            for (int i = 0; i < array.length; i++) {
                Object element = convertOne(array[i], to);
                if (element != array[i]) {
                    if (converted == null) {
                        converted = array.clone();
                    }
                    converted[i] = element;
                }
            }
            return converted == null ? array : converted;
        }
        return convertOne(value, to);
    }

    private static Object convertOne(Object value, Class<?> to) {
        if (value == null || to.isInstance(value)) {
            return value;
        }
        for (Conversion conversion : CONVERSIONS.getOrDefault(to, List.of())) {
            if (conversion.from().isInstance(value)) {
                Object converted = conversion.converter().apply(value);
                if (converted != null) {
                    return converted;
                }
            }
        }
        return value;
    }

    private static final Map<Class<?>, Function<Object, String>> CONVERTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Function<Object, String>>> RESOLVED = new ConcurrentHashMap<>();

    /** Registers how values of {@code type} (and its subtypes) read as text. */
    @SuppressWarnings("unchecked")
    public static <T> void registerText(Class<T> type, Function<? super T, String> toText) {
        CONVERTERS.put(type, value -> ((Function<Object, String>) toText).apply(value));
        RESOLVED.clear();
    }

    private static Function<Object, String> converter(Class<?> type) {
        return RESOLVED.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null; c = c.getSuperclass()) {
                if (CONVERTERS.containsKey(c)) {
                    return Optional.of(CONVERTERS.get(c));
                }
                for (Class<?> i : c.getInterfaces()) {
                    Function<Object, String> found = interfaceConverter(i);
                    if (found != null) {
                        return Optional.of(found);
                    }
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static Function<Object, String> interfaceConverter(Class<?> type) {
        if (CONVERTERS.containsKey(type)) {
            return CONVERTERS.get(type);
        }
        for (Class<?> parent : type.getInterfaces()) {
            Function<Object, String> found = interfaceConverter(parent);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
