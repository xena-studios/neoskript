/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
    /**
     * The meta it was last given, as Skript's item types keep it: it can hold what its item can't, like an
     * enchantment of level 0. {@code null} until its meta is changed, and again when its item is replaced.
     */
    private ItemMeta meta;
    /**
     * A block that has no item, like water or a bubble column, which Skript's item types can be: its stack is
     * then air. {@code null} for anything else.
     */
    private Material block;
    /**
     * The block data an alias gives its block, like {@code north anvil = minecraft:anvil[facing=north]}, which the
     * block gets when it's placed. {@code null} for the block's default, and for items that aren't blocks.
     */
    private org.bukkit.block.data.BlockData blockData;

    public ItemType(ItemStack stack, boolean amountGiven) {
        this.stack = stack.clone();
        this.amountGiven = amountGiven;
    }

    /**
     * A block's item, with the block's data, as Skript's item type of a block: it's written as the alias for that
     * block data ({@code south anvil}), and placing it places the same block data.
     */
    static ItemType ofBlock(org.bukkit.block.Block block) {
        ItemType type = new ItemType(new ItemStack(block.getType().isItem() ? block.getType() : Material.AIR), false);
        type.blockData = block.getBlockData();
        return type;
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
        // A block with no item, or an alias's block states, as the first item has them.
        type.block = all.getFirst().block;
        type.blockData = all.getFirst().blockData;
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
        return block != null ? block : stack.getType();
    }

    /**
     * Its Minecraft id, as Skript's {@code Aliases.getMinecraftId}: as the alias file that names it wrote it
     * ({@code cod} for {@code cooked fish = cod}), or its material's key.
     */
    public String rawName() {
        AliasTable.Named named = GlobalAliases.named(material(), block != null ? null : stack, blockData, false);
        return named != null ? named.item().id() : material().getKey().toString();
    }

    /** The block data an alias gives the block (see {@link #blockData}), or {@code null}. */
    public org.bukkit.block.data.BlockData blockData() {
        return blockData == null ? null : blockData.clone();
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

    /**
     * A plain copy: just its kind and amount, which, as Skript's plain types, only items with nothing more to
     * them are.
     */
    ItemType plainCopy() {
        ItemType copy = new ItemType(block != null ? new ItemStack(Material.AIR) : new ItemStack(material(), amount()),
                amountGiven);
        copy.block = block;
        copy.markPlain();
        return copy;
    }

    /** Replaces its item in place, keeping whether an amount was given. */
    void replace(ItemStack replacement) {
        stack = replacement.clone();
        meta = null;
    }

    /** Changes its item in place, each of them if it stands for several. */
    void editStacks(Consumer<ItemStack> change) {
        ItemStack copy = stack.clone();
        change.accept(copy);
        stack = copy;
        meta = null;
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
        ItemMeta edited = meta();
        if (edited != null) {
            change.accept(edited);
            stack.setItemMeta(edited);
            meta = edited.clone();
        }
        if (alternatives != null) {
            alternatives.forEach(each -> each.editMeta(change));
        }
    }

    /**
     * A copy of its meta, as it was last given (see {@link #meta}): what Skript's item types give as theirs,
     * which can hold more than {@code stack().getItemMeta()}.
     */
    public ItemMeta meta() {
        return meta != null ? meta.clone() : stack.getItemMeta();
    }

    /** A copy, as stored in a variable. */
    public ItemType copy() {
        ItemType copy = new ItemType(stack, amountGiven);
        copy.plain = plain;
        copy.meta = meta == null ? null : meta.clone();
        copy.block = block;
        copy.blockData = blockData;
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
        if (aliased == null) {
            aliased = fileAlias(words, amount, amountGiven);
        }
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
            // 'fancy sword of sharpness 2', with an alias from the alias files before the ' of '.
            for (int of = words.indexOf(" of "); of >= 0; of = words.indexOf(" of ", of + 1)) {
                ItemType named = fileAlias(words.substring(0, of), amount, amountGiven);
                if (named == null) {
                    continue;
                }
                Map<Enchantment, Integer> given = enchantments(words.substring(of + 4));
                if (given == null) {
                    continue;
                }
                if (!given.isEmpty()) {
                    named.editStacks(each -> each.editMeta(meta -> given.forEach((enchantment, level) ->
                            meta.addEnchant(enchantment, level, true))));
                }
                return named;
            }
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
            // As Skript, only with a namespace or an underscore: 'diamond' is a name, which an alias file can take.
            Matcher id = MINECRAFT_ID.matcher(words);
            if (id.matches() && (id.group(1).contains(":") || id.group(1).contains("_"))) {
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
        if (!material.isItem() && material.isBlock()) {
            ItemType block = new ItemType(new ItemStack(Material.AIR), amountGiven);
            block.block = material;
            return block;
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

    /**
     * The item an alias file calls {@code name}, like {@code speed potion} from Skript's default aliases or
     * {@code ruby} from {@code plugins/NeoSkript/aliases}, or {@code null}. As in Skript, {@code ruby item} and
     * {@code ruby block} are the alias's items that are items, or blocks.
     */
    private static ItemType fileAlias(String name, int amount, boolean amountGiven) {
        GlobalAliases.used(name);
        AliasTable.Type alias = GlobalAliases.alias(name);
        boolean blocks = false;
        boolean items = false;
        if (alias == null) {
            int space = name.lastIndexOf(' ');
            String last = space < 0 ? "" : name.substring(space + 1);
            blocks = last.equals("block") || last.equals("blocks");
            items = last.equals("item") || last.equals("items");
            if (!blocks && !items) {
                return null;
            }
            GlobalAliases.used(name.substring(0, space));
            alias = GlobalAliases.alias(name.substring(0, space));
            if (alias == null) {
                return null;
            }
        }
        List<ItemType> types = new ArrayList<>();
        for (AliasTable.Item item : alias.items) {
            Material material = Material.matchMaterial(item.material());
            if (material == null || blocks && (!material.isBlock() || material.getKey().getKey().endsWith("block"))
                    || items && !material.isItem()) {
                continue;
            }
            ItemType type;
            if (item.stack() instanceof ItemStack stack) {
                type = new ItemType(stack, false);
            } else if (material.isBlock()) {
                type = new ItemType(new ItemStack(Material.AIR), false);
                type.block = material;
            } else {
                continue;
            }
            if (!item.states().isEmpty() && item.blockData() instanceof org.bukkit.block.data.BlockData data) {
                type.blockData = data.clone();
            }
            types.add(type);
        }
        if (types.isEmpty()) {
            return null;
        }
        ItemType type = anyOf(types);
        if (amountGiven) {
            type.setAmount(amount);
        }
        return type;
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
        if (material == null && MINECRAFT_ID.matcher(name).matches() && !name.contains("[")
                && (name.contains(":") || name.contains("_"))) {
            material = Material.matchMaterial(name);
        }
        return material;
    }

    /** The item called {@code name}, singular or plural, or {@code null}. */
    static Material alias(String name) {
        Map<String, Material> known = aliases;
        if (known == null) {
            known = new HashMap<>();
            // Items first, then blocks that have no item (water, a bubble column), which Skript names after
            // their materials too; an item keeps its names where a block's would clash.
            for (boolean items : new boolean[] {true, false}) {
                for (Material material : Material.values()) {
                    // Air counts, as in Skript ('set block at X to air'), but not the other airs.
                    if (material.isLegacy() || material.isAir() && material != Material.AIR
                            || (items ? !material.isItem() && material != Material.AIR
                                    : material.isItem() || !material.isBlock())) {
                        continue;
                    }
                    String singular = keyName(material);
                    known.putIfAbsent(singular, material);
                    known.putIfAbsent(plural(singular), material);
                    // Items named in the plural, like stone bricks, can be written in the singular too.
                    if (singular.endsWith("s") && singular.length() > 3) {
                        known.putIfAbsent(singular.substring(0, singular.length() - 1), material);
                    }
                }
            }
            aliases = known;
        }
        GlobalAliases.used(name);
        Material found = known.get(name);
        // As in Skript, an item an alias file names doesn't have these names ('ruby = minecraft:red_dye' takes
        // 'red dye' away).
        if (found != null && GlobalAliases.namedByFiles(found)) {
            found = null;
        }
        // 'dirt block' is dirt, as Skript names blocks.
        if (found == null && name.endsWith(" block")) {
            Material block = known.get(name.substring(0, name.length() - 6));
            if (block != null && block.isBlock() && !GlobalAliases.namedByFiles(block)) {
                return block;
            }
        }
        return found;
    }

    /** NeoSkript's own names for {@code material}, which it has unless an alias file names it. */
    static List<String> ownNames(Material material) {
        alias("");
        List<String> names = new ArrayList<>();
        aliases.forEach((name, each) -> {
            if (each == material) {
                names.add(name);
                names.add(name + " block");
            }
        });
        return names;
    }

    /** A material's own name: its key in lower case, with spaces. */
    static String keyName(Material material) {
        return material.getKey().getKey().replace('_', ' ');
    }

    /** How an item reads: what an alias file calls it ({@code ruby}), or its material's own name. */
    static String name(Material material) {
        return name(material, null);
    }

    /** How a block reads, with its block data: what an alias file calls it ({@code north anvil}), or its own name. */
    static String name(Material material, org.bukkit.block.data.BlockData data) {
        AliasTable.Named named = GlobalAliases.named(material, null, data, false);
        return named != null ? named.singular() : keyName(material);
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

    /**
     * As Skript writes items: {@code 32 cooked beef}, or {@code diamond sword named Blade}. Like Skript's
     * ItemData, enchantments aren't written.
     */
    static String text(ItemStack stack, boolean withAmount) {
        return text(stack, null, withAmount, true);
    }

    private static String text(ItemStack stack, org.bukkit.block.data.BlockData blockData, boolean withAmount,
            boolean itemForm) {
        StringBuilder text = new StringBuilder();
        // As Skript, what the alias files call it, singular or plural, else its own name.
        AliasTable.Named named = GlobalAliases.named(stack.getType(), stack, blockData, itemForm);
        String name = named != null ? named.singular() : keyName(stack.getType());
        if (withAmount && stack.getAmount() != 1) {
            text.append(stack.getAmount()).append(' ').append(named != null ? named.plural() : plural(name));
        } else {
            text.append(name);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            text.append(" named ").append(TextComponentParser.instance().toString(meta.displayName()));
        }
        return text.toString();
    }

    @Override
    public String toString() {
        if (alternatives != null) {
            // As Skript: the amount once, then each item, in the plural if there are several.
            int amount = amount();
            boolean counted = alternatives.getFirst().amountGiven && amount != 1;
            List<String> names = alternatives.stream().map(each -> counted ? each.withoutAmount(true)
                    : each.toString()).toList();
            return (counted ? amount + " " : "") + String.join(", ", names.subList(0, names.size() - 1)) + " or "
                    + names.getLast();
        }
        return block != null ? name(block, blockData) : text(stack, blockData, amountGiven, false);
    }

    /** How it reads without its amount, in the plural or not: {@code rubys}, {@code fancy swords named Fancy}. */
    private String withoutAmount(boolean plural) {
        if (block != null) {
            return name(block, blockData);
        }
        String text = text(stack, blockData, plural, false);
        String prefix = stack.getAmount() + " ";
        return plural && stack.getAmount() != 1 && text.startsWith(prefix) ? text.substring(prefix.length()) : text;
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
        return item.block == block && item.stack.isSimilar(stack) && item.stack.getAmount() == stack.getAmount();
    }

    @Override
    public int hashCode() {
        return stack.getType().hashCode() * 31 + stack.getAmount();
    }
}
