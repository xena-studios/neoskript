/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Panda;

/**
 * The item, block and entity names this server reads, for the syntax export ({@link #names()}), and a check that
 * the browser's registry reads them as the server's literal parsers do ({@link #check}).
 */
public final class NameExport {

    private NameExport() {}

    /** The types whose literals the names are for. */
    static final List<String> TYPES = List.of("itemtype", "itemstack", "blockdata", "entitytype", "pluralentitytype",
            "countedentitytype");

    private static final Set<Material> POTIONS = Set.of(Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION);

    /** What the browser's registry needs to read the names as this server does. */
    public static SyntaxExport.Names names() {
        Map<String, String> materials = new LinkedHashMap<>();
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            materials.put(material.getKey().getKey(), (material.isItem() ? "i" : "") + (material.isBlock() ? "b" : "")
                    + (material.isAir() ? "a" : ""));
        }
        Map<String, String> aliases = new TreeMap<>();
        for (String name : AliasFiles.names()) {
            ItemType item;
            try {
                item = ItemType.parse(name);
            } catch (RuntimeException e) {
                continue;
            }
            if (item == null) {
                continue;
            }
            boolean several = item.alternatives().size() > 1;
            Material material = item.material();
            // The same as its material's own name: nothing to write down.
            if (!several && ItemType.alias(name) == material) {
                continue;
            }
            aliases.put(name, (several ? "g" : "") + (material.isBlock() ? "b" : "")
                    + (POTIONS.contains(material) ? "p" : ""));
        }
        Map<String, Integer> entities = new LinkedHashMap<>();
        List<SyntaxExport.Vocabulary> vocabularies = new ArrayList<>();
        Set<String> plurals = new TreeSet<>();
        Set<String> samePlurals = new TreeSet<>();
        EntityKind.export(entities, vocabularies, plurals, samePlurals);
        List<String> dyes = new ArrayList<>();
        for (DyeColor dye : DyeColor.values()) {
            dyes.add(dye.name().toLowerCase(Locale.ROOT));
        }
        List<String> genes = new ArrayList<>();
        for (Panda.Gene gene : Panda.Gene.values()) {
            genes.add(gene.name().toLowerCase(Locale.ROOT));
        }
        return new SyntaxExport.Names(materials, aliases, keys(org.bukkit.Registry.POTION),
                keys(RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)), dyes, genes, entities,
                List.copyOf(plurals), List.copyOf(samePlurals), vocabularies, EntityKind.hasThrownPotions());
    }

    private static List<String> keys(Iterable<? extends Keyed> registry) {
        Set<String> keys = new TreeSet<>();
        for (Keyed value : registry) {
            keys.add(value.getKey().toString());
        }
        return List.copyOf(keys);
    }

    /** What {@link #check} found. */
    public record Check(int checked, int unknown, List<String> differences) {}

    /**
     * Reads names made from this server's own (every material, alias, potion, enchantment and kind of entity, with
     * amounts, variants, misspellings and the like) with {@code server}'s literal parsers and {@code exported}'s,
     * and lists where they differ: where the export says words are or aren't a name and the server says otherwise.
     * Where the export can't tell, it's counted as unknown.
     */
    public static Check check(SyntaxRegistry server, SyntaxRegistry exported, SyntaxExport.Names names) {
        Set<String> phrases = phrases(names);
        int checked = 0;
        int unknown = 0;
        List<String> differences = new ArrayList<>();
        for (String id : TYPES) {
            TypeInfo serverType = server.type(id);
            TypeInfo exportedType = exported.type(id);
            if (serverType == null || exportedType == null) {
                differences.add("type " + id + " is missing");
                continue;
            }
            for (String phrase : phrases) {
                checked++;
                boolean is = server.isLiteral(serverType, phrase);
                boolean certainly = exported.isLiteral(exportedType, phrase);
                boolean might = exported.mightBeLiteral(exportedType, phrase);
                if (might && !certainly) {
                    unknown++;
                }
                if (is ? !might : certainly) {
                    differences.add("'" + phrase + "' as " + id + ": the server says " + is + ", the export " + !is);
                }
            }
        }
        return new Check(checked, unknown, differences);
    }

    private static Set<String> phrases(SyntaxExport.Names names) {
        Set<String> phrases = new LinkedHashSet<>();
        String enchantment = names.enchantments().isEmpty() ? "sharpness"
                : names.enchantments().getFirst().replace("minecraft:", "").replace('_', ' ');
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            String key = material.getKey().getKey();
            String name = key.replace('_', ' ');
            for (String form : List.of(name, name + "s", name + "es", name + " block", misspelt(name))) {
                phrases.add(form);
                phrases.add("5 " + form);
                phrases.add("5 of " + form);
                phrases.add("a " + form);
                phrases.add("plain " + form);
                phrases.add("all " + form);
                phrases.add("the " + form);
                phrases.add(form + " of " + enchantment + " 2");
                phrases.add(form + " of " + enchantment + " ii, unbreaking 3");
                phrases.add(form + " of " + enchantment + " xx");
                phrases.add(form + " of " + misspelt(enchantment));
                phrases.add("dropped " + form);
                phrases.add("falling " + form);
                phrases.add("thrown " + form);
                phrases.add(form + " block display");
                phrases.add(form + " item display");
                phrases.add("item display of " + form);
                phrases.add("block display of " + form);
                phrases.add("enderman holding " + form);
            }
            phrases.add(key);
            phrases.add("minecraft:" + key);
            phrases.add(key + "[]");
            phrases.add("minecraft:" + key + "[]");
            phrases.add("block display of minecraft:" + key);
        }
        for (String alias : names.aliases().keySet()) {
            phrases.add(alias);
            phrases.add("3 of " + alias);
            phrases.add("plain " + alias);
            phrases.add("thrown " + alias);
            phrases.add("item display of " + alias);
            phrases.add(misspelt(alias));
        }
        for (String potion : names.potions()) {
            String name = potion.replace("minecraft:", "").replace('_', ' ');
            phrases.add("potion of " + name);
            phrases.add("splash potion of " + name);
            phrases.add("lingering potion of " + potion);
            phrases.add("plain potion of " + name);
            phrases.add("thrown splash potion of " + name);
            phrases.add("potion of " + misspelt(name));
        }
        for (Map.Entry<String, Integer> entity : names.entities().entrySet()) {
            String name = entity.getKey();
            for (String form : List.of(name, misspelt(name))) {
                phrases.add(form);
                phrases.add("a " + form);
                phrases.add("the " + form);
                phrases.add("3 " + form);
                phrases.add("0 " + form);
                phrases.add("all " + form);
                phrases.add(form + " entity");
                phrases.add(form + " kid");
                phrases.add("baby " + form);
                phrases.add(form + " with nectar");
                phrases.add(form + " color red");
                phrases.add(form + " color rde");
            }
            if (entity.getValue() >= 0) {
                for (String word : names.vocabularies().get(entity.getValue()).words()) {
                    phrases.add(word + " " + name);
                    phrases.add("baby " + word + " " + name);
                    phrases.add("2 " + word + " " + name);
                    phrases.add(misspelt(word) + " " + name);
                }
            }
        }
        for (String phrase : List.of("lazy-aggressive panda", "lazy aggressive panda", "lazy-xyz panda",
                "red-blue kob", "red-xyz kob", "light_blue-red kob", "tamed ocelot", "tamed baby ocelot",
                "oak stairs[]", "oak_stairs[half=top]", "minecraft:oak_stairs[facing=north]", "the stone",
                "oak wall sign", "void air", "stone[]", "stne[]", "stone brick", "stone bricks", "1 stone brick",
                "an angry bee with nectar", "red sheep", "rde sheep", "farmer villager", "lucy axolotl")) {
            phrases.add(phrase);
        }
        return phrases;
    }

    /** {@code name} with its last letter left out: a misspelling. */
    private static String misspelt(String name) {
        return name.length() > 3 ? name.substring(0, name.length() - 1) : name + "q";
    }
}
