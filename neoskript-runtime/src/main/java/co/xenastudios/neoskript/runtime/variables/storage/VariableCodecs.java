/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The codecs for each savable type, by Skript type name and by Java class. */
public final class VariableCodecs {

    /** A value as saved: Skript's type name and the encoded bytes. */
    public record Encoded(String type, byte[] bytes) {}

    private record Registration(String name, Class<?> type, VariableCodec<?> codec) {}

    private final Map<String, Registration> byName = new HashMap<>();
    private final Map<Class<?>, Registration> byClass = new LinkedHashMap<>();
    private final Map<Class<?>, Optional<Registration>> resolved = new ConcurrentHashMap<>();

    public <T> void register(String name, Class<T> type, VariableCodec<T> codec) {
        Registration registration = new Registration(name, type, codec);
        byName.put(name, registration);
        byClass.put(type, registration);
        resolved.clear();
    }

    /** Encodes a value, or returns {@code null} if its type can't be saved. */
    @SuppressWarnings("unchecked")
    public Encoded encode(Object value) {
        if (value instanceof RawValue raw) {
            return new Encoded(raw.type(), raw.bytes());
        }
        Registration registration = registration(value.getClass());
        if (registration == null) {
            return null;
        }
        SkriptBinaryWriter out = new SkriptBinaryWriter(registration.name());
        ((VariableCodec<Object>) registration.codec()).encode(value, out);
        return new Encoded(registration.name(), out.toByteArray());
    }

    /**
     * Decodes a saved value. Types without a codec, and values the codec can't read, come back as a
     * {@link RawValue} with the original bytes.
     */
    public Object decode(String type, byte[] bytes) {
        Registration registration = byName.get(type);
        if (registration != null) {
            try {
                SkriptBinaryReader in = new SkriptBinaryReader(type, bytes);
                Object value = registration.codec().decode(in);
                if (value != null && in.atEnd()) {
                    return value;
                }
            } catch (RuntimeException e) {
                // Keep the bytes as they are.
            }
        }
        return new RawValue(type, bytes);
    }

    private Registration registration(Class<?> type) {
        return resolved.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null; c = c.getSuperclass()) {
                if (byClass.containsKey(c)) {
                    return Optional.of(byClass.get(c));
                }
            }
            for (Map.Entry<Class<?>, Registration> entry : byClass.entrySet()) {
                if (entry.getKey().isAssignableFrom(t)) {
                    return Optional.of(entry.getValue());
                }
            }
            return Optional.empty();
        }).orElse(null);
    }
}
