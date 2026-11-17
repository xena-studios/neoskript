/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A config, declared once: a record type whose components are the settings, and an instance of it holding
 * the defaults. From this the default file is written, the live one read and checked, and old files carried
 * forward. As the config of Xena Studios' other plugins, written again here so NeoSkript releases on its own.
 *
 * <p>Settings can be {@code boolean}, {@code int}, {@code long}, {@code double}, {@code String}, enums, nested
 * records, {@code List}s of those scalars, and any type with a {@linkplain Builder#codec codec}.
 *
 * @param <C> the config's record type
 */
public final class ConfigSpec<C extends Record> {

    final Class<C> type;
    final C defaults;
    final int version;
    final int unversioned;
    final List<String> currentKeys;
    final List<String> header;
    final TreeMap<Integer, Migration> migrations;
    final Map<Class<?>, Codec<?>> codecs;

    private ConfigSpec(Builder<C> builder) {
        this.type = builder.type;
        this.defaults = builder.defaults;
        this.version = builder.version;
        this.unversioned = builder.unversioned;
        this.currentKeys = List.copyOf(builder.currentKeys);
        this.header = List.copyOf(builder.header);
        this.migrations = new TreeMap<>(builder.migrations);
        this.codecs = Map.copyOf(builder.codecs);
        check(type, type.getSimpleName());
        for (int from = 1; from < version; from++) {
            if (!migrations.containsKey(from)) {
                throw new IllegalStateException("no migration from config version " + from);
            }
        }
    }

    public static <C extends Record> Builder<C> builder(Class<C> type, C defaults) {
        return new Builder<>(type, defaults);
    }

    public C defaults() {
        return defaults;
    }

    public int version() {
        return version;
    }

    @SuppressWarnings("unchecked")
    <T> Codec<T> codec(Class<T> type) {
        return (Codec<T>) codecs.get(type);
    }

    // Fails at startup (and in unit tests) on a setting type the config can't read.
    private void check(Type type, String path) {
        if (type instanceof Class<?> c) {
            if (c.isRecord() && !codecs.containsKey(c)) {
                for (RecordComponent component : c.getRecordComponents()) {
                    check(component.getGenericType(), path + "." + Names.key(component));
                }
            } else if (!isScalar(c)) {
                throw new IllegalStateException(path + ": the config can't hold a " + c.getName());
            }
        } else if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            Type element = p.getActualTypeArguments()[0];
            if (!(element instanceof Class<?> c && isScalar(c))) {
                throw new IllegalStateException(path + ": lists can only hold scalars, not " + element);
            }
        } else {
            throw new IllegalStateException(path + ": the config can't hold a " + type);
        }
    }

    boolean isScalar(Class<?> c) {
        return c == boolean.class || c == int.class || c == long.class || c == double.class || c == String.class
                || c.isEnum() || codecs.containsKey(c);
    }

    /** Whether settings of this type are a section of settings rather than one value. */
    boolean isSection(Class<?> c) {
        return c.isRecord() && !codecs.containsKey(c);
    }

    public static final class Builder<C extends Record> {

        private final Class<C> type;
        private final C defaults;
        private int version = 1;
        private int unversioned;
        private final List<String> currentKeys = new ArrayList<>();
        private final List<String> header = new ArrayList<>();
        private final Map<Integer, Migration> migrations = new HashMap<>();
        private final Map<Class<?>, Codec<?>> codecs = new HashMap<>();

        private Builder(Class<C> type, C defaults) {
            this.type = Objects.requireNonNull(type);
            this.defaults = Objects.requireNonNull(defaults);
        }

        /** Lines written at the top of the default config. */
        public Builder<C> header(String... lines) {
            header.addAll(List.of(lines));
            return this;
        }

        /** The current config version. Each older version needs a {@link #migrate migration}. */
        public Builder<C> version(int version) {
            this.version = version;
            return this;
        }

        /**
         * The version a file without {@code config-version} is, such as one written before the config had
         * versions. Without this, such a file is taken as current.
         */
        public Builder<C> unversioned(int version) {
            this.unversioned = version;
            return this;
        }

        /**
         * Keys only the current version has, such as a new section. A file without {@code config-version} that has
         * one is taken as current, not as the {@linkplain #unversioned unversioned} version, so it isn't carried
         * forward again.
         */
        public Builder<C> currentWith(String... keys) {
            currentKeys.addAll(List.of(keys));
            return this;
        }

        /** How to carry a config from {@code fromVersion} to the next version. */
        public Builder<C> migrate(int fromVersion, Migration migration) {
            migrations.put(fromVersion, migration);
            return this;
        }

        public <T> Builder<C> codec(Class<T> type, Codec<T> codec) {
            codecs.put(type, codec);
            return this;
        }

        public ConfigSpec<C> build() {
            return new ConfigSpec<>(this);
        }
    }
}
