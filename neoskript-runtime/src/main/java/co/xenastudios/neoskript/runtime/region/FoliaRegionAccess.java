/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.region;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Folia's regions, through Paper's API: {@code isOwnedByCurrentRegion} and the entity and region schedulers. */
public final class FoliaRegionAccess implements RegionAccess {

    private final Plugin plugin;
    private final Server server;

    public FoliaRegionAccess(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    @Override
    public Kind kindOf(Class<?> type) {
        if (type == null || type == Object.class) {
            return Kind.NONE;
        }
        if (Entity.class.isAssignableFrom(type)) {
            return Kind.ENTITY;
        }
        if (Block.class.isAssignableFrom(type)) {
            return Kind.BLOCK;
        }
        return Location.class.isAssignableFrom(type) ? Kind.LOCATION : Kind.NONE;
    }

    @Override
    public Kind kind(Object value) {
        return switch (value) {
            case Entity entity -> Kind.ENTITY;
            case Block block -> Kind.BLOCK;
            case Location location when location.isWorldLoaded() -> Kind.LOCATION;
            case null, default -> Kind.NONE;
        };
    }

    @Override
    public boolean owns(Object value) {
        return switch (value) {
            case null -> server.isGlobalTickThread();
            case Entity entity -> server.isOwnedByCurrentRegion(entity);
            case Block block -> server.isOwnedByCurrentRegion(block.getLocation());
            case Location location when location.isWorldLoaded() -> server.isOwnedByCurrentRegion(location);
            default -> true;
        };
    }

    @Override
    public boolean dispatching(Object event) {
        return event instanceof org.bukkit.event.Event;
    }

    @Override
    public void execute(Object value, Runnable task) {
        switch (value) {
            // An entity that's gone (a player who left) has nothing left to change.
            case Entity entity -> entity.getScheduler().run(plugin, scheduled -> task.run(), null);
            case Block block -> server.getRegionScheduler().execute(plugin, block.getLocation(), task);
            case Location location when location.isWorldLoaded() -> server.getRegionScheduler().execute(plugin, location, task);
            case null, default -> server.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    @Override
    public String describe(Object value) {
        return switch (value) {
            case Player player -> "the player " + player.getName();
            case Entity entity -> "a " + entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            case Block block -> "the block at " + block.getX() + ", " + block.getY() + ", " + block.getZ()
                    + " in " + block.getWorld().getName();
            case Location location -> "the location at " + location.getBlockX() + ", " + location.getBlockY() + ", "
                    + location.getBlockZ();
            default -> String.valueOf(value);
        };
    }
}
