/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
public record KeyedValues(Object[] values, String[] keys) implements co.xenastudios.neoskript.api.element.Keyed {

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

    /**
     * An addon's result as scripts have values: a map is keyed values, each key an index, with a nested map, collection
     * or array making a list under its key ({@code home::x}); a collection or an array is several values; anything else
     * is itself. A map or list that contains itself, one nested deeper than {@value #MAX_DEPTH}, or one with more than
     * {@value #MAX_NODES} maps, lists and values in all throws {@link IllegalArgumentException}.
     */
    public static Object result(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> flat = new LinkedHashMap<>();
            flatten("", map, flat, 0, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                    new int[1]);
            return new KeyedValues(flat.values().toArray(), flat.keySet().toArray(String[]::new));
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(java.util.Objects::nonNull).toArray();
        }
        if (value instanceof Object[] array) {
            return java.util.Arrays.stream(array).filter(java.util.Objects::nonNull).toArray();
        }
        return value;
    }

    /** {@link #result} for a key-aware expression: only a map changes, so other values stay as they were. */
    public static Object mapResult(Object value) {
        return value instanceof Map<?, ?> ? result(value) : value;
    }

    /** How deep maps and lists in a result are followed, against one that contains itself. */
    public static final int MAX_DEPTH = 64;

    /** How many maps, lists and values a result may have in all, so one that shares its parts can't take forever. */
    public static final int MAX_NODES = 1_000_000;

    /**
     * Flattens {@code value} into {@code into}. {@code path} holds the maps and lists being flattened above it, by
     * identity, so one that contains itself throws at once rather than after its paths are walked; {@code nodes}
     * counts every part, so one that holds the same map many times over is stopped too.
     */
    private static void flatten(String prefix, Object value, Map<String, Object> into, int depth,
            java.util.Set<Object> path, int[] nodes) {
        if (value == null) {
            return;
        }
        if (++nodes[0] > MAX_NODES) {
            throw new IllegalArgumentException("The result has more than " + MAX_NODES + " parts");
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("The result is nested more than " + MAX_DEPTH + " deep");
        }
        boolean container = value instanceof Map<?, ?> || value instanceof java.util.Collection<?>
                || value instanceof Object[];
        if (container && !path.add(value)) {
            throw new IllegalArgumentException("The result contains itself");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        flatten(child(prefix, String.valueOf(entry.getKey())), entry.getValue(), into, depth + 1, path,
                                nodes);
                    }
                }
            } else if (container) {
                List<Object> items = value instanceof java.util.Collection<?> collection ? new ArrayList<>(collection)
                        : java.util.Arrays.asList((Object[]) value);
                for (int i = 0; i < items.size(); i++) {
                    flatten(child(prefix, Integer.toString(i + 1)), items.get(i), into, depth + 1, path, nodes);
                }
            } else if (!prefix.isEmpty()) {
                into.put(prefix, value);
            }
        } finally {
            if (container) {
                path.remove(value);
            }
        }
    }

    /** The index of {@code key} in the list at {@code prefix}: {@code rows::1}. */
    private static String child(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "::" + key;
    }
}
