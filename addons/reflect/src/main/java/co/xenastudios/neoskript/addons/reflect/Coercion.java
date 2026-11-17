/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Set;

/**
 * How script values become Java arguments and Java results become script values, as skript-reflect's
 * {@code JavaUtil.canConvert} and {@code convert}: any number to any numeric parameter, numeric arrays of the same
 * depth, a one-character text to a {@code char}, a {@link JavaType} to a {@code Class}, and {@link Null} to any object
 * parameter. Beyond skript-reflect, what NeoSkript itself converts: a timespan to a {@code Duration}, an item type to an
 * {@code ItemStack}.
 */
final class Coercion {

    static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(boolean.class, Boolean.class, byte.class, Byte.class,
            char.class, Character.class, double.class, Double.class, float.class, Float.class, int.class, Integer.class,
            long.class, Long.class, short.class, Short.class);

    static final Map<String, Class<?>> PRIMITIVES = Map.of("boolean", boolean.class, "byte", byte.class, "char",
            char.class, "double", double.class, "float", float.class, "int", int.class, "long", long.class, "short",
            short.class);

    private static final Set<Class<?>> NUMERIC = Set.of(byte.class, double.class, float.class, int.class, long.class,
            short.class, Byte.class, Double.class, Float.class, Integer.class, Long.class, Short.class);

    private Coercion() {}

    /** Whether {@code value} can be passed where {@code to} is wanted. */
    static boolean canConvert(Object value, Class<?> to) {
        if (value == null || value == Null.NULL) {
            return !to.isPrimitive();
        }
        if (to.isInstance(value)) {
            return true;
        }
        if (value instanceof Number && NUMERIC.contains(to)) {
            return true;
        }
        if (value instanceof ObjectWrapper wrapper) {
            return canConvert(wrapper.array(), to);
        }
        if (to.isArray() && value.getClass().isArray() && depth(to) == depth(value.getClass())
                && isNumeric(base(to)) && isNumeric(base(value.getClass()))) {
            return true;
        }
        if (to.isPrimitive() && WRAPPERS.get(to).isInstance(value)) {
            return true;
        }
        if (value instanceof String text && (to == char.class || to == Character.class) && text.length() == 1) {
            return true;
        }
        if (to == Class.class && value instanceof JavaType) {
            return true;
        }
        return scriptConversion(value, to) != null;
    }

    /**
     * {@code value} as {@code to} through NeoSkript's own conversions, which skript-reflect doesn't have: a timespan
     * as a {@code Duration}, a date as an {@code Instant}, an item type as an {@code ItemStack}, a slot as its item.
     * {@code null} if there's none. Only asked once nothing else fits, so a call whose arguments fit costs nothing more.
     */
    private static Object scriptConversion(Object value, Class<?> to) {
        if (to.isPrimitive() || to == Object.class || value instanceof JavaType || value instanceof ObjectWrapper) {
            return null;
        }
        try {
            co.xenastudios.neoskript.api.Values values = co.xenastudios.neoskript.api.NeoSkript.get().values();
            Object plain = values.toJava(value);
            if (to.isInstance(plain)) {
                return plain;
            }
            Object converted = values.convert(value, to);
            return to.isInstance(converted) ? converted : null;
        } catch (RuntimeException | LinkageError notThere) {
            // A converter that needs what isn't there: no conversion, so the call says nothing matches.
            return null;
        }
    }

    /** {@code value} as {@code to} wants it; {@link #canConvert} has said it can be. */
    static Object convert(Object value, Class<?> to) {
        if (value == null || value == Null.NULL) {
            return null;
        }
        if (value instanceof ObjectWrapper wrapper) {
            value = wrapper.array();
        }
        if (value instanceof Number number && NUMERIC.contains(to)) {
            return number(number, to);
        }
        if (to.isArray() && value.getClass().isArray() && isNumeric(base(to)) && !to.isInstance(value)) {
            return numericArray(value, base(to));
        }
        if (value instanceof String text && (to == char.class || to == Character.class)) {
            return text.charAt(0);
        }
        if (to == Class.class && value instanceof JavaType type) {
            return type.javaClass();
        }
        if (!to.isInstance(value)) {
            Object converted = scriptConversion(value, to);
            if (converted != null) {
                return converted;
            }
        }
        return value;
    }

    private static Object number(Number number, Class<?> to) {
        if (to == byte.class || to == Byte.class) {
            return number.byteValue();
        } else if (to == double.class || to == Double.class) {
            return number.doubleValue();
        } else if (to == float.class || to == Float.class) {
            return number.floatValue();
        } else if (to == int.class || to == Integer.class) {
            return number.intValue();
        } else if (to == long.class || to == Long.class) {
            return number.longValue();
        } else if (to == short.class || to == Short.class) {
            return number.shortValue();
        }
        return number;
    }

    private static Object numericArray(Object array, Class<?> to) {
        Class<?> component = array.getClass().getComponentType();
        int length = Array.getLength(array);
        if (component.isArray()) {
            Object result = Array.newInstance(arrayOf(to, depth(component)), length);
            for (int i = 0; i < length; i++) {
                Object inner = Array.get(array, i);
                Array.set(result, i, inner == null ? null : numericArray(inner, to));
            }
            return result;
        }
        Object result = Array.newInstance(to, length);
        for (int i = 0; i < length; i++) {
            Array.set(result, i, number((Number) Array.get(array, i), to));
        }
        return result;
    }

    /**
     * A Java result as scripts have it: an array as one value ({@link ObjectWrapper}), and everything else as it is.
     * Numbers keep their boxed type, as in skript-reflect, so an {@code Integer} passed back to Java is still one
     * ({@code {_set}.contains({_list}.size())} on a set of integers): NeoSkript's arithmetic, comparisons and texts
     * take any of Java's numbers, and widen them where they need to.
     */
    static Object fromJava(Object value) {
        if (value != null && value.getClass().isArray()) {
            return ObjectWrapper.of(value);
        }
        return value;
    }

    /** What a script value is as a Java object: the array an {@link ObjectWrapper} holds, and no value for null. */
    static Object toJava(Object value) {
        if (value instanceof ObjectWrapper wrapper) {
            return wrapper.array();
        }
        return value == Null.NULL ? null : value;
    }

    static Object boxPrimitiveArray(Object array) {
        Class<?> component = array.getClass().getComponentType();
        if (component == null || !component.isPrimitive()) {
            return array;
        }
        int length = Array.getLength(array);
        Object[] boxed = (Object[]) Array.newInstance(WRAPPERS.get(component), length);
        for (int i = 0; i < length; i++) {
            boxed[i] = Array.get(array, i);
        }
        return boxed;
    }

    static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(type) || NUMERIC.contains(type);
    }

    static int depth(Class<?> type) {
        int depth = 0;
        while (type.isArray()) {
            type = type.getComponentType();
            depth++;
        }
        return depth;
    }

    static Class<?> base(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type;
    }

    static Class<?> arrayOf(Class<?> type, int depth) {
        for (int i = 0; i < depth; i++) {
            type = type.arrayType();
        }
        return type;
    }

    /** A class's name as skript-reflect's messages give it: its simple name. */
    static String debugName(Class<?> type) {
        return type.getSimpleName();
    }

    /** The class of a value, as skript-reflect's messages give it: {@code Object} for null. */
    static Class<?> classOf(Object value) {
        value = toJava(value);
        return value == null ? Object.class : value.getClass();
    }
}
