package co.xenastudios.neoskript.platform.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * NeoSkript's scheduling facade. It hides the difference between regular Paper and Folia so that
 * script delays, waits, and async sections behave identically on both.
 *
 * <p>The default implementation is built on Paper's region/global/async scheduler API, which is
 * present on both plain Paper and Folia, giving Folia-safe behaviour without a separate dependency.
 * Later phases extend this with entity- and location-scoped scheduling so effects can run on the
 * correct region thread.
 */
public interface NeoScheduler {

    /**
     * Runs a task on the global region thread (the closest equivalent to "the main thread" that is
     * valid on Folia).
     *
     * @param task the task to run
     */
    void runGlobal(Runnable task);

    /**
     * Runs a task on the global region thread after a delay.
     *
     * @param task       the task to run
     * @param delayTicks the delay in server ticks (clamped to a minimum of 1)
     */
    void runGlobalLater(Runnable task, long delayTicks);

    /**
     * Runs a task off the main thread on the async scheduler.
     *
     * @param task the task to run
     */
    void runAsync(Runnable task);

    /**
     * Runs a task repeatedly on the global region thread.
     *
     * @param task             the task to run
     * @param initialDelayTicks delay before the first run, in ticks (clamped to a minimum of 1)
     * @param periodTicks       interval between runs, in ticks (clamped to a minimum of 1)
     * @return a handle to cancel the repeating task
     */
    TaskHandle runRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    /**
     * Creates the default scheduler for the given plugin.
     *
     * @param plugin the owning plugin
     * @return a Folia-safe scheduler
     */
    static NeoScheduler create(Plugin plugin) {
        return new UnifiedScheduler(plugin);
    }
}
