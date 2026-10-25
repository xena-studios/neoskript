/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The names of the types values are, by their class, for syntax that says what type a value is at run time
 * (Skript's {@code debug info of %objects%}). A class's first type is its name.
 */
public final class TypeNames {

    private static final Map<Class<?>, String> NAMES = new ConcurrentHashMap<>();
    /** The name worked out for each class of value, most specific type first. */
    private static final Map<Class<?>, String> FOUND = new ConcurrentHashMap<>();

    private TypeNames() {}

    public static void register(Class<?> type, String name) {
        if (NAMES.putIfAbsent(type, name) == null) {
            FOUND.clear();
        }
    }

    /**
     * The name of the most specific type {@code value} is, as Skript's super class info: {@code 1} is an
     * integer and {@code 0.5} a number. Anything else is an object.
     */
    public static String of(Object value) {
        return FOUND.computeIfAbsent(value.getClass(), type -> {
            Class<?> best = null;
            for (Class<?> candidate : NAMES.keySet()) {
                if (candidate.isAssignableFrom(type) && (best == null || best.isAssignableFrom(candidate))) {
                    best = candidate;
                }
            }
            return best == null ? "object" : NAMES.get(best);
        });
    }
}
