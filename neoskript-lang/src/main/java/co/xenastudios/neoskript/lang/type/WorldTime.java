package co.xenastudios.neoskript.lang.type;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Minecraft world time of day, mirroring Skript's {@code time} type. Stored as the world tick
 * (0–23999); {@code 6:00} is tick 0 (dawn), {@code 12:00} is 6000 (noon), {@code 0:00} is 18000
 * (midnight). Parses 24-hour or 12-hour ({@code am}/{@code pm}) clock strings and renders 24-hour.
 */
public final class WorldTime {

    private static final Pattern PATTERN = Pattern.compile(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE);

    private final int ticks;

    private WorldTime(int ticks) {
        this.ticks = ((ticks % 24000) + 24000) % 24000;
    }

    /** @return a world time at the given tick */
    public static WorldTime ofTicks(long ticks) {
        return new WorldTime((int) ticks);
    }

    /** @return the world tick (0–23999) */
    public int ticks() {
        return ticks;
    }

    /** Parses a clock string such as {@code "12:00"}, {@code "6:30 pm"}, or {@code "0:00"}. */
    public static Optional<WorldTime> parse(String input) {
        Matcher m = PATTERN.matcher(input.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }
        String meridiem = m.group(3);
        if (meridiem != null) {
            hour %= 12;
            if (meridiem.equalsIgnoreCase("pm")) {
                hour += 12;
            }
        }
        int ticks = (hour * 1000 + minute * 1000 / 60 + 18000) % 24000;
        return Optional.of(new WorldTime(ticks));
    }

    @Override
    public String toString() {
        int total = (ticks + 6000) % 24000; // shift so tick 0 -> 6:00
        int hour = (total / 1000) % 24;
        int minute = (total % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hour, minute);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldTime t && t.ticks == ticks;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(ticks);
    }
}
