package co.xenastudios.neoskript.core.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lightweight execution profiler. When enabled it accumulates, per profiled key (e.g. a trigger),
 * an invocation count and total execution time, and can report the hottest keys. Disabled by default
 * so it adds no overhead to normal execution.
 */
public final class Profiler {

    /** A single profiled key's aggregated timing. */
    public record Sample(String key, long count, long totalNanos) {
        /** @return mean nanoseconds per invocation */
        public long averageNanos() {
            return count == 0 ? 0 : totalNanos / count;
        }
    }

    private static final class Stat {
        final AtomicLong count = new AtomicLong();
        final AtomicLong totalNanos = new AtomicLong();
    }

    private volatile boolean enabled;
    private final Map<String, Stat> stats = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Records one timed invocation of {@code key}. No-op unless {@linkplain #isEnabled() enabled}.
     *
     * @param key   the profiled key
     * @param nanos the elapsed time in nanoseconds
     */
    public void record(String key, long nanos) {
        if (!enabled) {
            return;
        }
        Stat stat = stats.computeIfAbsent(key, k -> new Stat());
        stat.count.incrementAndGet();
        stat.totalNanos.addAndGet(nanos);
    }

    /** Clears all accumulated samples. */
    public void reset() {
        stats.clear();
    }

    /**
     * @param limit the maximum number of samples to return
     * @return the hottest samples by total time, descending
     */
    public List<Sample> top(int limit) {
        return stats.entrySet().stream()
                .map(e -> new Sample(e.getKey(), e.getValue().count.get(), e.getValue().totalNanos.get()))
                .sorted(Comparator.comparingLong(Sample::totalNanos).reversed())
                .limit(limit)
                .toList();
    }
}
