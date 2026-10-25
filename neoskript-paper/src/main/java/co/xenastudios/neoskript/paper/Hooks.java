/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Skript's plugin hooks ({@code ch.njol.skript.hooks}): syntax for Vault (economy, chat, permissions) and regions
 * plugins (WorldGuard, GriefPrevention), which only exists when they're installed and enabled, as in Skript.
 *
 * <p>Skript hooks in once the server has started, after every plugin has enabled and registered its Vault
 * services, and then loads scripts. NeoSkript registers the syntax as it enables, after the plugins it soft-depends
 * on, and when any of them is installed takes Vault's services and loads scripts once the server has started. A
 * hook's classes are only loaded when its plugin is there, so servers without them never need their APIs.
 */
public final class Hooks {

    /** The plugins hooked into, in Skript's order, which NeoSkript's plugin.yml soft-depends on. */
    private static final List<String> PLUGINS = List.of("Vault", "WorldGuard", "GriefPrevention");

    private Hooks() {}

    /** Whether any plugin NeoSkript hooks into is installed, so hooking (and loading scripts) waits for the server. */
    public static boolean anyInstalled() {
        for (String name : PLUGINS) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hooks into the installed regions plugins, and registers the syntax of each enabled plugin, for
     * {@link StandardLibrary#register(Registrar, java.util.function.Consumer)}. {@code version} is NeoSkript's, for
     * Skript's messages.
     *
     * <p>Vault's services may not be registered yet while NeoSkript enables, so its syntax is registered whenever
     * Vault is enabled, and {@link #hookServices} takes the services once the server has started. Elements of a
     * service with no provider then fail to parse, as Skript doesn't have them.
     */
    public static void register(Registrar registrar, String version, Logger logger) {
        if (enabled("Vault") != null) {
            VaultHook.register(registrar);
        }
        boolean regions = false;
        Plugin worldGuard = enabled("WorldGuard");
        if (worldGuard != null) {
            regions |= hookRegions(WorldGuardHook.hook(worldGuard, logger), worldGuard, logger);
        }
        Plugin griefPrevention = enabled("GriefPrevention");
        if (griefPrevention != null) {
            regions |= hookRegions(GriefPreventionHook.hook(griefPrevention, version, logger), griefPrevention, logger);
        }
        if (regions) {
            Regions.register(registrar);
        }
    }

    private static boolean hookRegions(Regions.RegionsPlugin hook, Plugin plugin, Logger logger) {
        if (hook == null) {
            logger.error("Could not hook into {}. This can happen if Skript doesn't support the installed version of {}",
                    plugin.getName(), plugin.getName());
            return false;
        }
        Regions.PLUGINS.add(hook);
        logger.warn(Regions.hookedWarning(hook.getName()));
        return true;
    }

    /**
     * Takes Vault's economy, chat and permission providers, as Skript's Vault hook does once the server has started,
     * before scripts load.
     */
    public static void hookServices(Logger logger) {
        Plugin vault = enabled("Vault");
        if (vault != null && !VaultHook.hook(Bukkit.getServicesManager())) {
            logger.error("Could not hook into {}. This can happen if Skript doesn't support the installed version of {}",
                    vault.getName(), vault.getName());
        }
    }

    /** Starts what hooks do while the server runs: region border events from players moving. */
    public static void start(Plugin plugin, EventPriority priority) {
        if (!Regions.PLUGINS.isEmpty()) {
            Regions.start(plugin, priority);
        }
    }

    private static Plugin enabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin == null || !plugin.isEnabled() ? null : plugin;
    }

    /**
     * The blocks of the box between two corners, as Skript's {@code AABB} iterates them: x fastest, then z, then
     * y, from the low corner to the high one.
     */
    static Iterator<Block> blocks(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        return new Iterator<>() {
            private int x = minX - 1; // next() increases x by one immediately
            private int y = minY;
            private int z = minZ;

            @Override
            public boolean hasNext() {
                return y <= maxY && (x != maxX || y != maxY || z != maxZ);
            }

            @Override
            public Block next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                x++;
                if (x > maxX) {
                    x = minX;
                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        y++;
                    }
                }
                return world.getBlockAt(x, y, z);
            }
        };
    }
}
