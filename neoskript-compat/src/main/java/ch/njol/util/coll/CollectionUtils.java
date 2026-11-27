/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.util.coll;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Skript's array helpers, the ones addons use. */
public abstract class CollectionUtils {

    private CollectionUtils() {}

    public static <T> int indexOf(T[] array, T t) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length; i++) {
            if (Objects.equals(array[i], t)) {
                return i;
            }
        }
        return -1;
    }

    public static <T> boolean contains(T[] array, T o) {
        return indexOf(array, o) >= 0;
    }

    @SafeVarargs
    public static <T> boolean containsAny(T[] array, T... os) {
        if (os == null) {
            return false;
        }
        for (T o : os) {
            if (contains(array, o)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    public static <T> boolean containsAll(T[] array, T... os) {
        if (os == null) {
            return true;
        }
        for (T o : os) {
            if (!contains(array, o)) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean check(T[] array, Predicate<T> predicate, boolean and) {
        if (array == null) {
            return false;
        }
        for (T t : array) {
            boolean b = predicate.test(t);
            if (and && !b) {
                return false;
            }
            if (!and && b) {
                return true;
            }
        }
        return and;
    }

    public static boolean containsIgnoreCase(String[] array, String s) {
        if (array == null) {
            return false;
        }
        for (String a : array) {
            if (a == null ? s == null : a.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsSuperclass(Class<?>[] classes, Class<?> c) {
        if (classes == null || c == null) {
            return false;
        }
        for (Class<?> cl : classes) {
            if (cl.isAssignableFrom(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAnySuperclass(Class<?>[] classes, Class<?>... cs) {
        if (cs == null) {
            return false;
        }
        for (Class<?> c : cs) {
            if (containsSuperclass(classes, c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnyInstanceOf(Object object, Class<?>... classes) {
        for (Class<?> c : classes) {
            if (c.isInstance(object)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getRandom(T[] os) {
        return os == null || os.length == 0 ? null : os[java.util.concurrent.ThreadLocalRandom.current().nextInt(os.length)];
    }

    public static <T> T getRandom(List<T> os) {
        return os == null || os.isEmpty() ? null : os.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(os.size()));
    }

    @SafeVarargs
    public static <T> T[] array(final T... array) {
        return array;
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T[]> arrayType(Class<T> c) {
        return (Class<T[]>) Array.newInstance(c, 0).getClass();
    }

    public static <T> T[] subarray(T[] array, int startIndex, int endIndex) {
        return java.util.Arrays.copyOfRange(array, startIndex, endIndex);
    }

    public static Double[] wrap(double[] primitive) {
        Double[] wrapped = new Double[primitive.length];
        for (int i = 0; i < primitive.length; i++) {
            wrapped[i] = primitive[i];
        }
        return wrapped;
    }

    public static String toString(Collection<?> collection, boolean and) {
        StringBuilder text = new StringBuilder();
        int i = 0;
        for (Object o : collection) {
            if (i > 0) {
                text.append(i == collection.size() - 1 ? (and ? " and " : " or ") : ", ");
            }
            text.append(o);
            i++;
        }
        return text.toString();
    }
}
