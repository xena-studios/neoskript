/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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

    private static volatile VariableCodecs current;

    /** The codecs most recently set up, for storing values elsewhere too, like in persistent data. */
    public static VariableCodecs current() {
        return current;
    }

    public VariableCodecs() {
        current = this;
    }

    /** A value as saved: Skript's type name and the encoded bytes. */
    public record Encoded(String type, byte[] bytes) {}

    private record Registration(String name, Class<?> type, VariableCodec<?> codec) {}

    // Replaced whole, never changed in place, so a save reading them never sees one half changed.
    private volatile Map<String, Registration> byName = new HashMap<>();
    private volatile Map<Class<?>, Registration> byClass = new LinkedHashMap<>();
    private final Map<Class<?>, Optional<Registration>> resolved = new ConcurrentHashMap<>();

    public synchronized <T> void register(String name, Class<T> type, VariableCodec<T> codec) {
        Registration registration = new Registration(name, type, codec);
        Map<String, Registration> names = new HashMap<>(byName);
        names.put(name, registration);
        Map<Class<?>, Registration> classes = new LinkedHashMap<>(byClass);
        classes.put(type, registration);
        byName = names;
        byClass = classes;
        resolved.clear();
    }

    /**
     * Whether saving {@code value} runs code {@code loader} loaded: its own class, or the codec that saves it, which
     * an addon may register for a type it didn't load (a JDK or Bukkit type).
     */
    public boolean usesCodeOf(Object value, ClassLoader loader) {
        if (value == null || value instanceof RawValue) {
            return false;
        }
        if (value.getClass().getClassLoader() == loader) {
            return true;
        }
        Registration registration = registration(value.getClass());
        return registration != null && registration.codec() != null
                && registration.codec().getClass().getClassLoader() == loader;
    }

    /**
     * Drops the codecs {@code loader} loaded, and those for its classes, as when their addon is disabled, so it can be
     * freed. Values they saved stay as their bytes ({@link RawValue}).
     */
    public synchronized void forget(ClassLoader loader) {
        java.util.function.Predicate<Registration> owned = registration -> registration.type().getClassLoader() == loader
                || (registration.codec() != null && registration.codec().getClass().getClassLoader() == loader);
        Map<String, Registration> names = new HashMap<>(byName);
        names.values().removeIf(owned);
        Map<Class<?>, Registration> classes = new LinkedHashMap<>(byClass);
        classes.values().removeIf(owned);
        byName = names;
        byClass = classes;
        resolved.clear();
    }

    /**
     * Values of {@code type} can't be saved, even where they're also of a type that can be, as Skript's type for
     * them has no serializer: money, which is a number.
     */
    public synchronized void unsavable(Class<?> type) {
        Map<Class<?>, Registration> classes = new LinkedHashMap<>(byClass);
        classes.put(type, new Registration(null, type, null));
        byClass = classes;
        resolved.clear();
    }

    /** Encodes a value, or returns {@code null} if its type can't be saved. */
    @SuppressWarnings("unchecked")
    public Encoded encode(Object value) {
        if (value instanceof RawValue raw) {
            return new Encoded(raw.type(), raw.bytes());
        }
        Registration registration = registration(value.getClass());
        if (registration != null && registration.codec() == null) {
            return null;
        }
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
        try {
            ((VariableCodec<Object>) registration.codec()).encode(value, out);
        } catch (UnsupportedOperationException unsavable) {
            // A value the codec can't save, as Skript can't: a particle with data Skript can't save.
            return null;
        }
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
            } catch (RuntimeException | LinkageError e) {
                // Keep the bytes as they are: a codec that fails, or one whose classes can't load (an addon's codec
                // for a plugin that isn't installed), loses nothing.
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
