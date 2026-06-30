package co.xenastudios.neoskript.platform.scheduler;

/**
 * A handle to a scheduled task that can be cancelled, e.g. to stop a periodic trigger on reload.
 */
@FunctionalInterface
public interface TaskHandle {

    /** Cancels the task. Safe to call more than once. */
    void cancel();
}
