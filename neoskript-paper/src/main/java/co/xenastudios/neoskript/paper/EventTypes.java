/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The types events give values of, which Skript names in its English language file: heal reasons, teleport
 * causes, spawn reasons, weather types, fishing states...
 */
final class EventTypes {

    private EventTypes() {}

    /** Skript's weather types, which say what a world's (or a player's) weather is. */
    enum Weather {
        CLEAR, RAIN, THUNDER;

        /** Whether this is the weather with rain and thunder as given. */
        boolean is(boolean rain, boolean thunder) {
            return switch (this) {
                case CLEAR -> !thunder && !rain;
                case RAIN -> !thunder && rain;
                case THUNDER -> thunder && rain;
            };
        }
    }

    static void register(Registrar registrar) {
        enumType(registrar, "healreason", "heal reason", org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.class, """
                custom: unknown, custom, plugin, a plugin
                eating: eating, consuming, ingesting
                ender_crystal: end crystal, ender crystal, an end crystal, an ender crystal
                magic: magic, potion, a potion
                magic_regen: magic regen, magic regeneration, a magic regen, a magic regeneration, a healing potion, a regeneration potion, healing potion, regeneration potion, regen potion, a regen potion
                regen: peaceful regeneration, peaceful regen, peaceful
                satiated: satiated, satisfied, fed, sated
                wither: withering, withered, wither potion, a wither effect, wither effect, wither
                wither_spawn: wither spawning, wither spawn, wither summoning, a wither summoning, a wither spawn, a wither spawning
                """, "regen reason", "heal cause", "regen cause");
        enumType(registrar, "teleportcause", "teleport cause", org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class, """
                chorus_fruit: chorus, chorus fruit
                command: command
                end_gateway: gateway, end gateway, ender gateway
                end_portal: end portal, ender portal
                ender_pearl: ender pearl
                nether_portal: nether portal
                plugin: plugin
                spectate: spectate, spectator
                unknown: unknown
                dismount: dismount, dismounted
                exit_bed: exit bed, exiting bed, bed exit
                consumable_effect: consumable effect
                """, "teleport reason", "teleport type");
        enumType(registrar, "spawnreason", "spawn reason", org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class, """
                bed: bed
                beehive: beehive
                breeding: breed, breeding
                bucket: release from bucket
                build_irongolem: built iron golem, build iron golem
                build_snowman: built snowman, build snowman
                build_wither: built wither, build wither
                chunk_gen: chunk generation
                command: command
                cured: cured
                custom: customized, customised, custom
                default: default
                dispense_egg: dispense egg, dispensing egg
                drowned: drowned
                duplication: duplication
                egg: egg
                ender_pearl: ender pearl
                explosion: explosion
                frozen: frozen
                infection: infection, infected
                jockey: jockey
                lightning: lightning
                metamorphosis: metamorphosis
                mount: mount
                natural: natural
                nether_portal: nether portal
                ocelot_baby: ocelot baby
                ominous_item_spawner: ominous item spawner
                patrol: patrol
                piglin_zombified: piglin zombification
                potion_effect: potion effect
                raid: raid
                reinforcements: reinforcements
                sheared: shear, sheared
                shoulder_entity: perching, shoulder
                silverfish_block: silverfish reveal, silverfish trap
                slime_split: slime split
                spawner: mob spawner, creature spawner, spawner
                spawner_egg: spawn egg
                spell: spell
                trap: trap
                village_defense: village defense, golem defense, iron golem defense
                village_invasion: village invasion, village invading
                trial_spawner: trial spawner, trial mob spawner, trial creature spawner
                enchantment: enchantment
                rehydration: rehydration, rehydrated
                build_coppergolem: build copper golem, built copper golem
                reanimate: reanimate
                """, "spawning reason");
        enumType(registrar, "respawnreason", "respawn reason", org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.class, """
                death: death
                end_portal: end portal
                plugin: plugin
                """, "respawning reason");
        enumType(registrar, "transformreason", "transform reason", org.bukkit.event.entity.EntityTransformEvent.TransformReason.class, """
                cured: zombie cure, zombie curing, curing
                drowned: entity drowning, drowning, zombie converting to drowned, zombie drowning
                frozen: entity freezing, entity freeze, skeleton freezing, skeleton freeze, skeleton converting to stray
                lightning: lightning, creeper super charge
                metamorphosis: tadpole converting, tadpole converting to frog, tadpole metamorphosis, metamorphosis
                piglin_zombified: piglin zombification
                sheared: mooshroom shear, mooshroom shearing
                split: slime split, slime splitting, split, magma slime split, magma slime splitting
                unknown: unknown
                infection: infection, villager infection
                """, "entity transform reason", "transform cause", "entity transform cause");
        enumType(registrar, "unleashreason", "unleash reason", org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.class, """
                distance: distance
                holder_gone: holder (gone|disappeared)
                leashed_gone: leashed (gone|disappeared)
                player_unleash: player unleash, player unleashed, unleashed by player
                unknown: unknown
                """, "unleash cause");
        enumType(registrar, "resourcepackstate", "resource pack state", org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.class, """
                accepted: accept, accepted
                declined: decline, refuse, reject, declined, refused, rejected
                failed_download: download fail, fail, failed to download, failed
                successfully_loaded: successfully load, successfully install, success, successfully loaded, successfully installed
                downloaded: downloaded
                invalid_url: invalid url
                failed_reload: failed reload, failed to reload
                discarded: discarded
                """, "resourcepack state", "resource packstate", "resourcepackstate");
        enumType(registrar, "inventoryaction", "inventory action", org.bukkit.event.inventory.InventoryAction.class, """
                nothing: nothing, do nothing
                pickup_all: pickup all, pickup all items
                pickup_some: pickup some, pickup some items
                pickup_half: pickup half, pickup half stack
                pickup_one: pickup one item, pickup single item, pickup single
                place_all: place all, place all items
                place_some: place some, place some items
                place_one: place one, place one item
                swap_with_cursor: swap with cursor, swap stack with cursor, swap cursor, swap cursor stack
                drop_all_cursor: drop all from cursor, drop stack from cursor, drop items from cursor, drop stack from cursor, drop cursor, drop cursor stack
                drop_one_cursor: drop one from cursor, drop one item from cursor, drop single item from cursor, drop cursor item
                drop_all_slot: drop all from slot, drop stack from slot, drop items from slot, drop stack from slot, drop slot, drop slot stack
                drop_one_slot: drop one from slot, drop one item from slot, drop single item from slot, drop slot item
                move_to_other_inventory: instant move, move to other inventory, shift move
                hotbar_move_and_readd: hotbar move and readd
                hotbar_swap: swap with hotbar, swap items with hotbar, hotbar swap, hotbar swap items
                clone_stack: clone stack
                collect_to_cursor: collect to cursor, collect items to cursor
                unknown: unknown, unsupported, custom
                pickup_from_bundle: pickup from bundle, pickup items from bundle
                pickup_all_into_bundle: pickup all into bundle, pickup all items into bundle
                pickup_some_into_bundle: pickup some into bundle, pickup some items into bundle
                place_from_bundle: place from bundle, place items from bundle
                place_all_into_bundle: place all into bundle, place all items into bundle
                place_some_into_bundle: place some into bundle, place some items into bundle
                """, "inventoryaction");
        enumType(registrar, "clicktype", "click type", org.bukkit.event.inventory.ClickType.class, """
                left: left mouse button, left mouse, lmb
                shift_left: left mouse button with shift, left mouse with shift, shift+lmb
                right: right mouse button, right mouse, rmb
                shift_right: right mouse button with shift, right mouse with shift, shift+rmb
                window_border_left: window border using right mouse button, window border using right mouse, border using lmb
                window_border_right: window border using left mouse button, window border using right mouse, border using rmb
                middle: middle mouse button, middle mouse, mmb
                number_key: number key, 0-9
                double_click: double click using mouse, double click
                drop: drop key, drop item, q
                control_drop: drop key with control, drop stack, ctrl+q
                creative: creative action
                unknown: unknown, unsupported, custom
                swap_offhand: swap offhand, swap shield
                """, "clicktype");
        enumType(registrar, "inventoryclosereason", "inventory close reason", org.bukkit.event.inventory.InventoryCloseEvent.Reason.class, """
                unknown: unknown
                teleport: teleport
                cant_use: can't use, can not use, cannot use
                unloaded: unloaded
                open_new: open new, new opened
                player: player
                disconnect: disconnected, disconnect
                death: death
                plugin: plugin
                """, "inventoryclose reason", "inventory closereason", "inventoryclosereason");
        enumType(registrar, "quitreason", "quit reason", org.bukkit.event.player.PlayerQuitEvent.QuitReason.class, """
                disconnected: disconnected, quit
                erroneous_state: erroneous, erroneous state
                kicked: kicked
                timed_out: timed out
                """, "disconnect reason", "quit cause", "disconnect cause");
        enumType(registrar, "fishingstate", "fishing state", org.bukkit.event.player.PlayerFishEvent.State.class, """
                fishing: fishing line cast, rod cast, fishing rod cast, fishing
                caught_fish: fish caught, caught fish
                caught_entity: entity caught, caught entity
                in_ground: bobber in ground, in ground
                failed_attempt: fish escape, fish escaped, failed attempt
                reel_in: reel in
                bite: bite, fish bite
                lured: lured, fish lured
                """, "fishingstate");
        enumType(registrar, "potioncause", "potion effect cause", org.bukkit.event.entity.EntityPotionEffectEvent.Cause.class, """
                area_effect_cloud: enter area effect cloud
                arrow: arrow infliction
                attack: attack
                axolotl: axolotl
                warden: warden
                beacon: beacon effect
                command: command
                conduit: conduit effect
                conversion: conversion, converted
                death: death
                dolphin: dolphin boost
                expiration: expiration, expired
                food: food
                illusion: illusion
                milk: drinking milk
                plugin: plugin
                potion_drink: potion drunk, drinking potion
                potion_splash: potion splash, splash potion
                spider_spawn: spider spawn, spawned spider
                totem: removal by resurrection
                turtle_helmet: turtle helmet effect
                unknown: unknown
                villager_trade: villager trade
                patrol_captain: pillager captain, patrol captain
                wither_rose: wither rose infliction
                nautilus: nautilus
                """, "entity potion effect cause", "potioneffect cause", "potion effectcause", "potioneffectcause",
                "entity potioneffect cause");
        enumType(registrar, "potionaction", "potion effect action", org.bukkit.event.entity.EntityPotionEffectEvent.Action.class, """
                added: added, add
                changed: changed, change
                cleared: cleared, clear
                removed: removed, remove, removal
                """, "entity potion effect action", "potioneffect action", "potion effectaction", "potioneffectaction",
                "entity potioneffect action");
        enumType(registrar, "experiencecooldownchangereason", "experience cooldown change reason",
                org.bukkit.event.player.PlayerExpCooldownChangeEvent.ChangeReason.class, """
                plugin: plugin
                pickup_orb: orb pickup, pickup orb
                """, "xp cooldown change reason", "exp cooldown change reason", "experience cooldown change cause",
                "xp cooldown change cause", "exp cooldown change cause");
        enumType(registrar, "villagercareerchangereason", "villager career change reason",
                org.bukkit.event.entity.VillagerCareerChangeEvent.ChangeReason.class, """
                employed: employment
                losing_job: losing job
                """, "career change reason", "villager careerchange reason", "careerchange reason");
        enumType(registrar, "fireworktype", "firework type", org.bukkit.FireworkEffect.Type.class, """
                ball_large: large ball, ball large, large
                creeper: creeper, creeper face
                ball: ball, small, small ball
                star: star, star shaped
                burst: burst
                """, "fireworktype");

        // Weather, which Skript names itself rather than by Bukkit's two states.
        Map<String, Weather> weathers = new LinkedHashMap<>();
        for (String name : List.of("clear", "sun", "sunny")) {
            weathers.put(name, Weather.CLEAR);
        }
        for (String name : List.of("rain", "rainy", "raining")) {
            weathers.put(name, Weather.RAIN);
        }
        for (String name : List.of("thunder", "thundering", "thunderstorm")) {
            weathers.put(name, Weather.THUNDER);
        }
        registrar.namedType("weathertype", "weather type", Weather.class, weathers);
        registrar.alias("weathertype", "weather condition", "weather conditions");
        registrar.alias("weathertype", "weather", "weathers");
        registrar.text(Weather.class, weather -> weather.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Registers {@code type}'s constants as a named type, by the names Skript's language file gives them, one line
     * per constant ({@code eating: eating, consuming}). A name may have choices ({@code holder (gone|disappeared)}).
     * Constants this server doesn't have are left out; ones the names don't cover are named after the constant.
     * Each value reads as its first name.
     */
    static <E extends Enum<E>> void enumType(Registrar registrar, String id, String name, Class<E> type, String lang,
            String... aliases) {
        E[] constants = type.getEnumConstants();
        if (constants == null) {
            return;
        }
        Map<String, E> byConstant = new LinkedHashMap<>();
        for (E constant : constants) {
            byConstant.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        Map<String, E> values = new LinkedHashMap<>();
        Map<E, String> texts = new java.util.HashMap<>();
        for (String line : lang.strip().split("\n")) {
            int colon = line.indexOf(':');
            E constant = byConstant.get(line.substring(0, colon).strip());
            if (constant == null) {
                continue;
            }
            for (String names : line.substring(colon + 1).split(",")) {
                for (String each : expand(names.strip().toLowerCase(Locale.ROOT))) {
                    values.putIfAbsent(each, constant);
                    texts.putIfAbsent(constant, each);
                }
            }
        }
        for (Map.Entry<String, E> constant : byConstant.entrySet()) {
            String fallback = constant.getKey().replace('_', ' ');
            values.putIfAbsent(fallback, constant.getValue());
            texts.putIfAbsent(constant.getValue(), fallback);
        }
        registrar.namedType(id, name, type, values);
        for (String alias : aliases) {
            registrar.alias(id, alias, alias + "s");
        }
        registrar.text(type, value -> texts.getOrDefault(value, value.name().toLowerCase(Locale.ROOT)));
    }

    /** The names {@code pattern} stands for, choosing each {@code (a|b)} group's alternatives. */
    private static List<String> expand(String pattern) {
        int open = pattern.indexOf('(');
        if (open < 0) {
            return List.of(pattern);
        }
        int close = pattern.indexOf(')', open);
        List<String> names = new ArrayList<>();
        for (String choice : pattern.substring(open + 1, close).split("\\|")) {
            names.addAll(expand(pattern.substring(0, open) + choice + pattern.substring(close + 1)));
        }
        return names;
    }
}
