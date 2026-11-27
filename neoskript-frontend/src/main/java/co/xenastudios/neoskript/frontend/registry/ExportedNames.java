/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads item, block and entity names from an export's {@link SyntaxExport.Names}, as the server's literal parsers
 * read them: NeoSkript's {@code ItemType.parse}, {@code EntityKind.parse} and {@code BlockSyntax.blockData}, step
 * by step, with the server's names in place of its registries. Where a parser asks the server something the
 * names can't answer (whether block states are valid), it throws {@link #UNKNOWN}: the words might be a name.
 *
 * <p>The plugin checks these against the server's parsers each time it writes an export ({@code NameCheck}), so
 * a difference shows up there.
 */
public final class ExportedNames {

    /** Thrown where the names can't tell. */
    static final RuntimeException UNKNOWN = new RuntimeException("needs the server", null, false, false) {};

    private static final Pattern ITEM_AMOUNT = Pattern.compile("(\\d+)\\s+(?:of\\s+)?(.+)");
    private static final Pattern ENTITY_AMOUNT = Pattern.compile("(\\d{1,9}) (.+)");
    private static final Pattern ENCHANTMENT = Pattern.compile("(.+?)(?:\\s+(\\d+|[ivx]+))?");
    private static final Pattern ENCHANTMENT_SEPARATOR = Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");
    private static final Pattern POTION = Pattern.compile("(plain )?(splash |lingering )?potion of (.+)");
    private static final Pattern MINECRAFT_ID = Pattern.compile("((?:[a-z0-9_.-]+:)?[a-z0-9_./-]+)(\\[[^\\]]*\\])?");
    private static final Pattern DISPLAY_OF = Pattern.compile("displays?(?: entity)? of ");
    private static final List<String> ROMAN = List.of("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x");
    private static final Set<String> POTIONS = Set.of("potion", "splash_potion", "lingering_potion");

    /** An item a name stands for: several (an alias of several), a block, a potion. */
    record Item(boolean several, boolean block, boolean potion) {}

    private final Map<String, String> materials;
    /** Each item's names, singular and plural, by the material's key: the server's {@code ItemType.alias}. */
    private final Map<String, String> itemNames = new HashMap<>();
    private final Map<String, String> aliases;
    private final Set<String> potions;
    private final Set<String> enchantments;
    private final Set<String> dyes;
    private final Set<String> genes;
    private final Map<String, Integer> entities;
    private final Set<String> plurals;
    private final Set<String> samePlurals;
    private final List<Set<String>> vocabularies = new java.util.ArrayList<>();
    private final List<String> specials = new java.util.ArrayList<>();
    private final boolean thrownPotions;

    ExportedNames(SyntaxExport.Names names) {
        this.materials = names.materials();
        this.aliases = names.aliases();
        this.potions = Set.copyOf(names.potions());
        this.enchantments = Set.copyOf(names.enchantments());
        this.dyes = Set.copyOf(names.dyes());
        this.genes = Set.copyOf(names.pandaGenes());
        this.entities = names.entities();
        this.plurals = Set.copyOf(names.entityPlurals());
        this.samePlurals = Set.copyOf(names.sameEntityPlurals());
        for (SyntaxExport.Vocabulary vocabulary : names.vocabularies()) {
            vocabularies.add(Set.copyOf(vocabulary.words()));
            specials.add(vocabulary.special());
        }
        this.thrownPotions = names.thrownPotions();
        // Items first, then blocks that have no item, which Skript names after their materials too.
        for (boolean items : new boolean[] {true, false}) {
            for (Map.Entry<String, String> material : materials.entrySet()) {
                String key = material.getKey();
                String flags = material.getValue();
                boolean item = flags.indexOf('i') >= 0;
                boolean air = flags.indexOf('a') >= 0;
                boolean block = flags.indexOf('b') >= 0;
                if (air && !key.equals("air") || (items ? !item && !key.equals("air") : item || !block)) {
                    continue;
                }
                String singular = key.replace('_', ' ');
                itemNames.putIfAbsent(singular, key);
                itemNames.putIfAbsent(itemPlural(singular), key);
                if (singular.endsWith("s") && singular.length() > 3) {
                    itemNames.putIfAbsent(singular.substring(0, singular.length() - 1), key);
                }
            }
        }
    }

    private boolean isBlock(String material) {
        return materials.getOrDefault(material, "").indexOf('b') >= 0;
    }

    private boolean isItem(String material) {
        return materials.getOrDefault(material, "").indexOf('i') >= 0;
    }

    private static String words(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ items

    /** Whether {@code text} is an item type literal. */
    boolean itemType(String text) {
        return item(text) != null;
    }

    /** Whether {@code text} is an item stack literal: an item that's one material. */
    boolean itemStack(String text) {
        Item item = item(text);
        return item != null && !item.several();
    }

    /** As {@code ItemType.parse}: the item {@code text} names, or {@code null}. */
    Item item(String text) {
        String words = words(text);
        boolean amountGiven = false;
        String amount = "";
        Matcher counted = ITEM_AMOUNT.matcher(words);
        if (counted.matches()) {
            try {
                Integer.parseInt(counted.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
            amountGiven = true;
            amount = counted.group(1) + " ";
            words = counted.group(2);
        }
        words = words.replaceFirst("^(?:an?|all|every) ", "");
        if (words.startsWith("plain ")) {
            return item((amountGiven ? amount : "") + words.substring(6));
        }
        String alias = aliases.get(words);
        if (alias != null) {
            return new Item(alias.indexOf('g') >= 0, alias.indexOf('b') >= 0, alias.indexOf('p') >= 0);
        }
        Matcher potion = POTION.matcher(words);
        if (potion.matches()) {
            return key(potion.group(3), potions) ? new Item(false, false, true) : null;
        }
        String material = alias(words);
        if (material == null) {
            int of = words.indexOf(" of ");
            while (material == null && of >= 0) {
                material = named(words.substring(0, of));
                if (material != null && !enchantments(words.substring(of + 4))) {
                    return null;
                }
                of = words.indexOf(" of ", of + 1);
            }
        }
        if (material == null) {
            Matcher id = MINECRAFT_ID.matcher(words);
            if (id.matches()) {
                material = matchMaterial(id.group(1));
                if (material != null && id.group(2) != null) {
                    if (!isBlock(material)) {
                        return null;
                    }
                    if (!id.group(2).equals("[]")) {
                        // Whether the block states are valid takes the server.
                        throw UNKNOWN;
                    }
                }
            }
        }
        if (material == null) {
            return null;
        }
        return new Item(false, isBlock(material), POTIONS.contains(material));
    }

    /** As {@code ItemType.alias}: the material called {@code name}, singular or plural, or {@code null}. */
    String alias(String name) {
        String found = itemNames.get(name);
        if (found == null && name.endsWith(" block")) {
            String block = itemNames.get(name.substring(0, name.length() - 6));
            if (block != null && isBlock(block)) {
                return block;
            }
        }
        return found;
    }

    private String named(String name) {
        String material = alias(name);
        if (material == null && MINECRAFT_ID.matcher(name).matches() && !name.contains("[")) {
            material = matchMaterial(name);
        }
        return material;
    }

    /** As Bukkit's {@code Material.matchMaterial}: the material's key, or {@code null}. */
    private String matchMaterial(String name) {
        String filtered = name.startsWith("minecraft:") ? name.substring(10) : name;
        filtered = filtered.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "_").replaceAll("[^a-z0-9_]", "");
        return materials.containsKey(filtered) ? filtered : null;
    }

    private boolean enchantments(String text) {
        for (String part : ENCHANTMENT_SEPARATOR.split(text)) {
            Matcher matcher = ENCHANTMENT.matcher(part.strip());
            if (!matcher.matches() || !key(matcher.group(1).strip().replace(' ', '_'), enchantments)) {
                return false;
            }
            String level = matcher.group(2);
            if (level != null && !ROMAN.contains(level)) {
                try {
                    Integer.parseInt(level);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether {@code name}, with spaces as underscores, is one of {@code keys} ({@code minecraft:swiftness}), as
     * {@code NamespacedKey.fromString} reads it.
     */
    private static boolean key(String name, Set<String> keys) {
        String text = name.strip().replace(' ', '_');
        String[] parts = text.split(":", 3);
        if (text.isEmpty() || parts.length > 2) {
            return false;
        }
        String namespace = parts.length == 2 && !parts[0].isEmpty() ? parts[0] : "minecraft";
        String key = parts.length == 2 ? parts[1] : parts[0];
        if (key.isEmpty()) {
            throw UNKNOWN;
        }
        if (!key.matches("[a-z0-9/._-]+") || !namespace.matches("[a-z0-9._-]+")) {
            return false;
        }
        return keys.contains(namespace + ":" + key);
    }

    private static String itemPlural(String name) {
        if (name.endsWith("s")) {
            return name;
        }
        if (name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    // ------------------------------------------------------------------ blocks

    /** As {@code BlockSyntax.blockData}: whether {@code text} is block data. */
    boolean blockData(String text) {
        String words = text.strip().toLowerCase(Locale.ROOT).replaceFirst("^(?:an?|the) ", "");
        if (words.isEmpty()) {
            return false;
        }
        if (words.contains(" ") && !words.contains("[")) {
            String material = matchMaterial(words.replace(' ', '_'));
            return material != null && isBlock(material) && !isItem(material);
        }
        int bracket = words.indexOf('[');
        String written = (bracket < 0 ? words : words.substring(0, bracket)).strip();
        String name = written.replace(' ', '_');
        boolean states = bracket >= 0;
        String id = name.startsWith("minecraft:") ? name.substring(10) : name.contains(":") ? null : name;
        if (id != null && materials.containsKey(id) && isBlock(id)) {
            if (states) {
                throw UNKNOWN;
            }
            return true;
        }
        Item named = item(written);
        if (named == null || !named.block()) {
            return false;
        }
        if (states) {
            throw UNKNOWN;
        }
        return true;
    }

    // ------------------------------------------------------------------ entities

    /** Whether {@code text} is an entity type literal without an amount. */
    boolean entityType(String text) {
        return !counted(text) && entity(text);
    }

    /** Whether {@code text} is a plural entity type literal: {@code zombies}. */
    boolean pluralEntityType(String text) {
        return !counted(text) && isPlural(text) && entity(text);
    }

    /** Whether {@code text} is an entity type literal, with an amount or not. */
    boolean countedEntityType(String text) {
        return entity(text);
    }

    private static boolean counted(String text) {
        return ENTITY_AMOUNT.matcher(words(text).replaceFirst("^(?:an?|the|any) ", "")).matches();
    }

    /** As {@code EntityKind.parse}: whether {@code text} names a kind of entity. */
    boolean entity(String text) {
        String words = words(text).replaceFirst("^(?:an?|the|any) ", "");
        if (words.isEmpty()) {
            return false;
        }
        Matcher counted = ENTITY_AMOUNT.matcher(words);
        if (counted.matches()) {
            return entity(counted.group(2)) && Integer.parseInt(counted.group(1)) >= 1;
        }
        if (words.startsWith("dropped ") && !words.startsWith("dropped item")) {
            return alias(words.substring(8)) != null;
        }
        if (words.startsWith("falling ") && !words.startsWith("falling block")) {
            String named = alias(words.substring(8));
            String material = named != null ? named : matchMaterial(words.substring(8).replace(' ', '_'));
            return material != null && isBlock(material);
        }
        if (words.startsWith("thrown ") && !words.matches("thrown potions?") && thrownPotions) {
            Item item = item(words.substring(7));
            if (item == null) {
                return false;
            }
            if (item.several()) {
                // One of them at random.
                throw UNKNOWN;
            }
            return item.potion();
        }
        int with = words.indexOf(" with nectar");
        int without = words.indexOf(" without nectar");
        if (without >= 0 || with >= 0) {
            words = words.substring(0, without < 0 ? with : without);
        }
        int color = words.indexOf(" color ");
        if (color >= 0) {
            if (!dye(words.substring(color + 7))) {
                return false;
            }
            words = words.substring(0, color);
        }
        int holding = words.indexOf(" holding ");
        if (holding >= 0) {
            String material = alias(words.substring(holding + 9));
            if (material == null || !isBlock(material)) {
                return false;
            }
            words = words.substring(0, holding);
        }
        Matcher displayOf = DISPLAY_OF.matcher(words);
        int of = displayOf.find() ? displayOf.end() - 4 : words.lastIndexOf(" of ");
        if (of >= 0 && words.substring(0, of).replaceFirst(" entity$", "").matches(".*displays?")) {
            String display = words.substring(0, of).replaceFirst(" entity$", "");
            boolean block = display.startsWith("block");
            Item shown = block ? null : item(words.substring(of + 4));
            String what = words.substring(of + 4).replaceFirst("^(?:an?|the) ", "");
            if (shown == null) {
                String material = alias(what);
                if (material == null) {
                    if (!block) {
                        return false;
                    }
                    // Block data, as 'minecraft:stone': only a block's key without states can be told here.
                    if (what.contains("[")) {
                        throw UNKNOWN;
                    }
                    material = what.startsWith("minecraft:") ? what.substring(10) : what;
                    if (!materials.containsKey(material) || !isBlock(material)) {
                        return false;
                    }
                }
                // The server makes an item of it, which a block that isn't one can't be.
                if (!isItem(material)) {
                    return false;
                }
            }
            words = words.substring(0, of);
        }
        if (words.startsWith("tamed ") && words.endsWith(" ocelot")) {
            words = words.substring(0, words.length() - 6) + "cat";
        }
        String named = words.endsWith(" display entity") ? words.substring(0, words.length() - 7) : words;
        for (String suffix : new String[] {" block displays", " block display", " item displays", " item display"}) {
            if (!named.endsWith(suffix) || named.length() <= suffix.length()) {
                continue;
            }
            String material = alias(named.substring(0, named.length() - suffix.length())
                    .replaceFirst("^(?:an?|the) ", ""));
            if (material != null && (!suffix.startsWith(" block") || isBlock(material))) {
                words = suffix.strip();
            }
            break;
        }
        if (words.endsWith(" entity") && entities.containsKey(words.substring(0, words.length() - 7))) {
            words = words.substring(0, words.length() - 7);
        }
        if (words.endsWith(" kid")) {
            words = words.substring(0, words.length() - 4);
        }
        String[] parts = words.split(" ");
        for (int start = 0; start < parts.length; start++) {
            Integer base = entities.get(String.join(" ", java.util.Arrays.copyOfRange(parts, start, parts.length)));
            if (base == null) {
                continue;
            }
            return prefixes(java.util.Arrays.copyOfRange(parts, 0, start), base);
        }
        return false;
    }

    /** As {@code EntityKind.prefixes}: whether the words before an entity's name are its variants. */
    private boolean prefixes(String[] words, int base) {
        if (words.length == 0) {
            return true;
        }
        Set<String> vocabulary = base < 0 ? Set.of() : vocabularies.get(base);
        String special = base < 0 ? null : specials.get(base);
        int i = 0;
        while (i < words.length) {
            int length = 0;
            for (int end = words.length; end > i; end--) {
                String phrase = String.join(" ", java.util.Arrays.copyOfRange(words, i, end));
                boolean found = vocabulary.contains(phrase);
                if (!found && "panda".equals(special) && (phrase.contains("-") || end - i == 2)) {
                    String[] two = phrase.replace(' ', '-').split("-", 2);
                    found = two.length == 2 && gene(two[0]) && gene(two[1]);
                }
                if (!found && "fish".equals(special) && phrase.contains("-") && end - i == 1) {
                    String[] two = phrase.split("-", 2);
                    found = dye(two[0]) && dye(two[1]);
                }
                if (found) {
                    length = end - i;
                    break;
                }
            }
            if (length == 0) {
                return false;
            }
            i += length;
        }
        return true;
    }

    private boolean dye(String name) {
        return dyes.contains(name.strip().toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    private boolean gene(String name) {
        return genes.contains(name.strip().toLowerCase(Locale.ROOT));
    }

    /** As {@code EntityKind.isPlural}: whether {@code text} names entities in the plural. */
    boolean isPlural(String text) {
        String written = words(text);
        if (written.startsWith("a ") || written.startsWith("an ")) {
            return false;
        }
        int of = written.indexOf(" of ");
        if (of > 0 && isPlural(written.substring(0, of))) {
            return true;
        }
        String[] parts = written.split(" ");
        for (int start = 0; start < parts.length; start++) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(parts, start, parts.length));
            if (plurals.contains(name) || samePlurals.contains(name)) {
                return true;
            }
            if (entities.containsKey(name)) {
                return false;
            }
        }
        return false;
    }

    /** The open types these names read, by type id, with what reads them. */
    Map<String, java.util.function.Predicate<String>> checks() {
        Map<String, java.util.function.Predicate<String>> checks = new HashMap<>();
        checks.put("itemtype", this::itemType);
        checks.put("itemstack", this::itemStack);
        checks.put("blockdata", this::blockData);
        checks.put("entitytype", this::entityType);
        checks.put("pluralentitytype", this::pluralEntityType);
        checks.put("countedentitytype", this::countedEntityType);
        return checks;
    }
}
