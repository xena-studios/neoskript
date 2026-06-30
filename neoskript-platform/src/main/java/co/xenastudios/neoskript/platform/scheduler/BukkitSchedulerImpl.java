package co.xenastudios.neoskript.platform.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * {@link NeoScheduler} for non-Folia servers, built on the standard {@link org.bukkit.scheduler.BukkitScheduler}.
 * Used on plain Paper (and in tests under MockBukkit, which implements the Bukkit scheduler).
 */
final class BukkitSchedulerImpl implements NeoScheduler {

    private final Plugin plugin;

    BukkitSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public TaskHandle runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        var scheduledTask = Bukkit.getScheduler()
                .runTaskTimer(plugin, task, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        return scheduledTask::cancel;
    }
}
