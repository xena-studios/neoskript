/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

    @Override
    public Runnable runGlobalRepeating(long ticks, Runnable task) {
        long period = Math.max(ticks, 1);
        return plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, scheduled -> task.run(), period, period)::cancel;
    }

    @Override
    public void runLater(Object owner, long ticks, Runnable task) {
        // Folia's delayed tasks wait at least a tick.
        long delay = Math.max(ticks, 1);
        if (owner instanceof Entity entity) {
            // If the entity is gone by then (a player who left), the trigger carries on on the global thread.
            if (entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), () -> runGlobal(task), delay) == null) {
                runGlobalLater(ticks, task);
            }
        } else if (owner instanceof Location location) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, location, scheduled -> task.run(), delay);
        } else {
            runGlobalLater(ticks, task);
        }
    }
}
