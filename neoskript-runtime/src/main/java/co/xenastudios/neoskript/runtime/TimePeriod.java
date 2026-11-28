/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.Locale;

/**
 * A period of the day between two times, as Skript's {@code Timeperiod}: {@code day}, {@code night} or
 * {@code 6:00-7:30}. A time is it if the time is within it.
 *
 * @param start the ticks it starts at
 * @param end the ticks it ends at, the same as {@code start} for a single time
 */
public record TimePeriod(int start, int end) {

    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED = new java.util.concurrent.atomic.AtomicBoolean();

    /** Registers that a time is a period if it's within it, as Skript's comparator, once. */
    public static void registerComparisons() {
        if (REGISTERED.compareAndSet(false, true)) {
            Comparison.registerEquality(TimeOfDay.class, TimePeriod.class,
                    (time, period) -> period.contains(time.ticks()));
        }
    }

    public TimePeriod {
        start = Math.floorMod(start, 24000);
        end = Math.floorMod(end, 24000);
    }

    /** Reads {@code day}, {@code dusk}, {@code night}, {@code dawn}, a time or {@code <time>-<time>}, as Skript does. */
    public static TimePeriod parse(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        switch (value) {
            case "day" -> {
                return new TimePeriod(0, 11999);
            }
            case "dusk" -> {
                return new TimePeriod(12000, 13799);
            }
            case "night" -> {
                return new TimePeriod(13800, 22199);
            }
            case "dawn" -> {
                return new TimePeriod(22200, 23999);
            }
            default -> { }
        }
        int dash = value.indexOf('-');
        if (dash < 0) {
            TimeOfDay time = TimeOfDay.parse(value);
            return time == null ? null : new TimePeriod(time.ticks(), time.ticks());
        }
        TimeOfDay from = TimeOfDay.parse(value.substring(0, dash).strip());
        TimeOfDay to = TimeOfDay.parse(value.substring(dash + 1).strip());
        return from == null || to == null ? null : new TimePeriod(from.ticks(), to.ticks());
    }

    /** Whether the time {@code ticks} is in it. */
    public boolean contains(int ticks) {
        return start <= end ? ticks >= start && ticks <= end : ticks <= end || ticks >= start;
    }

    /** As Skript writes it: {@code 6:00-18:00}, or one time if it starts where it ends. */
    @Override
    public String toString() {
        return new TimeOfDay(start) + (start == end ? "" : "-" + new TimeOfDay(end));
    }
}
