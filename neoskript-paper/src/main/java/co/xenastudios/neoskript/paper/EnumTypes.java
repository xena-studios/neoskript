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
        enumType(registrar, "healreason", "heal reason", RegainReason.class, "heal reasons",
                "CUSTOM", "unknown, custom, plugin, a plugin",
                "EATING", "eating, consuming, ingesting",
                "ENDER_CRYSTAL", "end crystal, ender crystal, an end crystal, an ender crystal",
                "MAGIC", "magic, potion, a potion",
                "MAGIC_REGEN", "magic regen, magic regeneration, a magic regen, a magic regeneration, a healing potion, "
                        + "a regeneration potion, healing potion, regeneration potion, regen potion, a regen potion",
                "REGEN", "peaceful regeneration, peaceful regen, peaceful",
                "SATIATED", "satiated, satisfied, fed, sated",
                "WITHER", "withering, withered, wither potion, a wither effect, wither effect, wither",
                "WITHER_SPAWN", "wither spawning, wither spawn, wither summoning, a wither summoning, a wither spawn, "
                        + "a wither spawning");
        registrar.alias("healreason", "regen reason", "regen reasons");
        registrar.alias("healreason", "heal cause", "heal causes");
        registrar.alias("healreason", "regen cause", "regen causes");

        enumType(registrar, "teleportcause", "teleport cause", TeleportCause.class, "teleport causes",
                "CHORUS_FRUIT", "chorus, chorus fruit",
                "COMMAND", "command",
                "END_GATEWAY", "gateway, end gateway, ender gateway",
                "END_PORTAL", "end portal, ender portal",
                "ENDER_PEARL", "ender pearl",
                "NETHER_PORTAL", "nether portal",
                "PLUGIN", "plugin",
                "SPECTATE", "spectate, spectator",
                "UNKNOWN", "unknown",
                "DISMOUNT", "dismount, dismounted",
                "EXIT_BED", "exit bed, exiting bed, bed exit",
                "CONSUMABLE_EFFECT", "consumable effect");
        registrar.alias("teleportcause", "teleport reason", "teleport reasons");
        registrar.alias("teleportcause", "teleport type", "teleport types");

        enumType(registrar, "inventoryclosereason", "inventory close reason", InventoryCloseEvent.Reason.class,
                "inventory close reasons",
                "UNKNOWN", "unknown",
                "TELEPORT", "teleport",
                "CANT_USE", "can't use, can not use, cannot use",
                "UNLOADED", "unloaded",
                "OPEN_NEW", "open new, new opened",
                "PLAYER", "player",
                "DISCONNECT", "disconnected, disconnect",
                "DEATH", "death",
                "PLUGIN", "plugin");

        enumType(registrar, "inventoryaction", "inventory action", InventoryAction.class, "inventory actions",
                "NOTHING", "nothing, do nothing",
                "PICKUP_ALL", "pickup all, pickup all items",
                "PICKUP_SOME", "pickup some, pickup some items",
                "PICKUP_HALF", "pickup half, pickup half stack",
                "PICKUP_ONE", "pickup one item, pickup single item, pickup single",
                "PLACE_ALL", "place all, place all items",
                "PLACE_SOME", "place some, place some items",
                "PLACE_ONE", "place one, place one item",
                "SWAP_WITH_CURSOR", "swap with cursor, swap stack with cursor, swap cursor, swap cursor stack",
                "DROP_ALL_CURSOR", "drop all from cursor, drop stack from cursor, drop items from cursor, "
                        + "drop stack from cursor, drop cursor, drop cursor stack",
                "DROP_ONE_CURSOR", "drop one from cursor, drop one item from cursor, drop single item from cursor, "
                        + "drop cursor item",
                "DROP_ALL_SLOT", "drop all from slot, drop stack from slot, drop items from slot, drop stack from slot, "
                        + "drop slot, drop slot stack",
                "DROP_ONE_SLOT", "drop one from slot, drop one item from slot, drop single item from slot, drop slot item",
                "MOVE_TO_OTHER_INVENTORY", "instant move, move to other inventory, shift move",
                "HOTBAR_MOVE_AND_READD", "hotbar move and readd",
                "HOTBAR_SWAP", "swap with hotbar, swap items with hotbar, hotbar swap, hotbar swap items",
                "CLONE_STACK", "clone stack",
                "COLLECT_TO_CURSOR", "collect to cursor, collect items to cursor",
                "UNKNOWN", "unknown, unsupported, custom",
                "PICKUP_FROM_BUNDLE", "pickup from bundle, pickup items from bundle",
                "PICKUP_ALL_INTO_BUNDLE", "pickup all into bundle, pickup all items into bundle",
                "PICKUP_SOME_INTO_BUNDLE", "pickup some into bundle, pickup some items into bundle",
                "PLACE_FROM_BUNDLE", "place from bundle, place items from bundle",
                "PLACE_ALL_INTO_BUNDLE", "place all into bundle, place all items into bundle",
                "PLACE_SOME_INTO_BUNDLE", "place some into bundle, place some items into bundle");

        enumType(registrar, "clicktype", "click type", ClickType.class, "click types",
                "LEFT", "left mouse button, left mouse, LMB",
                "SHIFT_LEFT", "left mouse button with shift, left mouse with shift, Shift+LMB",
                "RIGHT", "right mouse button, right mouse, RMB",
                "SHIFT_RIGHT", "right mouse button with shift, right mouse with shift, Shift+RMB",
                "WINDOW_BORDER_LEFT", "window border using right mouse button, window border using right mouse, "
                        + "border using LMB",
                "WINDOW_BORDER_RIGHT", "window border using left mouse button, window border using right mouse, "
                        + "border using RMB",
                "MIDDLE", "middle mouse button, middle mouse, MMB",
                "NUMBER_KEY", "number key, 0-9",
                "DOUBLE_CLICK", "double click using mouse, double click",
                "DROP", "drop key, drop item, Q",
                "CONTROL_DROP", "drop key with control, drop stack, Ctrl+Q",
                "CREATIVE", "creative action",
                "UNKNOWN", "unknown, unsupported, custom",
                "SWAP_OFFHAND", "swap offhand, swap shield");

        enumType(registrar, "spawnreason", "spawn reason", SpawnReason.class, "spawn reasons",
                "BED", "bed",
                "BEEHIVE", "beehive",
                "BREEDING", "breed, breeding",
                "BUCKET", "release from bucket",
                "BUILD_IRONGOLEM", "built iron golem, build iron golem",
                "BUILD_SNOWMAN", "built snowman, build snowman",
                "BUILD_WITHER", "built wither, build wither",
                "CHUNK_GEN", "chunk generation",
                "COMMAND", "command",
                "CURED", "cured",
                "CUSTOM", "customized, customised, custom",
                "DEFAULT", "default",
                "DISPENSE_EGG", "dispense egg, dispensing egg",
                "DROWNED", "drowned",
                "DUPLICATION", "duplication",
                "EGG", "egg",
                "ENDER_PEARL", "ender pearl",
                "EXPLOSION", "explosion",
                "FROZEN", "frozen",
                "INFECTION", "infection, infected",
                "JOCKEY", "jockey",
                "LIGHTNING", "lightning",
                "METAMORPHOSIS", "metamorphosis",
                "MOUNT", "mount",
                "NATURAL", "natural",
                "NETHER_PORTAL", "nether portal",
                "OCELOT_BABY", "ocelot baby",
                "OMINOUS_ITEM_SPAWNER", "ominous item spawner",
                "PATROL", "patrol",
                "PIGLIN_ZOMBIFIED", "piglin zombification",
                "POTION_EFFECT", "potion effect",
                "RAID", "raid",
                "REINFORCEMENTS", "reinforcements",
                "SHEARED", "shear, sheared",
                "SHOULDER_ENTITY", "perching, shoulder",
                "SILVERFISH_BLOCK", "silverfish reveal, silverfish trap",
                "SLIME_SPLIT", "slime split",
                "SPAWNER", "mob spawner, creature spawner, spawner",
                "SPAWNER_EGG", "spawn egg",
                "SPELL", "spell",
                "TRAP", "trap",
                "VILLAGE_DEFENSE", "village defense, golem defense, iron golem defense",
                "VILLAGE_INVASION", "village invasion, village invading",
                "TRIAL_SPAWNER", "trial spawner, trial mob spawner, trial creature spawner",
                "ENCHANTMENT", "enchantment",
                "REHYDRATION", "rehydration, rehydrated",
                "BUILD_COPPERGOLEM", "build copper golem, built copper golem",
                "REANIMATE", "reanimate");
        registrar.alias("spawnreason", "spawning reason", "spawning reasons");

        enumType(registrar, "transformreason", "transform reason", TransformReason.class, "transform reasons",
                "CURED", "zombie cure, zombie curing, curing",
                "DROWNED", "entity drowning, drowning, zombie converting to drowned, zombie drowning",
                "FROZEN", "entity freezing, entity freeze, skeleton freezing, skeleton freeze, skeleton converting to stray",
                "LIGHTNING", "lightning, creeper super charge",
                "METAMORPHOSIS", "tadpole converting, tadpole converting to frog, tadpole metamorphosis, metamorphosis",
                "PIGLIN_ZOMBIFIED", "piglin zombification",
                "SHEARED", "mooshroom shear, mooshroom shearing",
                "SPLIT", "slime split, slime splitting, split, magma slime split, magma slime splitting",
                "UNKNOWN", "unknown",
                "INFECTION", "infection, villager infection");
        registrar.alias("transformreason", "transform cause", "transform causes");
        registrar.alias("transformreason", "entity transform reason", "entity transform reasons");

        enumType(registrar, "unleashreason", "unleash reason", UnleashReason.class, "unleash reasons",
                "DISTANCE", "distance",
                "HOLDER_GONE", "holder gone, holder disappeared",
                "LEASHED_GONE", "leashed gone, leashed disappeared",
                "PLAYER_UNLEASH", "player unleash, player unleashed, unleashed by player",
                "UNKNOWN", "unknown");
        registrar.alias("unleashreason", "unleash cause", "unleash causes");

        enumType(registrar, "potioncause", "potion effect cause", EntityPotionEffectEvent.Cause.class, "potion causes",
                "AREA_EFFECT_CLOUD", "enter area effect cloud",
                "ARROW", "arrow infliction",
                "ATTACK", "attack",
                "AXOLOTL", "axolotl",
                "WARDEN", "warden",
                "BEACON", "beacon effect",
                "COMMAND", "command",
                "CONDUIT", "conduit effect",
                "CONVERSION", "conversion, converted",
                "DEATH", "death",
                "DOLPHIN", "dolphin boost",
                "EXPIRATION", "expiration, expired",
                "FOOD", "food",
                "ILLUSION", "illusion",
                "MILK", "drinking milk",
                "PLUGIN", "plugin",
                "POTION_DRINK", "potion drunk, drinking potion",
                "POTION_SPLASH", "potion splash, splash potion",
                "SPIDER_SPAWN", "spider spawn, spawned spider",
                "TOTEM", "removal by resurrection",
                "TURTLE_HELMET", "turtle helmet effect",
                "UNKNOWN", "unknown",
                "VILLAGER_TRADE", "villager trade",
                "PATROL_CAPTAIN", "pillager captain, patrol captain",
                "WITHER_ROSE", "wither rose infliction",
                "NAUTILUS", "nautilus");
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
    private static <T extends Keyed> void registryType(Registrar registrar, String id, String name, Class<T> type,
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
