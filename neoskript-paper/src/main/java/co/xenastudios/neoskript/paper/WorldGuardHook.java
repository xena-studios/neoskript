/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Skript's WorldGuard hook ({@code ch.njol.skript.hooks.regions.WorldGuardHook}), for WorldGuard 7 with the
 * WorldEdit it needs. Only loaded when WorldGuard is installed.
 */
final class WorldGuardHook implements Regions.RegionsPlugin {

    private final WorldGuardPlugin plugin;

    private WorldGuardHook(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /** The hook, or {@code null} with Skript's error if the WorldEdit installed is too old. */
    static WorldGuardHook hook(org.bukkit.plugin.Plugin plugin, org.slf4j.Logger logger) {
        try {
            Class.forName("com.sk89q.worldedit.math.BlockVector3");
        } catch (ClassNotFoundException | LinkageError e) {
            logger.error("WorldEdit you're using is not compatible with Skript. Disabling WorldGuard support!");
            return null;
        }
        return new WorldGuardHook((WorldGuardPlugin) plugin);
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())) {
            return true; // Build access always granted by permission
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .testBuild(BukkitAdapter.adapt(location), plugin.wrapPlayer(player));
    }

    @Override
    public Collection<? extends Regions.Region> getRegionsAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptyList();
        }
        List<Regions.Region> regions = new ArrayList<>();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null) {
            return regions;
        }
        ApplicableRegionSet applicable = manager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
        if (applicable == null) {
            return regions;
        }
        for (ProtectedRegion region : applicable) {
            regions.add(new WorldGuardRegion(location.getWorld(), region));
        }
        return regions;
    }

    @Override
    public Regions.Region getRegion(World world, String name) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return null;
        }
        ProtectedRegion region = manager.getRegion(name);
        return region == null ? null : new WorldGuardRegion(world, region);
    }

    @Override
    public boolean hasMultipleOwners() {
        return true;
    }

    /** A WorldGuard region in a world, as Skript's {@code WorldGuardRegion}. */
    private final class WorldGuardRegion extends Regions.Region {
        private final World world;
        private final ProtectedRegion region;

        WorldGuardRegion(World world, ProtectedRegion region) {
            this.world = world;
            this.region = region;
        }

        @Override
        boolean contains(Location location) {
            return location.getWorld().equals(world)
                    && region.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        @Override
        boolean isMember(OfflinePlayer player) {
            return region.isMember(plugin.wrapOfflinePlayer(player));
        }

        @Override
        Collection<OfflinePlayer> getMembers() {
            return players(region.getMembers().getUniqueIds());
        }

        @Override
        boolean isOwner(OfflinePlayer player) {
            return region.isOwner(plugin.wrapOfflinePlayer(player));
        }

        @Override
        Collection<OfflinePlayer> getOwners() {
            return players(region.getOwners().getUniqueIds());
        }

        private static Collection<OfflinePlayer> players(Collection<UUID> ids) {
            List<OfflinePlayer> players = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                players.add(Bukkit.getOfflinePlayer(id));
            }
            return players;
        }

        @Override
        @SuppressWarnings("removal")
        Iterator<Block> getBlocks() {
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            // getBlockX(), not x(), which WorldEdit before 7.3 doesn't have.
            return Hooks.blocks(world, min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX(),
                    max.getBlockY(), max.getBlockZ());
        }

        @Override
        public String toString() {
            return region.getId() + " in world " + world.getName();
        }

        @Override
        Regions.RegionsPlugin getPlugin() {
            return WorldGuardHook.this;
        }

        @Override
        public boolean equals(Object other) {
            return other == this || other instanceof WorldGuardRegion that && world.equals(that.world)
                    && region.equals(that.region);
        }

        @Override
        public int hashCode() {
            return world.hashCode() * 31 + region.hashCode();
        }
    }
}
