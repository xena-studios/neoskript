/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;

/** Biomes, by Skript's names for them ({@code nether wastes}, {@code swampland}) or their keys. */
final class Biomes {

    private Biomes() {}

    /** Skript's names for biomes, by key, from its English language file; later names win, as there. */
    private static final String[] NAMES = {
            "swamp", "swampland, swamp, marsh",
            "swamp_hills", "swamp hills",
            "forest", "forest",
            "taiga", "taiga",
            "desert", "desert",
            "plains", "plains",
            "nether", "hell, nether",
            "the_end", "sky, the end",
            "ocean", "ocean, sea",
            "river", "river",
            "mountains", "extreme hills, mountains",
            "frozen_ocean", "frozen ocean",
            "frozen_river", "frozen river",
            "snowy_tundra", "ice plains, snowy tundra",
            "snowy_mountains", "ice mountains, snowy mountains",
            "mushroom_fields", "mushroom island, mushroom fields",
            "mushroom_field_shore", "mushroom shore, mushroom island shore, mushroom fields shore",
            "beach", "beach",
            "desert_hills", "desert hills",
            "wooded_hills", "forest hills, wooded hills",
            "taiga_hills", "taiga hills",
            "mountain_edge", "small mountains, hills, mountain edge",
            "jungle", "jungle",
            "jungle_hills", "jungle hills",
            "jungle_edge", "jungle edge",
            "deep_ocean", "deep ocean",
            "stone_shore", "stone beach, stone shore",
            "snowy_beach", "cold beach, snowy beach",
            "birch_forest", "birch forest",
            "birch_forest_hills", "birch forest hills",
            "dark_forest", "roofed forest, black forest, dark forest",
            "snowy_taiga", "cold taiga, snowy taiga",
            "snowy_taiga_hills", "cold taiga hills, snowy taiga hills",
            "giant_tree_taiga", "mega taiga, giant tree taiga, redwood taiga",
            "giant_tree_taiga_hills", "mega taiga hills, giant tree taiga hills, redwood taiga hills",
            "wooded_mountains", "extreme hills plus, mountains with trees, wooded mountains",
            "savanna", "savanna",
            "savanna_plateau", "savanna plateau",
            "badlands", "mesa, badlands",
            "wooded_badlands_plateau", "mesa plateau forest, badlands plateau forest, wooded badlands plateau",
            "badlands_plateau", "mesa plateau, badlands plateau",
            "eroded_badlands", "eroded badlands, eroded mesa",
            "sunflower_plains", "sunflower plains",
            "desert_lakes", "desert lakes",
            "flower_forest", "flower forest",
            "taiga_mountains", "taiga mountains",
            "swampland_mountains", "swampland mountains, swamp hills",
            "ice_spikes", "ice plains spikes, spiked ice plains, ice plains with spikes, ice spikes",
            "modified_jungle", "jungle mountains",
            "modified_jungle_edge", "jungle edge mountains",
            "snowy_taiga_mountains", "cold taiga mountains, snowy taiga mountains",
            "shattered_savanna", "savanna mountains, shattered savanna",
            "shattered_savanna_plateau", "savanna plateau mountains, shattered savanna plateau",
            "eroded badlands", "bryce, mesa bryce, mesa (bryce), hoodoos, eroded badlands",
            "modified_wooded_badlands_plateau", "mesa plateau forest mountains, modified wooded badlands plateau, badlands plateau forest mountains",
            "modified_badlands_plateau", "mesa plateau mountains, badlands plateau mountains, modified badlands plateau",
            "tall_birch_forest", "birch forest mountains, tall birch forest",
            "tall_birch_hills", "birch forest hills mountains, tall birch hills",
            "dark_forest_hills", "roofed forest mountains, dark forest hills",
            "giant_spruce_taiga", "mega spruce taiga, giant spruce taiga",
            "gravelly_mountains", "extreme hills mountains, gravelly mountains",
            "modified_gravelly_mountains", "extreme hills plus mountains, modified gravelly mountains",
            "giant_spruce_taiga_hills", "mega spruce taiga hills, giant spruce taiga hills",
            "the_void", "void, the void",
            "small_end_islands", "small end islands",
            "end_midlands", "end midlands",
            "end_highlands", "end highlands",
            "end_barrens", "end barrens",
            "warm_ocean", "warm ocean",
            "lukewarm_ocean", "lukewarm ocean",
            "cold_ocean", "cold ocean",
            "deep_warm_ocean", "deep warm ocean",
            "deep_lukewarm_ocean", "deep lukewarm ocean",
            "deep_cold_ocean", "deep cold ocean",
            "deep_frozen_ocean", "deep frozen ocean",
            "bamboo_jungle", "bamboo jungle",
            "bamboo_jungle_hills", "bamboo jungle hills",
            "nether_wastes", "nether wastes, nether, hell",
            "crimson_forest", "crimson forest",
            "soul_sand_valley", "soul sand valley",
            "warped_forest", "warped forest",
            "basalt_deltas", "basalt deltas",
            "custom", "custom, unknown",
            "dripstone_caves", "dripstone caves",
            "lush_caves", "lush caves",
            "meadow", "meadow",
            "snowy_slopes", "snowy slopes",
            "jagged_peaks", "jagged peaks",
            "stony_peaks", "stony peaks",
            "frozen_peaks", "frozen peaks",
            "grove", "grove",
            "old_growth_birch_forest", "old growth birch forest, tall birch forest",
            "old_growth_pine_taiga", "old growth pine taiga, giant tree taiga",
            "old_growth_spruce_taiga", "old growth spruce taiga, giant spruce taiga",
            "snowy_plains", "snowy plains, snowy tundra",
            "sparse_jungle", "sparse jungle, jungle edge",
            "stony_shore", "stony shore, stone shore",
            "windswept_hills", "windswept hills, mountains",
            "windswept_forest", "windswept forest, wooded mountains",
            "windswept_gravelly_hills", "windswept gravelly hills, gravelly mountains",
            "windswept_savanna", "windswept savanna, shattered savanna",
            "wooded_badlands", "wooded badlands, wooded mesa, mesa forest, badlands forest",

    };

    /** Skript's names of the server's biomes, worked out on first use as they come from its registry. */
    private static volatile Map<String, Biome> byName;

    static void register(Registrar registrar) {
        registrar.type("biome", "biome", Biome.class);
        registrar.literalParser("biome", Biomes::parse);
        registrar.parser("biome", Biomes::parse);
        registrar.text(Biome.class, Biomes::name);
        // The biome at a location, which can be set.
        registrar.expression("ExprBiome", "biome", info -> {
            SettableProperty<Object, Biome> biome = new SettableProperty<>(Object.class, Biome.class,
                    owner -> owner instanceof Location location && location.getWorld() != null
                            ? location.getBlock().getBiome() : null, (owner, value) -> {
                        if (owner instanceof Location location && location.getWorld() != null) {
                            location.getBlock().setBiome(value);
                        }
                    });
            return info.pattern() == 1 ? biome : new Located(biome);
        }, "[the] biome [(of|%direction%) %locations%]", "%locations%'[s] biome");
        // Any time state is taken, and changes nothing, as in Skript.
        registrar.timeStates("ExprBiome", info -> true);
    }

    /**
     * {@code the biome 2 above X}: the biome of the locations the direction (if any) combines with the locations,
     * or with the event's location without any.
     */
    private record Located(SettableProperty<Object, Biome> biome)
            implements co.xenastudios.neoskript.api.element.Expression, co.xenastudios.neoskript.api.element.Changeable {

        private static Object[] places(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            Object locations = arguments[1] == null ? WorldProperties.eventLocation(context.event()) : arguments[1];
            Object places = arguments[0] == null ? locations : Direction.combine(arguments[0], locations).toArray();
            return new Object[] {places};
        }

        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            return biome.get(context, places(context, arguments));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return biome.acceptChange(mode);
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments,
                Object[] delta, Mode mode) {
            biome.change(context, places(context, arguments), delta, mode);
        }
    }

    /** The biome {@code text} names, by Skript's name or its key ({@code minecraft:plains}), or {@code null}. */
    static Biome parse(String text) {
        String name = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Biome named = names().get(name);
        if (named != null) {
            return named;
        }
        NamespacedKey key = NamespacedKey.fromString(name.replace(' ', '_'));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
    }

    /** How Skript writes a biome: its first name, or its key's words. */
    static String name(Biome biome) {
        String key = biome.getKey().getKey();
        for (int i = 0; i < NAMES.length; i += 2) {
            if (NAMES[i].equals(key)) {
                return NAMES[i + 1].split(", ")[0];
            }
        }
        return key.replace('_', ' ');
    }

    private static Map<String, Biome> names() {
        Map<String, Biome> names = byName;
        if (names == null) {
            names = new HashMap<>();
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            for (Biome biome : registry) {
                names.put(biome.getKey().getKey().replace('_', ' '), biome);
            }
            for (int i = 0; i < NAMES.length; i += 2) {
                NamespacedKey key = NamespacedKey.fromString(NAMES[i].replace(' ', '_'));
                Biome biome = key == null ? null : registry.get(key);
                if (biome != null) {
                    for (String alias : NAMES[i + 1].split(", ")) {
                        names.put(alias, biome);
                    }
                }
            }
            byName = names;
        }
        return names;
    }
}
