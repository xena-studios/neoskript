/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.comparator;

import co.xenastudios.neoskript.compat.Compat;

/** Comparing values, as {@code is} and {@code is greater than} do. */
public final class Comparators {

    private Comparators() {}

    public static <T1, T2> void registerComparator(Class<T1> firstType, Class<T2> secondType,
            Comparator<T1, T2> comparator) {
        Compat.get().comparisons().register(firstType, secondType, comparator);
    }

    public static boolean exactComparatorExists(Class<?> firstType, Class<?> secondType) {
        return Compat.get().comparisons().exists(firstType, secondType, true);
    }

    public static boolean comparatorExists(Class<?> firstType, Class<?> secondType) {
        return Compat.get().comparisons().exists(firstType, secondType, false);
    }

    public static <T1, T2> Relation compare(T1 first, T2 second) {
        return Compat.get().comparisons().compare(first, second);
    }
}
