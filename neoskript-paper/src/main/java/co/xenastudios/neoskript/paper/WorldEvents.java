/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.TimeOfDay;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.weather.WeatherEvent;
import org.bukkit.event.world.ChunkEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * World events: saving, loading and unloading worlds and chunks, weather, portals, fireworks and world borders;
 * and the events NeoSkript fires itself (see {@link EventBridges}): times of day, experience spawning, and
 * starting and stopping.
 */
final class WorldEvents {

    private WorldEvents() {}

    static void register(Registrar registrar) {
        registerWorlds(registrar);
        registerWeather(registrar);
        registerBorders(registrar);
        registerBridged(registrar);
    }

    private static void registerWorlds(Registrar registrar) {
        String of = " [of %-worlds%]";
        registerWorld(registrar, "EvtWorld", WorldSaveEvent.class, "world sav(e|ing)" + of);
        registerWorld(registrar, "EvtWorldInit", WorldInitEvent.class, "world init[ialization]" + of);
        registerWorld(registrar, "EvtWorldUnload", WorldUnloadEvent.class, "world unload[ing]" + of);
        registerWorld(registrar, "EvtWorldLoad", WorldLoadEvent.class, "world load[ing]" + of);
        Events.simple(registrar, "EvtChunkLoad", ChunkLoadEvent.class, "chunk load[ing]");
        Events.simple(registrar, "EvtChunkGenerate", ChunkPopulateEvent.class, "chunk (generat|populat)(e|ing)");
        Events.simple(registrar, "EvtChunkUnload", ChunkUnloadEvent.class, "chunk unload[ing]");
        Events.simple(registrar, "EvtSpawnChange", SpawnChangeEvent.class, "[world] spawn change");
        Events.simple(registrar, "EvtPortalCreate", PortalCreateEvent.class, "portal creat(e|ion)");

        registrar.eventValue(WorldEvent.class, "world", WorldEvent::getWorld);
        registrar.eventValue(ChunkEvent.class, "chunk", ChunkEvent::getChunk);
        registrar.eventValue(PortalCreateEvent.class, "block", Block[].class, 0, event -> event.getBlocks().stream()
                .map(BlockState::getBlock).toArray(Block[]::new));
        registrar.eventValue(PortalCreateEvent.class, "entity", PortalCreateEvent::getEntity);
    }

    /** A world event, of the worlds written if any. */
    private static void registerWorld(Registrar registrar, String id, Class<? extends WorldEvent> event, String pattern) {
        registrar.event(id, List.of(event), Events.optional(0, (fired, worlds) ->
                Events.any(worlds, world -> world.equals(((WorldEvent) fired).getWorld()))), pattern);
    }

    /** Weather changes, to kinds of weather; and fireworks, of colours. */
    private static void registerWeather(Registrar registrar) {
        registrar.event("EvtWeatherChange", List.of(WeatherChangeEvent.class, ThunderChangeEvent.class),
                Events.optional(0, (event, types) -> {
                    boolean rain = event instanceof WeatherChangeEvent weather ? weather.toWeatherState()
                            : ((ThunderChangeEvent) event).getWorld().hasStorm();
                    boolean thunder = event instanceof ThunderChangeEvent change ? change.toThunderState()
                            : ((WeatherChangeEvent) event).getWorld().isThundering();
                    return Events.any(types, type -> type instanceof WorldProperties.Weather weather && is(weather, rain, thunder));
                }), "weather change [to %-weathertypes%]");
        registrar.eventValue(WeatherEvent.class, "world", WeatherEvent::getWorld);

        registrar.event("EvtFirework", List.of(FireworkExplodeEvent.class), info -> (context, args) -> {
            Object[] colours = Events.values(args[0]);
            if (colours == null) {
                return true;
            }
            java.util.Set<org.bukkit.Color> wanted = new java.util.HashSet<>();
            for (Object colour : colours) {
                if (colour instanceof Colors.Colour c) {
                    wanted.add(firework(c));
                }
            }
            for (FireworkEffect effect : ((FireworkExplodeEvent) context.event()).getEntity().getFireworkMeta().getEffects()) {
                if (wanted.containsAll(effect.getColors())) {
                    return true;
                }
            }
            return false;
        }, "[a] firework explo(d(e|ing)|sion) [colo[u]red %-colors%]");
        registrar.eventValue(FireworkExplodeEvent.class, "entity", org.bukkit.entity.Firework.class, 0,
                FireworkExplodeEvent::getEntity);
        registrar.eventValue(FireworkExplodeEvent.class, "color", Colors.Colour[].class, 0, event -> {
            List<Colors.Colour> found = new java.util.ArrayList<>();
            for (FireworkEffect effect : event.getEntity().getFireworkMeta().getEffects()) {
                for (org.bukkit.Color color : effect.getColors()) {
                    found.add(colour(color));
                }
            }
            return found.isEmpty() ? null : found.toArray(Colors.Colour[]::new);
        });
    }

    /** The colour a firework shows for a colour written, as Skript's: a named colour's dye's, or the colour itself. */
    private static org.bukkit.Color firework(Colors.Colour colour) {
        if (!(colour instanceof Colors.Named named)) {
            return org.bukkit.Color.fromARGB(colour.alpha(), colour.red(), colour.green(), colour.blue());
        }
        org.bukkit.DyeColor dye = switch (named) {
            case BLACK -> org.bukkit.DyeColor.BLACK;
            case DARK_GREY -> org.bukkit.DyeColor.GRAY;
            case LIGHT_GREY -> org.bukkit.DyeColor.LIGHT_GRAY;
            case WHITE -> org.bukkit.DyeColor.WHITE;
            case DARK_BLUE -> org.bukkit.DyeColor.BLUE;
            case BROWN -> org.bukkit.DyeColor.BROWN;
            case DARK_CYAN -> org.bukkit.DyeColor.CYAN;
            case LIGHT_CYAN -> org.bukkit.DyeColor.LIGHT_BLUE;
            case DARK_GREEN -> org.bukkit.DyeColor.GREEN;
            case LIGHT_GREEN -> org.bukkit.DyeColor.LIME;
            case YELLOW -> org.bukkit.DyeColor.YELLOW;
            case ORANGE -> org.bukkit.DyeColor.ORANGE;
            case DARK_RED -> org.bukkit.DyeColor.RED;
            case LIGHT_RED -> org.bukkit.DyeColor.PINK;
            case DARK_PURPLE -> org.bukkit.DyeColor.PURPLE;
            case LIGHT_PURPLE -> org.bukkit.DyeColor.MAGENTA;
        };
        return dye.getFireworkColor();
    }

    /** A firework's colour as a colour: the named one whose dye shows it, else as it is. */
    private static Colors.Colour colour(org.bukkit.Color color) {
        for (Colors.Named named : Colors.Named.values()) {
            if (firework(named).equals(color)) {
                return named;
            }
        }
        return Displays.colour(color);
    }

    /** Whether {@code weather} is the weather with rain and thunder as given, as Skript's WeatherType.isWeather. */
    private static boolean is(WorldProperties.Weather weather, boolean rain, boolean thunder) {
        return switch (weather) {
            case CLEAR -> !thunder && !rain;
            case RAIN -> !thunder && rain;
            case THUNDER -> thunder && rain;
        };
    }

    /** The world border events Paper fires. */
    private static void registerBorders(Registrar registrar) {
        var bounds = io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.class;
        var finish = io.papermc.paper.event.world.border.WorldBorderBoundsChangeFinishEvent.class;
        var center = io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent.class;
        Events.simple(registrar, "EvtWorldBorderBoundsChange", bounds, "world[ ]border [bounds] chang(e|ing)");
        Events.simple(registrar, "EvtWorldBorderBoundsFinishChange", finish, "world[ ]border [bounds] finish chang(e|ing)");
        Events.simple(registrar, "EvtWorldBorderCenterChange", center, "world[ ]border center chang(e|ing)");
        var border = io.papermc.paper.event.world.border.WorldBorderEvent.class;
        registrar.eventValue(border, "worldborder", io.papermc.paper.event.world.border.WorldBorderEvent::getWorldBorder);
        registrar.eventValue(bounds, "number", io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent::getNewSize);
        registrar.eventValue(bounds, "number", -1, io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent::getOldSize);
        registrar.eventValue(bounds, "timespan", event -> new Timespan(event.getDuration()));
        registrar.eventValue(finish, "number", io.papermc.paper.event.world.border.WorldBorderBoundsChangeFinishEvent::getNewSize);
        registrar.eventValue(finish, "number", -1, io.papermc.paper.event.world.border.WorldBorderBoundsChangeFinishEvent::getOldSize);
        registrar.eventValue(finish, "timespan", event -> new Timespan((long) event.getDuration()));
        registrar.eventValue(center, "location", io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent::getNewCenter);
        registrar.eventValue(center, "location", -1, io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent::getOldCenter);
    }

    /** The events {@link EventBridges} fires: times of day, experience spawning, starting and stopping. */
    private static void registerBridged(Registrar registrar) {
        registrar.event("EvtAtTime", List.of(EventBridges.ScheduledEvent.class), info -> (context, args) -> {
            EventBridges.ScheduledEvent event = (EventBridges.ScheduledEvent) context.event();
            Object[] worlds = Events.values(args[1]);
            return args[0] instanceof TimeOfDay time && event.passed(time.ticks())
                    && (worlds == null || Events.any(worlds, world -> world.equals(event.getWorld())));
        }, "at %time% [in %worlds%]");
        registrar.eventValue(EventBridges.ScheduledEvent.class, "world", EventBridges.ScheduledEvent::getWorld);

        registrar.event("EvtRealTime", List.of(EventBridges.RealTimeEvent.class), info -> (context, args) -> {
            EventBridges.RealTimeEvent event = (EventBridges.RealTimeEvent) context.event();
            return Events.any(Events.values(args[0]), value -> {
                if (!(value instanceof TimeOfDay time)) {
                    return false;
                }
                // A time's ticks count from 06:00, 1000 to the hour.
                int afterMidnight = time.ticksAfterMidnight();
                int hour = afterMidnight / 1000;
                int minute = (int) Math.floor((afterMidnight % 1000) * 60 / 1000.0);
                return hour == event.getHour() && minute == event.getMinute();
            });
        }, "at %times% [in] real time");

        registrar.event("EvtExperienceSpawn", List.of(EventBridges.ExperienceSpawnEvent.class), null,
                "[e]xp[erience] [orb] spawn", "spawn of [a[n]] [e]xp[erience] [orb]");
        registrar.eventValue(EventBridges.ExperienceSpawnEvent.class, "location", EventBridges.ExperienceSpawnEvent::getLocation);
        registrar.eventValue(EventBridges.ExperienceSpawnEvent.class, "experience",
                event -> new Experience(event.getSpawnedXP()));

        registrar.event("EvtSkript", List.of(EventBridges.SkriptStartEvent.class, EventBridges.SkriptStopEvent.class),
                info -> Events.narrowed(List.of(info.pattern() == 0 ? EventBridges.SkriptStartEvent.class
                        : EventBridges.SkriptStopEvent.class), (context, args) -> true),
                "(:server|skript) (start|load|enable)", "(:server|skript) (stop|unload|disable)");
        registrar.eventValue(EventBridges.SkriptStartEvent.class, "commandsender", event -> Bukkit.getConsoleSender());
        registrar.eventValue(EventBridges.SkriptStopEvent.class, "commandsender", event -> Bukkit.getConsoleSender());
    }
}
