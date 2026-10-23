/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * How items and game modes are saved, as Skript saves them. Item metadata, and item stacks, are YAML
 * written by Bukkit's own serializer, as in Skript.
 *
 * <p>An item type is a list of item datas; NeoSkript's are always one item, not "any sword". Item types it
 * can't write back exactly stay as Skript saved them (the storage only decodes values that re-encode to
 * the same bytes).
 */
final class ItemCodecs {

    /** Skript's item flag for an item whose metadata was changed (a name, lore, enchantments). */
    private static final int CHANGED_META = 2;

    private ItemCodecs() {}

    static void register(Registrar registrar) {
        registrar.storage("gamemode", GameMode.class, new VariableCodec<>() {
            @Override
            public void encode(GameMode mode, BinaryWriter out) {
                out.rawName(mode.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public GameMode decode(BinaryReader in) {
                return GameMode.valueOf(in.rawName().toUpperCase(Locale.ROOT));
            }
        });

        registrar.storage("itemstack", ItemStack.class, new VariableCodec<>() {
            @Override
            public void encode(ItemStack stack, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("value", yaml(stack));
            }

            @Override
            public ItemStack decode(BinaryReader in) {
                in.beginObject();
                Object value = load(in.stringField("value"));
                return value instanceof ItemStack stack ? stack : null;
            }
        });

        registrar.storage("itemtype", ItemType.class, new VariableCodec<>() {
            @Override
            public void encode(ItemType item, BinaryWriter out) {
                ItemStack stack = item.stack();
                boolean changed = stack.hasItemMeta();
                out.beginObject(6);
                out.booleanField("all", false);
                out.objectField("types", "ArrayList");
                out.beginObject(1);
                out.objectArrayField("values", "Object", 1);
                out.arrayElement("NewItemData");
                out.beginObject(8);
                out.booleanField("itemform", false);
                out.booleanField("isalias", !changed);
                out.objectField("meta", "ConfigurationSerializable_ItemMeta");
                out.beginObject(1);
                out.stringField("value", yaml(stack.getItemMeta()));
                out.booleanField("isanything", false);
                if (stack.getType().isBlock()) {
                    out.objectField("blockvalues", "NewBlockValues");
                    out.beginObject(2);
                    out.stringField("data", stack.getType().createBlockData().getAsString());
                    out.booleanField("isdefault", true);
                } else {
                    out.nullField("blockvalues");
                }
                out.booleanField("plain", false);
                out.intField("itemflags", changed ? CHANGED_META : 0);
                out.stringField("key", stack.getType().getKey().toString());
                out.intField("amount", item.amountGiven() ? stack.getAmount() : -1);
                out.nullField("item");
                out.nullField("globalmeta");
                out.nullField("block");
            }

            @Override
            public ItemType decode(BinaryReader in) {
                in.beginObject();
                if (in.booleanField("all") || !in.objectField("types").equals("ArrayList")) {
                    return null;
                }
                in.beginObject();
                if (in.objectArrayField("values", "Object") != 1 || !in.arrayElement().equals("NewItemData")) {
                    return null;
                }
                in.beginObject();
                in.booleanField("itemform");
                in.booleanField("isalias");
                if (!in.objectField("meta").equals("ConfigurationSerializable_ItemMeta")) {
                    return null;
                }
                in.beginObject();
                Object meta = load(in.stringField("value"));
                in.booleanField("isanything");
                if (in.objectOrNullField("blockvalues") != null) {
                    in.beginObject();
                    in.stringField("data");
                    in.booleanField("isdefault");
                }
                in.booleanField("plain");
                in.intField("itemflags");
                NamespacedKey key = NamespacedKey.fromString(in.stringField("key"));
                int amount = in.intField("amount");
                for (String field : new String[] {"item", "globalmeta", "block"}) {
                    if (in.objectOrNullField(field) != null) {
                        return null;
                    }
                }
                Material material = key == null ? null : Registry.MATERIAL.get(key);
                if (material == null || !(meta instanceof ItemMeta itemMeta)) {
                    return null;
                }
                ItemStack stack = new ItemStack(material, Math.max(amount, 1));
                stack.setItemMeta(itemMeta);
                return new ItemType(stack, amount >= 0);
            }
        });
    }

    /** {@code value} as Bukkit saves it in YAML, under the key {@code value}, as Skript does. */
    private static String yaml(Object value) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("value", value);
        return yaml.saveToString();
    }

    private static Object load(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return yaml.get("value");
    }
}
