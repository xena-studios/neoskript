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
            // A value without a codec is saved as what it converts to, if that has one: a slot as its item.
            for (Registration candidate : byName.values()) {
                Object converted = co.xenastudios.neoskript.runtime.Values.convert(value, candidate.type());
                if (converted != value && candidate.type().isInstance(converted)) {
                    return encode(converted);
                }
            }
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
    private boolean reencodes(Object value, String type, byte[] bytes) {
        Encoded encoded = encode(value);
        return encoded != null && encoded.type().equals(type) && java.util.Arrays.equals(encoded.bytes(), bytes);
    }

    public Object decode(String type, byte[] bytes) {
        Registration registration = byName.get(type);
        if (registration != null) {
            try {
                SkriptBinaryReader in = new SkriptBinaryReader(type, bytes);
                Object value = registration.codec().decode(in);
                // Only values that encode back to the same bytes are decoded, so a value with parts
                // NeoSkript doesn't model (an item that's several items) is kept as Skript saved it.
                if (value != null && in.atEnd() && reencodes(value, type, bytes)) {
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
