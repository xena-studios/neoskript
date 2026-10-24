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

    /** Keeps only values of {@code type}: a single value or {@code null}, or an array of the ones that are. */
    public static Object filter(Object value, Class<?> type) {
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (element != null && !type.isInstance(element)) {
                    return cast(array, type);
                }
            }
            return array;
        }
        return type.isInstance(value) ? value : null;
    }

    /** {@link #filter}, keeping the indices of keyed values ({@link KeyedValues}). */
    public static Object filterKeyed(Object value, Class<?> type) {
        return value instanceof KeyedValues keyed ? keyed.only(type) : filter(value, type);
    }

    /** What a loop goes through: the values, or a queue's elements, which looping takes out of it. */
    public static Object[] loopValues(Object[] values) {
        return loopedQueue(values) != null ? new Object[0] : values;
    }

    /** The queue a loop over {@code values} takes from as it goes, or {@code null} if they aren't one. */
    public static java.util.Queue<?> loopedQueue(Object[] values) {
        return values.length == 1 && values[0] instanceof java.util.Queue<?> queue ? queue : null;
    }

    /**
     * The values a loop over a queue has taken, with the queue's next value added once the loop reaches
     * {@code index}: it sees what was added to the queue while looping, as in Skript.
     */
    public static Object[] takeFromQueue(Object[] taken, int index, java.util.Queue<?> queue) {
        if (queue == null || index < taken.length) {
            return taken;
        }
        Object next = queue.poll();
        if (next == null) {
            return taken;
        }
        Object[] more = java.util.Arrays.copyOf(taken, index + 1);
        more[index] = next;
        return more;
    }

    /** One of {@code values} at random, or {@code null} if there are none. */
    public static Object random(Object[] values) {
        return values.length == 0 ? null : values[java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length)];
    }

    /** Whether a value counts as true for {@code set whether X to ...}: the boolean true. */
    public static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(single(value));
    }

    /** Adds a value, or every value of an array, to {@code list}: what an inline function collects. */
    public static void addAll(List<Object> list, Object value) {
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (element != null) {
                    list.add(element);
                }
            }
        } else if (value != null) {
            list.add(value);
        }
    }

    /** {@code values} sorted by {@code keys} (one per value), keeping equal keys in order. */
    public static Object[] sortByKeys(Object[] values, Object[] keys, boolean descending) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> {
            Integer c = Comparison.order(keys[x], keys[y]);
            int result = c == null ? 0 : c;
            return descending ? -result : result;
        });
        Object[] sorted = new Object[values.length];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = values[order[i]];
        }
        return sorted;
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

    /**
     * What {@code transform} gives a list: if each value became exactly one, those at the same indices;
     * otherwise all of them, numbered from 1.
     *
     * @param results the values each value became, in order
     * @param keys the list's indices, or {@code null}
     */
    public static Object transformed(java.util.List<Object[]> results, String[] keys) {
        boolean oneEach = keys != null && keys.length == results.size();
        java.util.List<Object> all = new java.util.ArrayList<>();
        for (Object[] result : results) {
            oneEach &= result.length == 1;
            java.util.Collections.addAll(all, result);
        }
        return oneEach ? new KeyedValues(all.toArray(), keys) : all.toArray();
    }

    /**
     * {@code value} rounded to a whole number, kept within an {@code int}: infinity is the largest, and
     * not-a-number is 0, where a plain cast would wrap round to nonsense.
     */
    public static int toInt(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }

    /** A random index below {@code size}: which of an or-list is meant. */
    public static int randomIndex(int size) {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(size);
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

    private static final Map<Class<?>, java.util.function.UnaryOperator<Object>> CLONERS = new ConcurrentHashMap<>();

    /** Registers how values of {@code type} are copied when they're stored in a variable. */
    @SuppressWarnings("unchecked")
    public static <T> void registerCloner(Class<T> type, java.util.function.UnaryOperator<T> cloner) {
        CLONERS.put(type, value -> ((java.util.function.UnaryOperator<Object>) (Object) cloner).apply(value));
    }

    /**
     * The value to store in a variable: a copy of values that can be changed in place, like items, so
     * changing a variable's item doesn't change the item it was set from, as in Skript.
     */
    public static Object copy(Object value) {
        if (value == null || CLONERS.isEmpty()) {
            return value;
        }
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            java.util.function.UnaryOperator<Object> cloner = CLONERS.get(type);
            if (cloner != null) {
                return cloner.apply(value);
            }
        }
        return value;
    }

    private record Changer(Class<?> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<Object, Object[]> change) {}

    private static final List<Changer> CHANGERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers how {@code mode} changes a value of {@code owner} that's itself the target. */
    @SuppressWarnings("unchecked")
    public static <T> void registerChanger(Class<T> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<? super T, Object[]> change) {
        CHANGERS.add(new Changer(owner, mode, (java.util.function.BiConsumer<Object, Object[]>) change));
    }

    /** Whether anything can change values this way, so {@code give X to player} compiles. */
    public static boolean hasChanger(co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        return CHANGERS.stream().anyMatch(c -> c.mode() == mode);
    }

    /** Changes each target value with the first changer registered for its class and {@code mode}. */
    public static void changeValues(Object targets, Object delta, co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        Object[] values = array(delta);
        for (Object target : array(targets)) {
            for (Changer changer : CHANGERS) {
                if (changer.mode() == mode && changer.owner().isInstance(target)) {
                    changer.change().accept(target, values);
                    break;
                }
            }
        }
    }

    /**
     * Changes {@code target} in place with the first changer registered for its class and {@code mode}, as
     * {@code add "x" to {queue}} changes the queue in the variable. Whether there was one.
     */
    public static boolean changeValue(Object target, Object[] values, co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        for (Changer changer : CHANGERS) {
            if (changer.mode() == mode && changer.owner().isInstance(target)) {
                changer.change().accept(target, values);
                return true;
            }
        }
        return false;
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
