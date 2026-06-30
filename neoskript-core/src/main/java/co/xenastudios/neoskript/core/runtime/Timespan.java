package co.xenastudios.neoskript.core.runtime;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-friendly durations ({@code 5 seconds}, {@code 2 minutes}, {@code 20 ticks}) into
 * server ticks (20 ticks per second). Used by the {@code every <timespan>:} periodic trigger.
 */
public final class Timespan {

    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(ticks?|seconds?|secs?|minutes?|mins?|hours?|hrs?)",
            Pattern.CASE_INSENSITIVE);

    private Timespan() {
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
        return 20L * 60L * 60L; // hours
    }
}
