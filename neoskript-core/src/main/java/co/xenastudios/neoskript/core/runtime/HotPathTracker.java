package co.xenastudios.neoskript.core.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts how often each trigger runs and reports when one crosses a "hot" threshold. This identifies
 * triggers worth compiling once the bytecode {@link ExecutionEngine} lands; today it is purely
 * observational (the interpreter runs everything).
 */
public final class HotPathTracker {

    private final long threshold;
    private final Map<Object, AtomicLong> counts = new ConcurrentHashMap<>();

    public HotPathTracker(long threshold) {
        this.threshold = threshold;
    }

    /**
     * Records one execution of {@code key}.
     *
     * @param key the trigger (or other) key
     * @return {@code true} on the single execution that crosses the threshold, so callers can act
     *         once (e.g. log or schedule compilation)
     */
    public boolean recordAndCheckHot(Object key) {
        long count = counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        return count == threshold;
    }

    /**
     * @param key the key
     * @return how many times {@code key} has executed
     */
    public long count(Object key) {
        AtomicLong value = counts.get(key);
        return value == null ? 0 : value.get();
    }

    /** Clears all counts (used on reload). */
    public void reset() {
        counts.clear();
    }
}
