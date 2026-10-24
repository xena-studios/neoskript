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

    private ItemStack stack;
    private boolean amountGiven;
    /**
     * The items it may be, as for an alias like {@code any boots = diamond boots, iron boots}; {@code null}
     * when it's one item. The first is also {@link #stack}.
     */
    private List<ItemType> alternatives;

    public ItemType(ItemStack stack, boolean amountGiven) {
        this.stack = stack.clone();
        this.amountGiven = amountGiven;
    }

    /** An item that may be any of {@code items}, as an alias of several stands for. */
    static ItemType anyOf(List<ItemType> items) {
        List<ItemType> all = new ArrayList<>();
        for (ItemType item : items) {
            for (ItemType each : item.alternatives()) {
                all.add(each.copy());
            }
        }
        ItemType type = new ItemType(all.getFirst().stack, all.getFirst().amountGiven);
        if (all.size() > 1) {
            type.alternatives = all;
        }
        return type;
    }

    /** The items it may be: itself, unless it stands for several. */
    public List<ItemType> alternatives() {
        return alternatives == null ? List.of(this) : java.util.Collections.unmodifiableList(alternatives);
    }

    /**
     * A copy of the item, to give or put somewhere. One standing for several gives one of them at random,
     * as Skript does.
     */
    public ItemStack stack() {
        if (alternatives != null) {
            return alternatives.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(alternatives.size())).stack();
        }
        return stack.clone();
    }

    public Material material() {
        return stack.getType();
    }

    public int amount() {
        return stack.getAmount();
    }

    /** Whether it was written {@code plain}: only items with nothing more to them than their kind are it. */
    private boolean plain;

    public boolean plain() {
        return plain;
    }

    private void markPlain() {
        plain = true;
        if (alternatives != null) {
            alternatives.forEach(ItemType::markPlain);
        }
    }

    /** Replaces its item in place, keeping whether an amount was given. */
    void replace(ItemStack replacement) {
        stack = replacement.clone();
    }

    /** Changes its item in place, each of them if it stands for several. */
    void editStacks(Consumer<ItemStack> change) {
        ItemStack copy = stack.clone();
        change.accept(copy);
        stack = copy;
        if (alternatives != null) {
            alternatives.forEach(each -> each.editStacks(change));
        }
    }

    /** Changes its amount in place, as when a variable's item is changed. */
    public void setAmount(int amount) {
        stack.setAmount(Math.max(0, amount));
        amountGiven = true;
        if (alternatives != null) {
            alternatives.forEach(each -> each.setAmount(amount));
        }
    }

    /** Whether the item was written with an amount, like {@code 32 cooked beef}. */
    public boolean amountGiven() {
        return amountGiven;
    }

    /** Changes its meta in place, as when a variable's item is changed. */
    public void editMeta(Consumer<ItemMeta> change) {
        stack.editMeta(change);
        if (alternatives != null) {
            alternatives.forEach(each -> each.editMeta(change));
        }
    }

    /** A copy, as stored in a variable. */
    public ItemType copy() {
        ItemType copy = new ItemType(stack, amountGiven);
        copy.plain = plain;
        if (alternatives != null) {
            copy.alternatives = new ArrayList<>(alternatives.stream().map(ItemType::copy).toList());
        }
        return copy;
    }

    /** A copy with its meta changed by {@code change}. */
    public ItemType withMeta(Consumer<ItemMeta> change) {
        ItemType copy = copy();
        copy.editMeta(change);
        return copy;
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
        // 'plain stone': stone with nothing else to it, which only items with nothing else to them are.
        if (words.startsWith("plain ")) {
            ItemType item = parse((amountGiven ? amount + " " : "") + words.substring(6));
            if (item == null) {
                return null;
            }
            item.markPlain();
            return item;
        }
        ItemType aliased = scriptAlias(words, amount, amountGiven);
        if (aliased != null) {
            return aliased;
        }
        // 'potion of swiftness', 'splash potion of water', 'plain potion of mundane': a potion of that kind.
        Matcher potion = POTION.matcher(words);
        if (potion.matches()) {
            org.bukkit.potion.PotionType kind = potionType(potion.group(3));
            if (kind == null) {
                return null;
            }
            Material bottle = potion.group(2) == null ? Material.POTION
                    : potion.group(2).startsWith("splash") ? Material.SPLASH_POTION : Material.LINGERING_POTION;
            ItemStack stack = new ItemStack(bottle, Math.max(amount, 1));
            stack.editMeta(org.bukkit.inventory.meta.PotionMeta.class, meta -> meta.setBasePotionType(kind));
            return new ItemType(stack, amountGiven);
        }
        Material material = alias(words);
        Map<Enchantment, Integer> enchantments = Map.of();
        if (material == null) {
            // 'diamond sword of sharpness 2': the alias is before an ' of ', and enchantments after it.
            int of = words.indexOf(" of ");
            while (material == null && of >= 0) {
                material = named(words.substring(0, of));
                if (material != null) {
                    enchantments = enchantments(words.substring(of + 4));
                    if (enchantments == null) {
                        return null;
                    }
                }
                of = words.indexOf(" of ", of + 1);
            }
        }
        org.bukkit.block.data.BlockData state = null;
        if (material == null) {
            // 'minecraft:birch_door[open=false]', 'oak_stairs': a Minecraft id, with block states.
            Matcher id = MINECRAFT_ID.matcher(words);
            if (id.matches()) {
                material = Material.matchMaterial(id.group(1));
                if (material != null && id.group(2) != null) {
                    if (!material.isBlock()) {
                        return null;
                    }
                    try {
                        state = org.bukkit.Bukkit.createBlockData(material, id.group(2));
                    } catch (IllegalArgumentException invalid) {
                        return null;
                    }
                }
            }
        }
        if (material == null || amount < 0) {
            return null;
        }
        ItemStack stack = new ItemStack(material, Math.max(amount, 1));
        // No states, as in 'oak_stairs[]', is any of the block.
        if (state != null && !words.endsWith("[]")) {
            org.bukkit.block.data.BlockData data = state;
            stack.editMeta(org.bukkit.inventory.meta.BlockDataMeta.class, meta -> meta.setBlockData(data));
        }
        if (!enchantments.isEmpty()) {
            Map<Enchantment, Integer> chosen = enchantments;
            stack.editMeta(meta -> chosen.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true)));
        }
        return new ItemType(stack, amountGiven);
    }

    /** A Minecraft id, namespaced or not, with block states if any: {@code minecraft:oak_stairs[half=top]}. */
    private static final Pattern MINECRAFT_ID = Pattern.compile("((?:[a-z0-9_.-]+:)?[a-z0-9_./-]+)(\\[[^\\]]*\\])?");

    /** Guards against aliases defined in terms of themselves. */
    private static final ThreadLocal<java.util.Set<String>> RESOLVING = ThreadLocal.withInitial(java.util.HashSet::new);

    /** The item the script being compiled calls {@code name} in its {@code aliases:}, or {@code null}. */
    private static ItemType scriptAlias(String name, int amount, boolean amountGiven) {
        String script = co.xenastudios.neoskript.runtime.ScriptFeatures.compilingScript();
        String definition = co.xenastudios.neoskript.runtime.ScriptAliases.definition(script, name);
        if (definition == null || !RESOLVING.get().add(name)) {
            return null;
        }
        try {
            List<ItemType> items = new ArrayList<>();
            for (String part : definitionParts(definition)) {
                ItemType item = parse(part);
                if (item == null) {
                    return null;
                }
                items.add(item);
            }
            if (items.isEmpty()) {
                return null;
            }
            ItemType type = anyOf(items);
            if (amountGiven) {
                type.setAmount(amount);
            } else {
                type.amountGiven = false;
            }
            return type;
        } finally {
            RESOLVING.get().remove(name);
        }
    }

    /** What an alias stands for, item by item: {@code diamond boots, iron boots and golden boots}. */
    static List<String> definitionParts(String definition) {
        List<String> parts = new ArrayList<>();
        for (String part : definition.split("\\s*,\\s*|\\s+(?:and|or)\\s+")) {
            if (!part.isBlank()) {
                parts.add(part.strip());
            }
        }
        return parts;
    }

    /** Skript's error for an alias standing for {@code definition}, or {@code null} if each item is one. */
    static String checkDefinition(String definition) {
        for (String part : definitionParts(definition)) {
            if (parse(part) == null) {
                return "Minecraft id " + part + " is not valid";
            }
        }
        return null;
    }

    private static final Pattern POTION = Pattern.compile("(plain )?(splash |lingering )?potion of (.+)");

    /** The kind of potion {@code name} names: {@code swiftness}, {@code long swiftness}, {@code water}. */
    private static org.bukkit.potion.PotionType potionType(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.strip().replace(' ', '_'));
        return key == null ? null : org.bukkit.Registry.POTION.get(key);
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

    /** An item's name or its Minecraft id ({@code minecraft:diamond_axe}, {@code oak_stairs}), or {@code null}. */
    private static Material named(String name) {
        Material material = alias(name);
        if (material == null && MINECRAFT_ID.matcher(name).matches() && !name.contains("[")) {
            material = Material.matchMaterial(name);
        }
        return material;
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
        Material found = known.get(name);
        // 'dirt block' is dirt, as Skript names blocks.
        if (found == null && name.endsWith(" block")) {
            Material block = known.get(name.substring(0, name.length() - 6));
            if (block != null && block.isBlock()) {
                return block;
            }
        }
        return found;
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
        if (alternatives != null) {
            List<String> names = alternatives.stream().map(ItemType::toString).toList();
            return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
        }
        return text(stack, amountGiven);
    }

    /** Items are equal if they're the same item with the same amount, name, enchantments and so on. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ItemType item) || (item.alternatives == null) != (alternatives == null)) {
            return false;
        }
        if (alternatives != null) {
            return alternatives.equals(item.alternatives);
        }
        return item.stack.isSimilar(stack) && item.stack.getAmount() == stack.getAmount();
    }

    @Override
    public int hashCode() {
        return stack.getType().hashCode() * 31 + stack.getAmount();
    }
}
