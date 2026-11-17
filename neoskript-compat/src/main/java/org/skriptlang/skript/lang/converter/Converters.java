/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.converter;

import co.xenastudios.neoskript.compat.Compat;
import java.lang.reflect.Array;

/** Converting values from one class to another, as scripts do where a placeholder wants another type. */
public final class Converters {

    private Converters() {}

    public static <F, T> void registerConverter(Class<F> fromType, Class<T> toType, Converter<F, T> converter) {
        registerConverter(fromType, toType, converter, 0);
    }

    public static <F, T> void registerConverter(Class<F> fromType, Class<T> toType, Converter<F, T> converter, int flags) {
        Compat.get().conversions().register(fromType, toType, converter, flags);
    }

    public static boolean exactConverterExists(Class<?> fromType, Class<?> toType) {
        return Compat.get().conversions().exists(fromType, toType, true);
    }

    public static boolean converterExists(Class<?> fromType, Class<?> toType) {
        return toType.isAssignableFrom(fromType) || Compat.get().conversions().exists(fromType, toType, false);
    }

    public static boolean converterExists(Class<?> fromType, Class<?>... toTypes) {
        for (Class<?> toType : toTypes) {
            if (converterExists(fromType, toType)) {
                return true;
            }
        }
        return false;
    }

    public static <From, To> To convert(From from, Class<To> toType) {
        return Compat.get().conversions().convert(from, toType);
    }

    @SuppressWarnings("unchecked")
    public static <From, To> To convert(From from, Class<? extends To>[] toTypes) {
        for (Class<? extends To> toType : toTypes) {
            To converted = convert(from, (Class<To>) toType);
            if (converted != null) {
                return converted;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <To> To[] convert(Object[] from, Class<To> toType) {
        if (from == null) {
            return (To[]) Array.newInstance(toType, 0);
        }
        java.util.List<To> out = new java.util.ArrayList<>(from.length);
        for (Object o : from) {
            To converted = convert(o, toType);
            if (converted != null) {
                out.add(converted);
            }
        }
        return out.toArray((To[]) Array.newInstance(toType, out.size()));
    }

    @SuppressWarnings("unchecked")
    public static <To> To[] convert(Object[] from, Class<? extends To>[] toTypes, Class<To> superType) {
        if (from == null) {
            return (To[]) Array.newInstance(superType, 0);
        }
        java.util.List<To> out = new java.util.ArrayList<>(from.length);
        for (Object o : from) {
            To converted = convert(o, toTypes);
            if (converted != null) {
                out.add(converted);
            }
        }
        return out.toArray((To[]) Array.newInstance(superType, out.size()));
    }

    @SuppressWarnings("unchecked")
    public static <From, To> To[] convert(From[] from, Class<To> toType, Converter<? super From, ? extends To> converter) {
        java.util.List<To> out = new java.util.ArrayList<>(from.length);
        for (From o : from) {
            To converted = o == null ? null : converter.convert(o);
            if (converted != null) {
                out.add(converted);
            }
        }
        return out.toArray((To[]) Array.newInstance(toType, out.size()));
    }

    public static <To> To convertStrictly(Object from, Class<To> toType) {
        To converted = convert(from, toType);
        if (converted == null) {
            throw new ClassCastException("Cannot convert '" + from + "' to " + toType.getName());
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    public static <From, To> To[] convertUnsafe(From[] from, Class<?> toType, Converter<? super From, ? extends To> converter) {
        return convert(from, (Class<To>) toType, converter);
    }
}
