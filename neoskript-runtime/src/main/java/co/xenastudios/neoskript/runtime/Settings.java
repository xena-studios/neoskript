/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

/**
 * The settings from config.yml that change how scripts behave, as Skript's config.sk options of the same names.
 * The plugin sets them when it starts and on {@code /sk reload config}; until then they're Skript's defaults.
 * Triggers read them on hot paths, so each is one field read.
 */
public final class Settings {

    /** Skript's default {@code player name regex pattern}. */
    public static final String DEFAULT_PLAYER_NAME_PATTERN = "[a-zA-Z0-9_]{1,16}";

    private static volatile int numberAccuracy = 2;
    /** The accuracy as written, which may be negative: {@code numbers from X to Y} steps by it, as Skript does. */
    private static volatile int writtenAccuracy = 2;
    private static volatile boolean caseSensitive;
    private static volatile int targetBlockDistance = 100;
    private static volatile Pattern playerNamePattern = Pattern.compile(DEFAULT_PLAYER_NAME_PATTERN);
    /** Skript's {@code date format}; formatting synchronizes on it, as {@link DateFormat} isn't thread-safe. */
    private static volatile DateFormat dateFormat = defaultDateFormat();

    private Settings() {}

    /** Skript's {@code number accuracy}: at most this many decimal places when a number is written as text. */
    public static int numberAccuracy() {
        return numberAccuracy;
    }

    public static void numberAccuracy(int accuracy) {
        writtenAccuracy = accuracy;
        numberAccuracy = Math.max(accuracy, 0);
    }

    /**
     * The number accuracy as config.yml has it, below 0 too, for {@code numbers from 1.5 to 3}: as in Skript, a
     * negative one makes its steps 10, 100 and so on, though text rounds to whole numbers.
     */
    public static int writtenNumberAccuracy() {
        return writtenAccuracy;
    }

    /** Skript's {@code case sensitive}: whether text comparisons, {@code contains}, replace and split heed case. */
    public static boolean caseSensitive() {
        return caseSensitive;
    }

    public static void caseSensitive(boolean sensitive) {
        caseSensitive = sensitive;
    }

    /** Skript's {@code maximum target block distance}: how far targeted blocks and entities are looked for. */
    public static int targetBlockDistance() {
        return targetBlockDistance;
    }

    public static void targetBlockDistance(int distance) {
        targetBlockDistance = distance;
    }

    /** Skript's {@code player name regex pattern}: the text that can be read as an offline player's name. */
    public static Pattern playerNamePattern() {
        return playerNamePattern;
    }

    public static void playerNamePattern(Pattern pattern) {
        playerNamePattern = pattern;
    }

    /**
     * Sets Skript's {@code date format}: {@code default} (or {@code null}) for the server locale's short date and
     * time, as {@code 1/15/24, 10:30 AM}, or a {@link SimpleDateFormat} pattern.
     *
     * @throws IllegalArgumentException if the pattern isn't valid
     */
    public static void dateFormat(String pattern) {
        dateFormat = pattern == null || pattern.equalsIgnoreCase("default") ? defaultDateFormat()
                : new SimpleDateFormat(pattern);
    }

    /** A point in time as text, in the {@linkplain #dateFormat(String) date format}. */
    public static String formatDate(long millis) {
        DateFormat format = dateFormat;
        synchronized (format) {
            return format.format(new java.util.Date(millis));
        }
    }

    private static DateFormat defaultDateFormat() {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    }
}
