/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.Plugin;

/**
 * Events Skript fires itself, which NeoSkript fires as Bukkit events so triggers listen to them as to any
 * other: experience spawning (from blocks, deaths, bottles and fishing), the time of day passing in a world,
 * the real time of day passing, and Skript starting and stopping. Nothing is fired unless a trigger listens.
 */
public final class EventBridges {

    private EventBridges() {}

    /** Experience about to spawn, whatever drops it. Cancelling it, or setting it to 0, spawns none. */
    public static final class ExperienceSpawnEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Location location;
        private int experience;
        private boolean cancelled;

        ExperienceSpawnEvent(int experience, Location location) {
            this.experience = experience;
            this.location = location;
        }

        public int getSpawnedXP() {
            return experience;
        }

        public void setSpawnedXP(int experience) {
            this.experience = Math.max(0, experience);
        }

        public Location getLocation() {
            return location;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /**
     * A world's time of day passing from {@code from} (exclusive) to {@code to} (inclusive), in ticks, checked
     * every 10 ticks as Skript's {@code at} event does. Past midnight (06:00 in ticks) {@code from} is negative.
     */
    public static final class ScheduledEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final World world;
        private final int from;
        private final int to;
        private final boolean midnight;

        ScheduledEvent(World world, int from, int to, boolean midnight) {
            this.world = world;
            this.from = from;
            this.to = to;
            this.midnight = midnight;
        }

        public World getWorld() {
            return world;
        }

        /** Whether the world's time passed {@code ticks} (0 to 23999) since the last check. */
        public boolean passed(int ticks) {
            int time = midnight && ticks > 12000 ? ticks - 24000 : ticks;
            return time > from && time <= to;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** The real time of day reaching a minute: {@code at 14:30 in real time}. */
    public static final class RealTimeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int hour;
        private final int minute;

        RealTimeEvent(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }

        public int getHour() {
            return hour;
        }

        public int getMinute() {
            return minute;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** NeoSkript having started, with its scripts loaded: Skript's {@code on server start}. */
    public static final class SkriptStartEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** NeoSkript stopping: Skript's {@code on server stop}. */
    public static final class SkriptStopEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    private static final Listener LISTENER = new Listener() {};
    /** Each world's time when it was last checked for {@code at} events. */
    private static final Map<World, Integer> LAST_CHECKED = new ConcurrentHashMap<>();
    private static final int CHECK_PERIOD = 10;
    private static volatile int lastMinute = -1;

    /** Starts firing the bridged events, once NeoSkript's scripts have loaded. */
    public static void start(Plugin plugin, EventPriority priority) {
        for (Class<? extends Event> source : java.util.List.of(BlockExpEvent.class, EntityDeathEvent.class,
                ExpBottleEvent.class, PlayerFishEvent.class)) {
            Bukkit.getPluginManager().registerEvent(source, LISTENER, priority, (listener, event) -> {
                if (source.isInstance(event)) {
                    experience(event);
                }
            }, plugin, true);
        }
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> checkTimes(), 1, CHECK_PERIOD);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> checkRealTime(plugin), 1, 1, TimeUnit.SECONDS);
        // Skript starts its start event once the server is running.
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (SkriptStartEvent.getHandlerList().getRegisteredListeners().length > 0) {
                Bukkit.getPluginManager().callEvent(new SkriptStartEvent());
            }
        });
    }

    /** Fires the stop event, as NeoSkript is disabled. */
    public static void stop() {
        if (SkriptStopEvent.getHandlerList().getRegisteredListeners().length > 0) {
            Bukkit.getPluginManager().callEvent(new SkriptStopEvent());
        }
        HandlerList.unregisterAll(LISTENER);
    }

    /** Fires an experience spawn for experience something drops, and puts back what triggers left of it. */
    private static void experience(Event event) {
        if (ExperienceSpawnEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return;
        }
        ExperienceSpawnEvent spawn = switch (event) {
            case BlockExpEvent block -> new ExperienceSpawnEvent(block.getExpToDrop(),
                    block.getBlock().getLocation().add(0.5, 0.5, 0.5));
            case EntityDeathEvent death -> new ExperienceSpawnEvent(death.getDroppedExp(), death.getEntity().getLocation());
            case ExpBottleEvent bottle -> new ExperienceSpawnEvent(bottle.getExperience(), bottle.getEntity().getLocation());
            case PlayerFishEvent fish when fish.getState() == PlayerFishEvent.State.CAUGHT_FISH ->
                    new ExperienceSpawnEvent(fish.getExpToDrop(), fish.getPlayer().getLocation());
            default -> null;
        };
        if (spawn == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(spawn);
        int experience = spawn.isCancelled() ? 0 : spawn.getSpawnedXP();
        switch (event) {
            case BlockExpEvent block -> block.setExpToDrop(experience);
            case EntityDeathEvent death -> death.setDroppedExp(experience);
            case ExpBottleEvent bottle -> bottle.setExperience(experience);
            case PlayerFishEvent fish -> fish.setExpToDrop(experience);
            default -> {}
        }
    }

    /** Fires each world's time passing since the last check, as Skript's EvtAtTime checks it. */
    private static void checkTimes() {
        if (ScheduledEvent.getHandlerList().getRegisteredListeners().length == 0) {
            LAST_CHECKED.clear();
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            int now = (int) world.getTime();
            Integer last = LAST_CHECKED.get(world);
            int checked = last == null ? now - 1 : last;
            if (checked == now) {
                continue;
            }
            if (checked + CHECK_PERIOD * 2 < now || checked > now && checked - 24000 + CHECK_PERIOD * 2 < now) {
                checked = Math.floorMod(now - CHECK_PERIOD, 24000);
            }
            boolean midnight = checked > now;
            if (midnight) {
                checked -= 24000;
            }
            Bukkit.getPluginManager().callEvent(new ScheduledEvent(world, checked, now, midnight));
            LAST_CHECKED.put(world, now);
        }
    }

    /** Fires the real time once at each new minute, on the global thread. */
    private static void checkRealTime(Plugin plugin) {
        LocalDateTime now = LocalDateTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        if (minute == lastMinute) {
            return;
        }
        boolean first = lastMinute < 0;
        lastMinute = minute;
        if (first || RealTimeEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.getPluginManager().callEvent(new RealTimeEvent(now.getHour(), now.getMinute())));
    }
}
