/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Values with the indices they came from, as {@code keyed {_list::*}} gives them: setting a list to them, or
 * passing them to a function, keeps the indices rather than numbering the values from 1.
 *
 * @param values the values, none {@code null}
 * @param keys each value's index
 */
public record KeyedValues(Object[] values, String[] keys) {

    /** {@code value} with its indices, numbered from 1 if it has none. */
    public static KeyedValues of(Object value) {
        if (value instanceof KeyedValues keyed) {
            return keyed;
        }
        Object[] values = Values.list(new Object[] {value});
        String[] keys = new String[values.length];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Integer.toString(i + 1);
        }
        return new KeyedValues(values, keys);
    }

    /** The values alone, if {@code value} is keyed; otherwise {@code value} itself. */
    public static Object plain(Object value) {
        return value instanceof KeyedValues keyed ? keyed.values() : value;
    }

    /**
     * Joins keyed and plain parts, as {@code (keyed {_a::*}), "x" and "y"}: keyed values keep their indices,
     * and the others take the lowest whole-number indices not already taken.
     */
    public static KeyedValues merge(Object[] parts) {
        Map<String, Object> keyed = new LinkedHashMap<>();
        List<Object> plain = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof KeyedValues values) {
                for (int i = 0; i < values.values().length; i++) {
                    keyed.putIfAbsent(values.keys()[i], values.values()[i]);
                }
            } else {
                Values.addAll(plain, part);
            }
        }
        int next = 1;
        for (Object value : plain) {
            while (keyed.containsKey(Integer.toString(next))) {
                next++;
            }
            keyed.put(Integer.toString(next++), value);
        }
        return new KeyedValues(keyed.values().toArray(), keyed.keySet().toArray(String[]::new));
    }

    /** Only the values that are instances of {@code type}, with their keys. */
    public KeyedValues only(Class<?> type) {
        List<Object> kept = new ArrayList<>(values.length);
        List<String> keptKeys = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            if (type.isInstance(values[i])) {
                kept.add(values[i]);
                keptKeys.add(keys[i]);
            }
        }
        return kept.size() == values.length ? this : new KeyedValues(kept.toArray(), keptKeys.toArray(String[]::new));
    }
}
