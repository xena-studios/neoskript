/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.yggdrasil;

import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A value's fields, by name, as a serializer saves it: primitives and objects. As Skript's: names are lowercased
 * ({@code boundID} is {@code boundid}), and kept in a hash map, whose order is the order they're written in.
 */
public final class Fields {

    private final Map<String, Object> values = new HashMap<>();
    private final Set<String> primitives = new HashSet<>();

    private static String name(String field) {
        return field.toLowerCase(Locale.ENGLISH);
    }

    public Fields() {}

    public static Fields singletonObject(String id, Object value) {
        Fields fields = new Fields();
        fields.putObject(id, value);
        return fields;
    }

    public static Fields singletonPrimitive(String id, Object value) {
        Fields fields = new Fields();
        fields.putPrimitive(id, value);
        return fields;
    }

    public int size() {
        return values.size();
    }

    public void putObject(String field, Object value) {
        values.put(name(field), value);
        primitives.remove(name(field));
    }

    public void putPrimitive(String field, Object value) {
        values.put(name(field), value);
        primitives.add(name(field));
    }

    public boolean contains(String field) {
        return values.containsKey(name(field));
    }

    public boolean hasField(String field) {
        return values.containsKey(name(field));
    }

    /** Whether a field holds a primitive: not one of Skript's methods. */
    public boolean isPrimitive(String field) {
        return primitives.contains(name(field));
    }

    /** The fields, by (lowercased) name, in the order Skript writes them: not one of Skript's methods. */
    public Map<String, Object> values() {
        return java.util.Collections.unmodifiableMap(values);
    }

    public Object getObject(String field) throws StreamCorruptedException {
        if (!values.containsKey(name(field))) {
            throw new StreamCorruptedException("Nonexistent field " + field);
        }
        return values.get(name(field));
    }

    public <T> T getObject(String field, Class<T> expectedType) throws StreamCorruptedException {
        Object value = getObject(field);
        if (value != null && !expectedType.isInstance(value)) {
            throw new StreamCorruptedException("Field " + field + " of " + value.getClass() + ", but expected "
                    + expectedType);
        }
        return expectedType.cast(value);
    }

    public Object getPrimitive(String field) throws StreamCorruptedException {
        return getObject(field);
    }

    @SuppressWarnings("unchecked")
    public <T> T getPrimitive(String field, Class<T> expectedType) throws StreamCorruptedException {
        Object value = getObject(field);
        Class<?> boxed = java.lang.invoke.MethodType.methodType(expectedType).wrap().returnType();
        if (!boxed.isInstance(value)) {
            throw new StreamCorruptedException("Field " + field + " of " + value + ", but expected " + expectedType);
        }
        return (T) value;
    }

    public <T> T getAndRemoveObject(String field, Class<T> expectedType) throws StreamCorruptedException {
        T value = getObject(field, expectedType);
        removeField(field);
        return value;
    }

    public <T> T getAndRemovePrimitive(String field, Class<T> expectedType) throws StreamCorruptedException {
        T value = getPrimitive(field, expectedType);
        removeField(field);
        return value;
    }

    public boolean removeField(String field) {
        primitives.remove(name(field));
        return values.remove(name(field)) != null;
    }
}
