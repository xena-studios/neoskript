package co.xenastudios.neoskript.core.runtime;

/**
 * Schedules the continuation of a delayed trigger. Supplied by the platform layer (backed by the
 * Folia-safe scheduler) so core can suspend and resume a trigger across a {@code wait} without
 * depending on Bukkit.
 */
@FunctionalInterface
public interface DelayScheduler {

    /**
     * Runs {@code task} after a delay.
     *
     * @param task  the continuation to run
     * @param ticks the delay in server ticks
     */
    void runLater(Runnable task, long ticks);
}
