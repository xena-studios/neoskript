/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Skript's GriefPrevention hook ({@code ch.njol.skript.hooks.regions.GriefPreventionHook}): claims as regions,
 * named by their id. Only loaded when GriefPrevention is installed.
 */
final class GriefPreventionHook implements Regions.RegionsPlugin {

    private final GriefPrevention plugin;
    private final boolean supportsUUIDs;
    private final Method getClaim;
    private final Field claimsField;

    private GriefPreventionHook(GriefPrevention plugin, boolean supportsUUIDs, Method getClaim, Field claimsField) {
        this.plugin = plugin;
        this.supportsUUIDs = supportsUUIDs;
        this.getClaim = getClaim;
        this.claimsField = claimsField;
    }

    /** The hook, or {@code null} with Skript's error if this GriefPrevention can't be looked into. */
    static GriefPreventionHook hook(org.bukkit.plugin.Plugin plugin, String version, org.slf4j.Logger logger) {
        boolean supportsUUIDs;
        try {
            // ownerID is a public field
            Claim.class.getField("ownerID");
            supportsUUIDs = true;
        } catch (NoSuchFieldException e) {
            supportsUUIDs = false;
        }
        Method getClaim = null;
        try {
            getClaim = DataStore.class.getDeclaredMethod("getClaim", long.class);
            getClaim.setAccessible(true);
            if (!Claim.class.isAssignableFrom(getClaim.getReturnType())) {
                getClaim = null;
            }
        } catch (NoSuchMethodException | SecurityException e) {
            // Look through the claims instead.
        }
        Field claimsField = null;
        try {
            claimsField = DataStore.class.getDeclaredField("claims");
            claimsField.setAccessible(true);
            if (!List.class.isAssignableFrom(claimsField.getType())) {
                claimsField = null;
            }
        } catch (NoSuchFieldException | SecurityException e) {
            // Find claims by the method instead.
        }
        if (getClaim == null && claimsField == null) {
            logger.error("Skript {} is not compatible with GriefPrevention {}. Please report this at"
                    + " https://github.com/SkriptLang/Skript/issues/ if this error occurred after you updated"
                    + " GriefPrevention.", version, plugin.getPluginMeta().getVersion());
            return null;
        }
        return new GriefPreventionHook((GriefPrevention) plugin, supportsUUIDs, getClaim, claimsField);
    }

    private Claim getClaim(long id) {
        if (getClaim != null) {
            try {
                return (Claim) getClaim.invoke(plugin.dataStore, id);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                return null;
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        try {
            for (Object claim : (List<?>) claimsField.get(plugin.dataStore)) {
                if (claim instanceof Claim c && c.getID() == id) {
                    return c;
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // Not there.
        }
        return null;
    }

    @Override
    public String getName() {
        return "GriefPrevention";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return plugin.allowBuild(player, location) == null; // returns reason string if not allowed to build
    }

    @Override
    public Collection<? extends Regions.Region> getRegionsAt(Location location) {
        Claim claim = plugin.dataStore.getClaimAt(location, false, null);
        return claim == null ? Collections.emptySet() : List.of(new GriefPreventionRegion(claim));
    }

    @Override
    public Regions.Region getRegion(World world, String name) {
        try {
            Claim claim = getClaim(Long.parseLong(name));
            if (claim != null && world.equals(claim.getLesserBoundaryCorner().getWorld())) {
                return new GriefPreventionRegion(claim);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean hasMultipleOwners() {
        return false;
    }

    /** A claim, as Skript's {@code GriefPreventionRegion}. */
    private final class GriefPreventionRegion extends Regions.Region {
        private final Claim claim;

        GriefPreventionRegion(Claim claim) {
            this.claim = claim;
        }

        @Override
        boolean contains(Location location) {
            return claim.contains(location, false, false);
        }

        @Override
        boolean isMember(OfflinePlayer player) {
            return isOwner(player);
        }

        @Override
        Collection<OfflinePlayer> getMembers() {
            return getOwners();
        }

        @Override
        boolean isOwner(OfflinePlayer player) {
            String name = player.getName();
            // Assume no ownership when player has never visited server
            return name != null && name.equalsIgnoreCase(claim.getOwnerName());
        }

        @Override
        @SuppressWarnings("deprecation")
        Collection<OfflinePlayer> getOwners() {
            if (claim.isAdminClaim() || supportsUUIDs && claim.ownerID == null) { // Not all claims have owners!
                return Collections.emptyList();
            }
            return List.of(supportsUUIDs ? Bukkit.getOfflinePlayer(claim.ownerID)
                    : Bukkit.getOfflinePlayer(claim.getOwnerName()));
        }

        @Override
        Iterator<Block> getBlocks() {
            Location lower = claim.getLesserBoundaryCorner();
            Location upper = claim.getGreaterBoundaryCorner();
            if (lower == null || upper == null || lower.getWorld() == null || upper.getWorld() == null
                    || lower.getWorld() != upper.getWorld()) {
                return Collections.emptyIterator();
            }
            // Claims go all the way up, from the lower corner's height.
            return Hooks.blocks(lower.getWorld(), lower.getBlockX(), lower.getBlockY(), lower.getBlockZ(),
                    upper.getBlockX(), upper.getWorld().getMaxHeight() - 1, upper.getBlockZ());
        }

        @Override
        public String toString() {
            return "Claim #" + claim.getID();
        }

        @Override
        Regions.RegionsPlugin getPlugin() {
            return GriefPreventionHook.this;
        }

        @Override
        public boolean equals(Object other) {
            return other == this || other instanceof GriefPreventionRegion that && claim.equals(that.claim);
        }

        @Override
        public int hashCode() {
            return claim.hashCode();
        }
    }
}
