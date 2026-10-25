/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.scheduler;

import org.bukkit.plugin.Plugin;

/** Paper: everything on the main thread. */
public final class PaperScheduler implements Scheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void runGlobalLater(long ticks, Runnable task) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
    }

    @Override
    public Runnable runGlobalRepeating(long ticks, Runnable task) {
        long period = Math.max(ticks, 1);
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, period, period)::cancel;
    }

    @Override
    public Runnable runGlobalRepeating(long delay, long ticks, Runnable task) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, Math.max(delay, 0), Math.max(ticks, 1))::cancel;
    }
}
