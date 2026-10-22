/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.scheduler;

import org.bukkit.plugin.Plugin;

/** Folia: regionized schedulers. */
public final class FoliaScheduler implements Scheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runGlobalLater(long ticks, Runnable task) {
        if (ticks < 1) {
            runGlobal(task);
        } else {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), ticks);
        }
    }
}
