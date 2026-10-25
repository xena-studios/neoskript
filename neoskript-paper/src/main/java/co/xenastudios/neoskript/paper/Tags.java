/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/** Minecraft's tags of items, blocks and entity types, and scripts' own: {@code item tag "slabs"}. */
final class Tags {

    private Tags() {}

    /** The kinds of tag, as Bukkit names their registries, with the class of what they tag. */
    private static final List<String> KINDS = List.of("items", "blocks", "entity_types");

    /** Tags scripts have registered, under {@code skript:}. */
    private static final Map<String, Tag> CUSTOM = new ConcurrentHashMap<>();

    /**
     * A tag: what kind of thing it tags, its key, and what it holds.
     *
     * @param values the materials or entity types in it
     */
    record Tag(String kind, NamespacedKey key, Set<? extends Keyed> values) {
        boolean contains(Object value) {
            Keyed keyed = switch (value) {
                case ItemType item when !kind.equals("entity_types") -> item.material();
                case Block block when !kind.equals("entity_types") -> block.getType();
                case BlockData data when !kind.equals("entity_types") -> data.getMaterial();
                case Entity entity when kind.equals("entity_types") -> entity.getType();
                case EntityKind entityKind when kind.equals("entity_types") -> entityKind.type();
                case null, default -> null;
            };
            return keyed != null && values.contains(keyed);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Tag tag && tag.kind.equals(kind) && tag.key.equals(key);
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + key.hashCode();
        }

        @Override
        public String toString() {
            return (kind.equals("entity_types") ? "entity" : kind.substring(0, kind.length() - 1)) + " tag \"" + key + "\"";
        }
    }

    static void register(Registrar registrar) {
        registrar.type("minecrafttag", "minecraft tag", Tag.class);
        String origins = "[minecraft:minecraft|datapack:datapack|paper:paper|custom:(custom|skript)] ";
        String kinds = "[item:item|block:block|entity:entity [type]] ";
        registrar.expression("ExprTag", "minecrafttag", info -> {
            List<String> kindsWanted = kinds(info);
            String origin = origin(info);
            return (context, args) -> {
                List<Object> found = new ArrayList<>();
                for (Object name : (Object[]) args[0]) {
                    Tag tag = find(Values.text(name), kindsWanted, origin);
                    if (tag != null) {
                        found.add(tag);
                    }
                }
                return found.size() == 1 ? found.getFirst() : found.toArray();
            };
        }, "[the] " + origins + kinds + "tag[s] %strings%");
        registrar.condition("CondIsTagged", info -> {
            boolean negated = info.pattern() == 1;
            boolean valuesAnd = info.and(0);
            boolean tagsAnd = info.and(1);
            return (context, args) -> negated != Condition.check((Object[]) args[0], valuesAnd, value ->
                    Condition.check((Object[]) args[1], tagsAnd, tag -> tag instanceof Tag t && t.contains(value)));
        }, "%itemtypes/blocks/blockdatas/entities/entitytypes% (is|are) tagged (as|with) %minecrafttags%",
                "%itemtypes/blocks/blockdatas/entities/entitytypes% (isn't|is not|aren't|are not) tagged (as|with) %minecrafttags%");
        registrar.expression("ExprTagContents", "object", info -> (context, args) -> {
            List<Object> contents = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Tag tag) {
                    for (Keyed keyed : tag.values()) {
                        contents.add(keyed instanceof Material material ? new ItemType(new org.bukkit.inventory.ItemStack(
                                material.isItem() ? material : Material.AIR), false) : EntityKind.of((EntityType) keyed));
                    }
                }
            }
            return contents.toArray();
        }, "[the] tag (contents|values) of %minecrafttags%", "%minecrafttags%'[s] tag (contents|values)");
        // 'the key of {_tag}': its namespaced key, "minecraft:dirt".
        registrar.property("ExprTagKey", "string", "[namespace[d]] key[s]", "minecrafttags", Tag.class,
                tag -> tag.key().toString());
        registrar.expression("ExprTagsOf", "minecrafttag", info -> {
            List<String> kindsWanted = kinds(info);
            String origin = origin(info);
            return (context, args) -> {
                Set<Object> found = new LinkedHashSet<>();
                for (Tag tag : all(kindsWanted, origin)) {
                    for (Object value : (Object[]) args[0]) {
                        if (tag.contains(value)) {
                            found.add(tag);
                        }
                    }
                }
                return found.toArray();
            };
        }, "[all [[of] the]|the] " + origins + kinds + "tags of %itemtypes/blocks/blockdatas/entities/entitytypes%",
                "%itemtypes/blocks/blockdatas/entities/entitytypes%'[s] " + origins + kinds + "tags");
        registrar.expression("ExprTagsOfType", "minecrafttag", info -> {
            List<String> kindsWanted = kinds(info);
            String origin = origin(info);
            return (context, args) -> all(kindsWanted, origin).toArray();
        }, "[all [[of] the]|the] " + origins + kinds + "tags");
        registrar.effect("EffRegisterTag", info -> {
            String kind = info.hasTag("block") ? "blocks" : info.hasTag("entity") ? "entity_types" : "items";
            return (context, args) -> {
                String name = Values.text(args[0]).toLowerCase(Locale.ROOT);
                Set<Keyed> values = new LinkedHashSet<>();
                for (Object value : (Object[]) args[1]) {
                    switch (value) {
                        case ItemType item -> values.add(item.material());
                        case EntityKind entity when entity.type() != null -> values.add(entity.type());
                        default -> {}
                    }
                }
                NamespacedKey key = new NamespacedKey("skript", name.startsWith("skript:") ? name.substring(7) : name);
                CUSTOM.put(kind + "/" + key.getKey(), new Tag(kind, key, values));
            };
        }, "register [a[n]] [custom] (item|block:block|entity:entity [type]) tag named %text% (using|containing) %itemtypes/entitytypes%");
    }

    private static List<String> kinds(co.xenastudios.neoskript.api.element.ElementInfo info) {
        return info.hasTag("item") ? List.of("items") : info.hasTag("block") ? List.of("blocks")
                : info.hasTag("entity") ? List.of("entity_types") : KINDS;
    }

    private static String origin(co.xenastudios.neoskript.api.element.ElementInfo info) {
        return info.hasTag("minecraft") ? "minecraft" : info.hasTag("paper") ? "paper" : info.hasTag("custom") ? "skript"
                : info.hasTag("datapack") ? "datapack" : null;
    }

    /** The tag {@code name} names: with its namespace, or found in Minecraft's, then Paper's, then scripts'. */
    private static Tag find(String name, List<String> kinds, String origin) {
        String text = name.strip().toLowerCase(Locale.ROOT);
        NamespacedKey given = NamespacedKey.fromString(text);
        if (given == null) {
            return null;
        }
        boolean namespaced = text.contains(":");
        List<String> namespaces = namespaced ? List.of(given.getNamespace())
                : origin != null ? List.of(origin) : List.of("minecraft", "paper", "skript");
        for (String namespace : namespaces) {
            for (String kind : kinds) {
                Tag tag = lookup(kind, new NamespacedKey(namespace, given.getKey()));
                if (tag != null) {
                    return tag;
                }
            }
        }
        return null;
    }

    /** Paper's own tags ({@code paper:helmets}), which aren't in Bukkit's tag registry, found once when needed. */
    private static volatile List<Tag> paperTags;

    private static List<Tag> paperTags() {
        List<Tag> known = paperTags;
        if (known == null) {
            known = new ArrayList<>();
            for (String holder : List.of("com.destroystokyo.paper.MaterialTags", "io.papermc.paper.tag.EntityTags")) {
                try {
                    for (java.lang.reflect.Field field : Class.forName(holder).getFields()) {
                        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                                && field.get(null) instanceof org.bukkit.Tag<?> tag) {
                            String kind = holder.endsWith("EntityTags") ? "entity_types" : "items";
                            // Paper keys them 'paper:helmets_settag'; scripts call them 'paper:helmets'.
                            NamespacedKey key = new NamespacedKey("paper", tag.getKey().getKey().replaceFirst("_settag$", ""));
                            known.add(new Tag(kind, key, tag.getValues()));
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError absent) {
                    // Not on this server.
                }
            }
            paperTags = known;
        }
        return known;
    }

    private static Tag lookup(String kind, NamespacedKey key) {
        if (key.getNamespace().equals("skript")) {
            return CUSTOM.get(kind + "/" + key.getKey());
        }
        if (key.getNamespace().equals("paper")) {
            String wanted = kind.equals("blocks") ? "items" : kind;
            return paperTags().stream().filter(tag -> tag.kind().equals(wanted) && tag.key().equals(key)).findFirst()
                    .orElse(null);
        }
        org.bukkit.Tag<? extends Keyed> tag = kind.equals("entity_types") ? Bukkit.getTag(kind, key, EntityType.class)
                : Bukkit.getTag(kind, key, Material.class);
        return tag == null ? null : new Tag(kind, key, tag.getValues());
    }

    /** Every tag of the kinds, from the origin (or every origin). */
    private static List<Tag> all(List<String> kinds, String origin) {
        List<Tag> tags = new ArrayList<>();
        for (String kind : kinds) {
            Iterable<? extends org.bukkit.Tag<? extends Keyed>> found = kind.equals("entity_types")
                    ? Bukkit.getTags(kind, EntityType.class) : Bukkit.getTags(kind, Material.class);
            for (org.bukkit.Tag<? extends Keyed> tag : found) {
                if (origin == null || tag.getKey().getNamespace().equals(origin)
                        || origin.equals("datapack") && !tag.getKey().getNamespace().equals("minecraft")
                        && !tag.getKey().getNamespace().equals("paper")) {
                    tags.add(new Tag(kind, tag.getKey(), tag.getValues()));
                }
            }
            if (origin == null || origin.equals("paper")) {
                paperTags().stream().filter(tag -> tag.kind().equals(kind)).forEach(tags::add);
            }
            if (origin == null || origin.equals("skript")) {
                CUSTOM.values().stream().filter(tag -> tag.kind().equals(kind)).forEach(tags::add);
            }
        }
        return tags;
    }
}
