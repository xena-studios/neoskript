/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Skript's {@code item type}: an item, with an amount if one was given, as scripts write it:
 * {@code 32 cooked beef}, {@code diamond sword of sharpness 2}. Syntax like {@code named} makes a new item;
 * changing an item in a variable ({@code set persistent data tag "x" of {_item}}) changes it in place,
 * which is safe because variables store copies.
 */
public final class ItemType {

    private static final Pattern AMOUNT = Pattern.compile("(\\d+)\\s+(?:of\\s+)?(.+)");
    private static final Pattern ENCHANTMENT = Pattern.compile("(.+?)(?:\\s+(\\d+|[ivx]+))?");
    private static final Pattern ENCHANTMENT_SEPARATOR = Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");
    private static final String[] ROMAN = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};

    /** Every item's names, singular and plural, built the first time an item is written. */
    private static volatile Map<String, Material> aliases;

    private final ItemStack stack;
    private final boolean amountGiven;

    public ItemType(ItemStack stack, boolean amountGiven) {
        this.stack = stack.clone();
        this.amountGiven = amountGiven;
    }

    /** A copy of the item, to give or put somewhere. */
    public ItemStack stack() {
        return stack.clone();
    }

    public Material material() {
        return stack.getType();
    }

    public int amount() {
        return stack.getAmount();
    }

    /** Whether the item was written with an amount, like {@code 32 cooked beef}. */
    public boolean amountGiven() {
        return amountGiven;
    }

    /** Changes its meta in place, as when a variable's item is changed. */
    public void editMeta(Consumer<ItemMeta> change) {
        stack.editMeta(change);
    }

    /** A copy, as stored in a variable. */
    public ItemType copy() {
        return new ItemType(stack, amountGiven);
    }

    /** A copy with its meta changed by {@code change}. */
    public ItemType withMeta(Consumer<ItemMeta> change) {
        ItemStack copy = stack.clone();
        copy.editMeta(change);
        return new ItemType(copy, amountGiven);
    }

    /**
     * Parses an item as scripts write it, or returns {@code null}: {@code [<amount> [of]] [a|an|all|every]
     * <alias> [of <enchantment> [<level>] [, <more>...]]}.
     */
    public static ItemType parse(String text) {
        String words = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int amount = 1;
        boolean amountGiven = false;
        Matcher counted = AMOUNT.matcher(words);
        if (counted.matches()) {
            try {
                amount = Integer.parseInt(counted.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
            amountGiven = true;
            words = counted.group(2);
        }
        words = words.replaceFirst("^(?:an?|all|every) ", "");
        Material material = alias(words);
        Map<Enchantment, Integer> enchantments = Map.of();
        if (material == null) {
            // 'diamond sword of sharpness 2': the alias is before an ' of ', and enchantments after it.
            int of = words.indexOf(" of ");
            while (material == null && of >= 0) {
                material = alias(words.substring(0, of));
                if (material != null) {
                    enchantments = enchantments(words.substring(of + 4));
                    if (enchantments == null) {
                        return null;
                    }
                }
                of = words.indexOf(" of ", of + 1);
            }
        }
        if (material == null || amount < 0) {
            return null;
        }
        ItemStack stack = new ItemStack(material, Math.max(amount, 1));
        if (!enchantments.isEmpty()) {
            Map<Enchantment, Integer> chosen = enchantments;
            stack.editMeta(meta -> chosen.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true)));
        }
        return new ItemType(stack, amountGiven);
    }

    private static Map<Enchantment, Integer> enchantments(String text) {
        Map<Enchantment, Integer> enchantments = new java.util.LinkedHashMap<>();
        for (String part : ENCHANTMENT_SEPARATOR.split(text)) {
            Matcher matcher = ENCHANTMENT.matcher(part.strip());
            if (!matcher.matches()) {
                return null;
            }
            Enchantment enchantment = enchantment(matcher.group(1));
            if (enchantment == null) {
                return null;
            }
            enchantments.put(enchantment, level(matcher.group(2)));
        }
        return enchantments;
    }

    private static Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.strip().replace(' ', '_'));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    private static int level(String text) {
        if (text == null) {
            return 1;
        }
        int roman = List.of(ROMAN).indexOf(text);
        return roman >= 0 ? roman + 1 : Integer.parseInt(text);
    }

    /** The item called {@code name}, singular or plural, or {@code null}. */
    static Material alias(String name) {
        Map<String, Material> known = aliases;
        if (known == null) {
            known = new HashMap<>();
            for (Material material : Material.values()) {
                // Air counts, as in Skript ('set block at X to air'), but not the other airs.
                if (material.isLegacy() || !material.isItem() && material != Material.AIR
                        || material.isAir() && material != Material.AIR) {
                    continue;
                }
                String singular = name(material);
                known.putIfAbsent(singular, material);
                known.putIfAbsent(plural(singular), material);
                // Items named in the plural, like stone bricks, can be written in the singular too.
                if (singular.endsWith("s") && singular.length() > 3) {
                    known.putIfAbsent(singular.substring(0, singular.length() - 1), material);
                }
            }
            aliases = known;
        }
        return known.get(name);
    }

    /** How an item reads: its material's name in lower case, with spaces. */
    static String name(Material material) {
        return material.getKey().getKey().replace('_', ' ');
    }

    private static String plural(String name) {
        if (name.endsWith("s")) {
            // Already plural, or a mass noun: oak planks, glass.
            return name;
        }
        if (name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    /** As Skript writes items: {@code 32 cooked beef}, {@code diamond sword of sharpness 2 named "Blade"}. */
    static String text(ItemStack stack, boolean withAmount) {
        StringBuilder text = new StringBuilder();
        String name = name(stack.getType());
        if (withAmount && stack.getAmount() != 1) {
            text.append(stack.getAmount()).append(' ').append(plural(name));
        } else {
            text.append(name);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> enchantments = new ArrayList<>();
            meta.getEnchants().forEach((enchantment, level) ->
                    enchantments.add(enchantment.getKey().getKey().replace('_', ' ') + " " + level));
            if (!enchantments.isEmpty()) {
                text.append(" of ").append(String.join(", ", enchantments));
            }
            Component display = meta.displayName();
            if (display != null) {
                text.append(" named \"").append(Messages.LEGACY_SECTION.serialize(display)).append('"');
            }
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return text(stack, amountGiven);
    }

    /** Items are equal if they're the same item with the same amount, name, enchantments and so on. */
    @Override
    public boolean equals(Object other) {
        return other instanceof ItemType item && item.stack.isSimilar(stack) && item.stack.getAmount() == stack.getAmount();
    }

    @Override
    public int hashCode() {
        return stack.getType().hashCode() * 31 + stack.getAmount();
    }
}
