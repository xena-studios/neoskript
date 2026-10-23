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

    private static final Pattern PART = Pattern.compile(
            "(\\d+(?:\\.\\d+)?|an?|one|two|three|four|five|six|seven|eight|nine|ten) ?((?:real|rl|irl|minecraft|mc) )?"
                    + "(tick|second|minute|hour|day|week|month|year)s?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPACT = Pattern.compile("(\\d+(?:\\.\\d+)?) ?([tsmhdw])", Pattern.CASE_INSENSITIVE);
    private static final String[] WORDS = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
    private static final long[] UNIT_MILLIS = {50, 1000, 60_000, 3_600_000, 86_400_000L, 604_800_000L, 2_592_000_000L,
        31_536_000_000L};
    private static final String[] UNITS = {"tick", "second", "minute", "hour", "day", "week", "month", "year"};

    /** Parses a timespan literal like {@code 1 minute and 30 seconds}, or returns {@code null}. */
    public static Timespan parse(String text) {
        Matcher compact = COMPACT.matcher(text.strip());
        if (compact.matches()) {
            // '1d', '2h', '30m', '10s', '5t': a number and a unit letter.
            int unit = "tsmhdw".indexOf(Character.toLowerCase(compact.group(2).charAt(0)));
            return new Timespan(Math.round(Double.parseDouble(compact.group(1)) * UNIT_MILLIS[unit]));
        }
        Matcher matcher = PART.matcher(text);
        double millis = 0;
        int end = 0;
        boolean any = false;
        while (matcher.find()) {
            String between = text.substring(end, matcher.start()).strip().toLowerCase(Locale.ROOT);
            if (!between.isEmpty() && !between.equals(",") && !between.equals("and") && !between.equals(", and")) {
                return null;
            }
            double amount = amount(matcher.group(1).toLowerCase(Locale.ROOT));
            int unit = List.of(UNITS).indexOf(matcher.group(3).toLowerCase(Locale.ROOT));
            double unitMillis = UNIT_MILLIS[unit];
            String prefix = matcher.group(2);
            if (prefix != null && prefix.strip().toLowerCase(Locale.ROOT).matches("minecraft|mc") && unit > 0) {
                // A Minecraft day is 20 real minutes: 72 times faster than real time.
                unitMillis /= 72;
            }
            millis += amount * unitMillis;
            end = matcher.end();
            any = true;
        }
        return any && text.substring(end).isBlank() ? new Timespan(Math.round(millis)) : null;
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
        if (millis == 0) {
            return "0 seconds";
        }
        List<String> parts = new ArrayList<>();
        long left = millis;
        for (int unit = 4; unit >= 1 && left > 0; unit--) {
            long amount = left / UNIT_MILLIS[unit];
            if (amount > 0) {
                parts.add(amount + " " + UNITS[unit] + (amount == 1 ? "" : "s"));
                left -= amount * UNIT_MILLIS[unit];
            }
        }
        if (left > 0) {
            double seconds = left / 1000.0;
            parts.add(Values.decimal(seconds) + " seconds");
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }
}
