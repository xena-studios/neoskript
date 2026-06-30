package co.xenastudios.neoskript.platform.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * {@link NeoScheduler} for Folia, built on Paper's region/global/async scheduler API.
 */
final class FoliaScheduler implements NeoScheduler {

    private final Plugin plugin;

    FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayTicks));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public TaskHandle runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        var scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, scheduled -> task.run(), Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        return scheduledTask::cancel;
    }
}
