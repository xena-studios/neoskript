/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

/**
 * An addon's syntax, as it registers it, kept in order by kind until NeoSkript takes it, on the first tick
 * ({@link CompatAddon#registerInto}).
 */
public final class CompatSyntaxRegistry implements SyntaxRegistry {

    /** A key: its name, and for a child key, its parent's. */
    public record Named<I extends SyntaxInfo<?>>(String name, Key<SyntaxInfo<?>> parentKey)
            implements Key<I>, ChildKey<I, SyntaxInfo<?>> {

        @Override
        @SuppressWarnings("unchecked")
        public Key<SyntaxInfo<?>> parent() {
            return parentKey;
        }

        /** The kind at the top: an effect's is a statement. */
        public String root() {
            return name;
        }
    }

    private final CompatAddon addon;
    private final Map<Key<?>, List<SyntaxInfo<?>>> syntaxes = new LinkedHashMap<>();
    /** Each info, with its key, in the order registered. */
    private final List<Map.Entry<Key<?>, SyntaxInfo<?>>> order = new ArrayList<>();

    public CompatSyntaxRegistry(CompatAddon addon) {
        this.addon = addon;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <I extends SyntaxInfo<?>> Collection<I> syntaxes(Key<I> key) {
        return (Collection<I>) List.copyOf(syntaxes.getOrDefault(key, List.of()));
    }

    @Override
    public synchronized <I extends SyntaxInfo<?>> void register(Key<I> key, I info) {
        syntaxes.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        order.add(Map.entry(key, info));
        if (addon != null) {
            addon.registered();
        }
    }

    @Override
    public synchronized void unregister(SyntaxInfo<?> info) {
        syntaxes.values().forEach(list -> list.remove(info));
        order.removeIf(entry -> entry.getValue() == info);
    }

    @Override
    public synchronized <I extends SyntaxInfo<?>> void unregister(Key<I> key, I info) {
        List<SyntaxInfo<?>> list = syntaxes.get(key);
        if (list != null) {
            list.remove(info);
        }
        order.removeIf(entry -> entry.getKey().equals(key) && entry.getValue() == info);
    }

    @Override
    public synchronized Collection<SyntaxInfo<?>> elements() {
        return order.stream().<SyntaxInfo<?>>map(Map.Entry::getValue).toList();
    }

    /** Everything registered, with its key, in order. */
    public synchronized List<Map.Entry<Key<?>, SyntaxInfo<?>>> ordered() {
        return List.copyOf(order);
    }
}
