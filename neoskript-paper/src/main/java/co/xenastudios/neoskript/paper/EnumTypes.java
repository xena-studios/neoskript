/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Skript's types for enums and registries that are a list of names: teleport causes, spawn reasons, cat types
 * and so on, by the names in Skript's English language file. Each can be written as a literal
 * ({@code ender pearl}, {@code black (cat type)}) and listed with {@code all <type>}; registry values are
 * only looked up once scripts parse or run.
 */
final class EnumTypes {

    private EnumTypes() {}

    static void register(Registrar registrar) {
        registrar.alias("healreason", "regen reason", "regen reasons");
        registrar.alias("healreason", "heal cause", "heal causes");
        registrar.alias("healreason", "regen cause", "regen causes");

        registrar.alias("teleportcause", "teleport reason", "teleport reasons");
        registrar.alias("teleportcause", "teleport type", "teleport types");

        registrar.alias("spawnreason", "spawning reason", "spawning reasons");

        registrar.alias("transformreason", "transform cause", "transform causes");
        registrar.alias("transformreason", "entity transform reason", "entity transform reasons");

        registrar.alias("unleashreason", "unleash cause", "unleash causes");

        registrar.alias("potioncause", "entity potion effect cause", "entity potion effect causes");

        registryType(registrar, "cattype", "cat type", Cat.Type.class, RegistryKey.CAT_VARIANT, "cat types",
                "tabby", "tabby",
                "black", "black",
                "red", "red",
                "siamese", "siamese",
                "british_shorthair", "british shorthair",
                "calico", "calico",
                "persian", "persian",
                "ragdoll", "ragdoll",
                "white", "white",
                "jellie", "jellie",
                "all_black", "all black");
        registryType(registrar, "wolfvariant", "wolf variant", Wolf.Variant.class, RegistryKey.WOLF_VARIANT, "wolf variants",
                "ashen", "ashen",
                "black", "black",
                "chestnut", "chestnut",
                "pale", "pale",
                "rusty", "rusty",
                "snowy", "snowy",
                "spotted", "spotted",
                "striped", "striped",
                "woods", "woods");

        // Biomes and attribute types, registered with their own syntax, can be listed too: 'all biomes'.
        registrar.allValues("biome", () -> RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME));
        registrar.allValues("attributetype", () -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE));
    }

    /**
     * An enum's type, by Skript's names for its constants ({@code table}: constant, then its names). A
     * constant the table leaves out is named as Skript names it then: its words and the language node's,
     * like {@code new thing teleport causes}.
     */
    private static <E extends Enum<E>> void enumType(Registrar registrar, String id, String name, Class<E> type,
            String node, String... table) {
        if (type.getEnumConstants() == null) {
            // Not an enum on this server's version.
            return;
        }
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < table.length; i += 2) {
            names.put(table[i], table[i + 1]);
        }
        Map<String, E> byName = new LinkedHashMap<>();
        Map<E, String> shown = new HashMap<>();
        for (E constant : type.getEnumConstants()) {
            String written = names.get(constant.name());
            List<String> all = written == null
                    ? List.of(constant.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + node)
                    : List.of(written.split(", "));
            for (String each : all) {
                byName.putIfAbsent(each.toLowerCase(Locale.ROOT), constant);
            }
            shown.put(constant, written == null ? constant.name() : all.getFirst());
        }
        registrar.type(id, name, type);
        java.util.function.Function<String, E> parse = text -> byName.get(text.strip().toLowerCase(Locale.ROOT));
        registrar.literalParser(id, parse);
        registrar.parser(id, parse);
        registrar.text(type, shown::get);
        List<E> constants = List.of(type.getEnumConstants());
        registrar.allValues(id, () -> constants);
    }

    /**
     * A registry's type, by Skript's names for its values ({@code table}: key, then its names), looked up
     * in the registry the first time it's needed. A value the table leaves out goes by its key's words.
     */
    static <T extends Keyed> void registryType(Registrar registrar, String id, String name, Class<T> type,
            RegistryKey<T> key, String node, String... table) {
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < table.length; i += 2) {
            names.put(table[i], table[i + 1]);
        }
        java.util.function.Supplier<Map<String, T>> byName = new java.util.function.Supplier<>() {
            private volatile Map<String, T> known;

            @Override
            public Map<String, T> get() {
                Map<String, T> map = known;
                if (map == null) {
                    map = new HashMap<>();
                    for (T value : RegistryAccess.registryAccess().getRegistry(key)) {
                        String written = names.get(value.getKey().getKey());
                        for (String each : (written == null ? value.getKey().getKey().replace('_', ' ') : written).split(", ")) {
                            map.putIfAbsent(each, value);
                        }
                    }
                    known = map;
                }
                return map;
            }
        };
        registrar.type(id, name, type);
        java.util.function.Function<String, T> parse = text -> byName.get().get(text.strip().toLowerCase(Locale.ROOT));
        registrar.literalParser(id, parse);
        registrar.parser(id, parse);
        registrar.text(type, value -> {
            String written = names.get(value.getKey().getKey());
            return written == null ? value.getKey().getKey().replace('_', ' ') : written.split(", ")[0];
        });
        registrar.allValues(id, () -> RegistryAccess.registryAccess().getRegistry(key));
    }
}
