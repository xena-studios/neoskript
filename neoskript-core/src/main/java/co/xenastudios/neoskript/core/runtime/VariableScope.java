package co.xenastudios.neoskript.core.runtime;

/**
 * Implemented by execution contexts that can run a compound variable mutation atomically with
 * respect to other contexts sharing the same globals. Needed because, on Folia, event handlers run
 * concurrently on multiple region threads, so read-modify-write operations on shared global
 * variables (e.g. {@code add 1 to {counter}}) must be serialized to avoid lost updates.
 *
 * <p>This is a core-internal capability (not part of the public {@code TriggerContext} SPI); the
 * built-in effects detect it with {@code instanceof}.
 */
public interface VariableScope {

    /**
     * Runs {@code action} while holding the shared global lock.
     *
     * @param action the mutation to run atomically
     */
    void runAtomic(Runnable action);
}
