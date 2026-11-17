/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import co.xenastudios.neoskript.api.NeoSkript;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.List;

/** Values crossing between NeoSkript and an addon: arguments as the classes an addon asks for, results back. */
final class CompatValuesBridge {

    private CompatValuesBridge() {}

    /** An addon's result as NeoSkript's values: one value, or an array of them. */
    static Object result(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Object[] array) {
            Object[] values = new Object[array.length];
            int count = 0;
            for (Object element : array) {
                if (element != null) {
                    values[count++] = NeoSkript.get().values().fromJava(CompatAdapters.toNeoSkript(element));
                }
            }
            return count == values.length ? values : java.util.Arrays.copyOf(values, count);
        }
        return NeoSkript.get().values().fromJava(CompatAdapters.toNeoSkript(value));
    }

    /** {@code type}, boxed if it's primitive. */
    static Class<?> boxed(Class<?> type) {
        return MethodType.methodType(type).wrap().returnType();
    }

    /** {@code value} as a {@code type}, or null if it isn't one and can't be made one. */
    static Object as(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }
        Class<?> boxed = MethodType.methodType(type).wrap().returnType();
        Object adapted = CompatAdapters.toSkript(value, boxed);
        if (boxed.isInstance(adapted)) {
            return adapted;
        }
        Class<?> neoskript = CompatAdapters.neoskriptClass(boxed);
        if (neoskript != boxed) {
            Object converted = Compat.get().conversions().convert(value, neoskript);
            return converted == null ? null : CompatAdapters.toSkript(converted, boxed);
        }
        return Compat.get().conversions().convert(value, boxed);
    }

    /** An argument's values as an array of {@code type}: those that are or convert to one. */
    static Object[] array(Object argument, Class<?> type) {
        Class<?> boxed = MethodType.methodType(type).wrap().returnType();
        Object[] given = argument == null ? new Object[0]
                : argument instanceof Object[] array ? array : new Object[] {argument};
        Object[] values = (Object[]) Array.newInstance(boxed, given.length);
        int count = 0;
        for (Object value : given) {
            Object converted = as(value, boxed);
            if (converted != null) {
                values[count++] = converted;
            }
        }
        if (count == values.length) {
            return values;
        }
        Object[] kept = (Object[]) Array.newInstance(boxed, count);
        System.arraycopy(values, 0, kept, 0, count);
        return kept;
    }

    /** A function's arguments as its parameters' classes. */
    static Object[] functionArguments(List<CompatFunction.Param<?>> parameters, Object[] arguments) {
        Object[] converted = new Object[parameters.size()];
        for (int i = 0; i < converted.length && i < arguments.length; i++) {
            Class<?> type = parameters.get(i).type();
            if (type.isArray()) {
                converted[i] = array(arguments[i], type.getComponentType());
            } else {
                Object[] values = array(arguments[i], type);
                converted[i] = values.length == 0 ? null : values[0];
            }
        }
        return converted;
    }
}
