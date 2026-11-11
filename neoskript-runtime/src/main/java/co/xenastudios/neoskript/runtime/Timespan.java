/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A length of time, like {@code 2 minutes and 30 seconds}. */
public record Timespan(long millis) implements Comparable<Timespan> {

    /** An eternity, Skript's {@code forever}: the longest timespan, which stays forever through arithmetic. */
    public static final Timespan FOREVER = new Timespan(Long.MAX_VALUE);

    /** Whether this is {@link #FOREVER}. */
    public boolean isInfinite() {
        return millis == Long.MAX_VALUE;
    }

    private static final Pattern COMPACT = Pattern.compile("(\\d+(?:\\.\\d+)?)(mo|ms|[tsmhdwy])");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern CLOCK = Pattern.compile("(\\d+):(\\d\\d)((?::\\d\\d){0,2})(\\.\\d{1,4})?");
    private static final String[] WORDS = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
    private static final long[] UNIT_MILLIS = {50, 1000, 60_000, 3_600_000, 86_400_000L, 604_800_000L, 2_592_000_000L,
        31_536_000_000L, 1};
    private static final String[] UNITS = {"tick", "second", "minute", "hour", "day", "week", "month", "year",
        "millisecond"};
    private static final String[] SHORT_UNITS = {"t", "s", "m", "h", "d", "w", "mo", "y", "ms"};

    /**
     * Parses a timespan as Skript does, or returns {@code null}: {@code 1 minute and 30 seconds}, {@code 1d 6h},
     * {@code 2.5h}, {@code 1 mc day}, or a clock reading like {@code 1:30} (minutes and seconds).
     */
    public static Timespan parse(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        Matcher clock = CLOCK.matcher(value);
        if (clock.matches()) {
            List<Long> parts = new ArrayList<>();
            parts.add(Long.parseLong(clock.group(1)));
            parts.add(Long.parseLong(clock.group(2)));
            for (String part : clock.group(3).split(":")) {
                if (!part.isEmpty()) {
                    parts.add(Long.parseLong(part));
                }
            }
            // The last part is seconds, then minutes, hours and days before it.
            long millis = 0;
            for (int i = 0; i < parts.size(); i++) {
                millis += parts.get(parts.size() - 1 - i) * UNIT_MILLIS[i + 1];
            }
            if (clock.group(4) != null) {
                millis += Math.round(Double.parseDouble(clock.group(4)) * 1000);
            }
            return new Timespan(millis);
        }
        String[] words = value.split("\\s+");
        double millis = 0;
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.equals("and")) {
                if (i == 0 || i == words.length - 1) {
                    return null;
                }
                continue;
            }
            double amount = 1;
            Matcher compact = COMPACT.matcher(word.endsWith(",") ? word.substring(0, word.length() - 1) : word);
            if (compact.matches()) {
                // '1d', '2h', '30m', '1mo': a number and a unit's letter.
                millis += Double.parseDouble(compact.group(1)) * UNIT_MILLIS[List.of(SHORT_UNITS).indexOf(compact.group(2))];
                continue;
            }
            if (word.equals("a") || word.equals("an") || NUMBER.matcher(word).matches() || List.of(WORDS).contains(word)) {
                if (i == words.length - 1) {
                    return null;
                }
                amount = amount(word);
                word = words[++i];
            }
            boolean minecraft = false;
            if (word.equals("real") || word.equals("rl") || word.equals("irl")
                    || (minecraft = word.equals("minecraft") || word.equals("mc"))) {
                if (i == words.length - 1) {
                    return null;
                }
                word = words[++i];
            }
            if (word.endsWith(",")) {
                word = word.substring(0, word.length() - 1);
            }
            int unit = unit(word);
            if (unit < 0) {
                return null;
            }
            double unitMillis = UNIT_MILLIS[unit];
            if (minecraft && unit > 0) {
                // A Minecraft day is 20 real minutes: 72 times faster than real time.
                unitMillis /= 72;
            }
            millis += amount * unitMillis;
        }
        return new Timespan(Math.round(millis));
    }

    private static int unit(String word) {
        String singular = word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
        return List.of(UNITS).indexOf(singular);
    }

    private static double amount(String text) {
        if (text.equals("a") || text.equals("an")) {
            return 1;
        }
        int word = List.of(WORDS).indexOf(text);
        return word >= 0 ? word + 1 : Double.parseDouble(text);
    }

    /** The length in server ticks (20 a second), rounded down. */
    public long ticks() {
        return millis / 50;
    }

    @Override
    public int compareTo(Timespan other) {
        return Long.compare(millis, other.millis);
    }

    /** As Skript writes it: {@code 2 minutes and 30 seconds}. */
    @Override
    public String toString() {
        if (isInfinite()) {
            return "forever";
        }
        return text(millis);
    }

    /** The years to seconds Skript writes timespans in, largest first, with their names. */
    private static final long[] TEXT_MILLIS = {31_536_000_000L, 2_592_000_000L, 604_800_000L, 86_400_000L, 3_600_000L,
            60_000L, 1000L};
    private static final String[] TEXT_UNITS = {"year", "month", "week", "day", "hour", "minute", "second"};

    /**
     * As Skript's Timespan.toString: the largest unit that fits, then what's left after {@code and}, unless
     * that would read as 0 of the next unit: {@code 1 day and 1 hour and 31.15 seconds}, {@code 1.5 seconds}.
     */
    private static String text(long millis) {
        for (int i = 0; i < TEXT_MILLIS.length - 1; i++) {
            long unit = TEXT_MILLIS[i];
            if (millis >= unit) {
                long remainder = millis % unit;
                if (!"0".equals(Values.decimal((double) remainder / TEXT_MILLIS[i + 1]))) {
                    return withAmount(Math.floor((double) millis / unit), i) + " and " + text(remainder);
                }
                return withAmount((double) millis / unit, i);
            }
        }
        return withAmount((double) millis / 1000, TEXT_UNITS.length - 1);
    }

    private static String withAmount(double amount, int unit) {
        return Values.decimal(amount) + " " + TEXT_UNITS[unit] + (amount == 1 ? "" : "s");
    }
}
