package co.xenastudios.neoskript.core.runtime;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A duration value, stored as server ticks (20 ticks per second). Parses and renders human-friendly
 * forms ({@code 5 seconds}, {@code 2 minutes}, {@code 20 ticks}); used both as the {@code %timespan%}
 * value type and by the {@code every <timespan>:} periodic trigger (via {@link #parseTicks}).
 */
public final class Timespan implements Comparable<Timespan> {

    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(ticks?|seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)",
            Pattern.CASE_INSENSITIVE);

    private final long ticks;

    private Timespan(long ticks) {
        this.ticks = ticks;
    }

    /** @return a timespan of the given number of ticks (clamped to be non-negative) */
    public static Timespan ofTicks(long ticks) {
        return new Timespan(Math.max(0L, ticks));
    }

    /** @return a timespan of the given number of milliseconds */
    public static Timespan ofMillis(long millis) {
        return new Timespan(Math.max(0L, Math.round(millis / 50.0)));
    }

    /** @return the duration in ticks */
    public long ticks() {
        return ticks;
    }

    /** @return the duration in milliseconds (50 ms per tick) */
    public long millis() {
        return ticks * 50L;
    }

    /**
     * Parses a timespan to ticks.
     *
     * @param input the text, e.g. {@code "5 seconds"}
     * @return the duration in ticks, or empty if it is not a recognised timespan
     */
    public static OptionalLong parseTicks(String input) {
        Matcher matcher = PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        double amount = Double.parseDouble(matcher.group(1));
        long perUnit = ticksPerUnit(matcher.group(2).toLowerCase(java.util.Locale.ROOT));
        return OptionalLong.of(Math.max(1L, Math.round(amount * perUnit)));
    }

    /** Parses a timespan value, e.g. {@code "5 seconds"} → a {@link Timespan}. */
    public static Optional<Timespan> parse(String input) {
        OptionalLong ticks = parseTicks(input);
        return ticks.isEmpty() ? Optional.empty() : Optional.of(ofTicks(ticks.getAsLong()));
    }

    private static long ticksPerUnit(String unit) {
        if (unit.startsWith("tick")) {
            return 1L;
        }
        if (unit.startsWith("sec")) {
            return 20L;
        }
        if (unit.startsWith("min")) {
            return 20L * 60L;
        }
        if (unit.startsWith("day")) {
            return 20L * 60L * 60L * 24L;
        }
        return 20L * 60L * 60L; // hours
    }

    @Override
    public int compareTo(Timespan other) {
        return Long.compare(ticks, other.ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Timespan t && t.ticks == ticks;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks);
    }

    /** Renders the duration in the largest whole unit(s), e.g. {@code "1 hour 30 minutes"}. */
    @Override
    public String toString() {
        if (ticks == 0) {
            return "0 seconds";
        }
        long remaining = ticks;
        long[] units = {20L * 60L * 60L * 24L, 20L * 60L * 60L, 20L * 60L, 20L, 1L};
        String[] names = {"day", "hour", "minute", "second", "tick"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < units.length; i++) {
            long value = remaining / units[i];
            if (value > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(value).append(' ').append(names[i]).append(value == 1 ? "" : "s");
                remaining %= units[i];
            }
        }
        return sb.toString();
    }
}
