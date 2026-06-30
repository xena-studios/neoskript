package co.xenastudios.neoskript.platform.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Default {@link NeoScheduler} built on Paper's region/global/async scheduler API. These schedulers
 * exist on both plain Paper and Folia, so this single implementation is Folia-safe.
 */
final class UnifiedScheduler implements NeoScheduler {

    private final Plugin plugin;

    UnifiedScheduler(Plugin plugin) {
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
}
