/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The aliases alias files define, as {@link AliasLoader} read them: what each name stands for, and what each item is
 * called. Never changed once loaded; a reload makes a new one.
 */
final class AliasTable {

    /** An alias: the items it stands for. Names that share one (singular and plural) share it. */
    static final class Type {
        /** The name it was first defined with, as written. */
        final String name;
        final List<Item> items = new ArrayList<>();

        Type(String name) {
            this.name = name;
        }
    }

    /**
     * One item an alias stands for: Skript's {@code ItemData}. {@code stack} and {@code blockData} are what the server
     * made (an item stack, block data), or {@code null} for a material that isn't an item or a block. {@code itemKey}
     * and {@code blockKey} tell two items apart.
     *
     * @param id the id as the file wrote it, in lower case ({@code cod} or {@code minecraft:cod})
     * @param material the material's key, like {@code minecraft:cod}
     * @param components the components applied to the item, or {@code null}
     * @param states the block states the file gave, which also decide {@code blockData}
     * @param relatedEntity the entity a {@code [relatedEntity=...]} relates the item to, or {@code null}
     */
    record Item(String id, String material, String components, Object stack, String itemKey, Object blockData,
            String blockKey, Map<String, String> states, String relatedEntity) {

        /** Skript's {@code ItemData.isDefault}: nothing applied to the item, and not a block. */
        boolean isDefault() {
            return components == null && blockKey == null;
        }

        /** Skript's exact match: the same item, with the same components and block data. */
        boolean sameAs(Item other) {
            return material.equals(other.material) && itemKey.equals(other.itemKey)
                    && Objects.equals(blockKey, other.blockKey);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    /** What an item is called: Skript's {@code MaterialName}. */
    record Named(Item item, String singular, String plural) {}

    /**
     * The names a material's items have: the plain item's (the last one named, as in Skript), and the others' in the
     * order they were named.
     */
    static final class Names {
        Named defaultItem;
        final List<Named> items = new ArrayList<>();
    }

    static final AliasTable EMPTY = new AliasTable(Map.of(), Map.of(), Set.of(), Set.of(), 0);

    private final Map<String, Type> aliases;
    private final Map<String, Names> names;
    private final Set<String> covered;
    private final Set<String> merged;
    private final int count;

    AliasTable(Map<String, Type> aliases, Map<String, Names> names, Set<String> covered, Set<String> merged, int count) {
        this.aliases = Collections.unmodifiableMap(aliases);
        this.names = Collections.unmodifiableMap(names);
        this.covered = Collections.unmodifiableSet(covered);
        this.merged = Collections.unmodifiableSet(merged);
        this.count = count;
    }

    /** The alias called {@code name} (in lower case), or {@code null}. */
    Type get(String name) {
        return aliases.get(name);
    }

    /** The names of a material's items, by its key, or {@code null} if no alias names it. */
    Names names(String material) {
        return names.get(material);
    }

    /**
     * Whether NeoSkript's own names for a material ({@code red dye} for red dye) are left out, as Skript leaves them
     * out: an alias file names the material, or its name was already an alias's, which took the item.
     */
    boolean namedByFiles(String material) {
        return covered.contains(material) || merged.contains(material);
    }

    /** How many names the files define. */
    int count() {
        return count;
    }

    /** Every name, in lower case. */
    Set<String> names() {
        return aliases.keySet();
    }

    /** The materials whose own names are left out (see {@link #namedByFiles}). */
    Set<String> namedMaterials() {
        Set<String> all = new HashSet<>(covered);
        all.addAll(merged);
        return all;
    }

    /** Whether {@code name} stands for the same items in both tables. */
    static boolean same(Type a, Type b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.items.size() != b.items.size()) {
            return false;
        }
        for (int i = 0; i < a.items.size(); i++) {
            if (!a.items.get(i).sameAs(b.items.get(i))) {
                return false;
            }
        }
        return true;
    }
}
