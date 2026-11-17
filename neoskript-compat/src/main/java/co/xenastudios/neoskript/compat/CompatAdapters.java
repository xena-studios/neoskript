/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.List;
import java.util.function.Function;

/**
 * Skript's value classes that NeoSkript has its own of (D24): an addon gets Skript's, holding NeoSkript's value, and a
 * script gets NeoSkript's back.
 */
final class CompatAdapters {

    /** One of Skript's value classes and NeoSkript's for the same values. */
    record Adapter(Class<?> skript, Class<?> neoskript, Function<Object, Object> toSkript,
            Function<Object, Object> toNeoSkript) {}

    private static final List<Adapter> ADAPTERS = List.of(
            new Adapter(ch.njol.skript.aliases.ItemType.class, co.xenastudios.neoskript.paper.ItemType.class,
                    value -> ch.njol.skript.aliases.ItemType.of((co.xenastudios.neoskript.paper.ItemType) value),
                    value -> ((ch.njol.skript.aliases.ItemType) value).neoskript()),
            new Adapter(ch.njol.skript.entity.EntityData.class, co.xenastudios.neoskript.paper.EntityKind.class,
                    value -> ch.njol.skript.entity.EntityData.of((co.xenastudios.neoskript.paper.EntityKind) value),
                    value -> ((ch.njol.skript.entity.EntityData<?>) value).neoskript()));

    private CompatAdapters() {}

    /** The class an addon sees for values of NeoSkript's {@code type}. */
    static Class<?> skriptClass(Class<?> type) {
        for (Adapter adapter : ADAPTERS) {
            if (adapter.neoskript() == type) {
                return adapter.skript();
            }
        }
        return type;
    }

    /** NeoSkript's class for values of Skript's {@code type}. */
    static Class<?> neoskriptClass(Class<?> type) {
        for (Adapter adapter : ADAPTERS) {
            if (adapter.skript() == type) {
                return adapter.neoskript();
            }
        }
        return type;
    }

    /** {@code value} as the addon's {@code type}, if it's NeoSkript's value of it; else {@code value}. */
    static Object toSkript(Object value, Class<?> type) {
        for (Adapter adapter : ADAPTERS) {
            if (type.isAssignableFrom(adapter.skript()) && adapter.neoskript().isInstance(value)) {
                return adapter.toSkript().apply(value);
            }
        }
        return value;
    }

    /** An addon's value as NeoSkript's. */
    static Object toNeoSkript(Object value) {
        for (Adapter adapter : ADAPTERS) {
            if (adapter.skript().isInstance(value)) {
                return adapter.toNeoSkript().apply(value);
            }
        }
        return value;
    }
}
