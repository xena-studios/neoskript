/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A time of day in a Minecraft world, Skript's {@code time}: {@code 12:30}, {@code 5pm}. Ticks count from
 * 06:00, as a world's time does, so 0 is 06:00 and 18000 is midnight.
 *
 * @param ticks from 0 to 23999
 */
public record TimeOfDay(int ticks) implements Comparable<TimeOfDay> {

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?: ?([ap])\\.?m\\.?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUR = Pattern.compile("(\\d{1,2}) ?([ap])\\.?m\\.?", Pattern.CASE_INSENSITIVE);

    public TimeOfDay {
        ticks = Math.floorMod(ticks, 24000);
    }

    /** The time a world's tick count shows. */
    public static TimeOfDay ofWorldTicks(long worldTicks) {
        return new TimeOfDay((int) Math.floorMod(worldTicks, 24000L));
    }

    /** Times named by word: where the day, dusk, night and dawn begin, noon and midnight. */
    private static final java.util.Map<String, Integer> NAMED = java.util.Map.of("day", 0, "dusk", 12000, "night", 13800,
            "dawn", 22200, "noon", 6000, "midday", 6000, "midnight", 18000);

    /** Parses {@code 12:30}, {@code 2:30pm}, {@code 5pm} or a named time like {@code night}, or returns {@code null}. */
    public static TimeOfDay parse(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(value);
        if (named != null) {
            return new TimeOfDay(named);
        }
        int hours;
        int minutes = 0;
        String half;
        Matcher clock = CLOCK.matcher(value);
        Matcher hour = HOUR.matcher(value);
        if (clock.matches()) {
            hours = Integer.parseInt(clock.group(1));
            minutes = Integer.parseInt(clock.group(2));
            half = clock.group(3);
        } else if (hour.matches()) {
            hours = Integer.parseInt(hour.group(1));
            half = hour.group(2);
        } else {
            return null;
        }
        if (half != null) {
            if (hours < 1 || hours > 12) {
                return null;
            }
            hours = hours % 12 + (half.equals("p") ? 12 : 0);
        }
        if (hours > 24 || minutes > 59) {
            return null;
        }
        return new TimeOfDay(fromMidnight(hours, minutes) - 6000);
    }

    private static int fromMidnight(int hours, int minutes) {
        return (int) Math.round(hours * 1000 + minutes * 1000 / 60.0);
    }

    /** How many ticks after midnight it is, which is what adding a time to another adds. */
    public int ticksAfterMidnight() {
        return Math.floorMod(ticks + 6000, 24000);
    }

    @Override
    public int compareTo(TimeOfDay other) {
        return Integer.compare(ticks, other.ticks);
    }

    /** As Skript writes times: {@code 06:00}. */
    @Override
    public String toString() {
        int fromMidnight = ticksAfterMidnight();
        return String.format(Locale.ROOT, "%02d:%02d", fromMidnight / 1000, fromMidnight % 1000 * 60 / 1000);
    }
}
