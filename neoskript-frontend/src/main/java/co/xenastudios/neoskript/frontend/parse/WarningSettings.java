/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.Set;
import java.util.function.LongFunction;

/**
 * Warnings turned off for every script, and the long parse time warning, as config.yml's {@code warnings} (Skript's
 * {@code disable ... warnings} options and {@code long parse time warning threshold}). The plugin sets them; the CLI
 * and the web parser leave them as Skript's defaults: every warning on, and no long parse time warning.
 */
public final class WarningSettings {

    /** "Unreachable code. The previous statement stops further execution." */
    public static final String UNREACHABLE_CODE = "unreachable";
    /** "List is missing 'and' or 'or', defaulting to 'and'". */
    public static final String MISSING_AND_OR = "conjunction";
    /** "A ... cannot be saved. That is, the contents of the variable ... will be lost when the server stops." */
    public static final String VARIABLE_NOT_SAVED = "save";
    /** "If you meant to make the variable {...} a list, its name should contain '::'. ..." */
    public static final String SINGLE_COLON = "colon";
    /** "Starting a variable's name with an expression is discouraged ...". */
    public static final String STARTS_WITH_EXPRESSION = "starting";

    private static volatile Set<String> disabled = Set.of();
    private static volatile long longParseMillis;
    private static volatile LongFunction<String> duration = millis -> millis + " milliseconds";
    /** The clock lines are timed by; tests set their own. */
    static volatile java.util.function.LongSupplier clock = System::currentTimeMillis;

    private WarningSettings() {}

    /** Turns off the warnings tagged {@code tags} ({@link #UNREACHABLE_CODE} and so on) for every script. */
    public static void disable(Set<String> tags) {
        disabled = Set.copyOf(tags);
    }

    /** Whether the warnings tagged {@code tag} are turned off for every script. */
    public static boolean disabled(String tag) {
        return disabled.contains(tag);
    }

    /**
     * Warns about a line that takes longer than {@code millis} to parse (0 for never), with the time it took written
     * by {@code duration}, as Skript writes a timespan.
     */
    public static void longParseTime(long millis, LongFunction<String> duration) {
        WarningSettings.duration = duration;
        longParseMillis = millis;
    }

    static long longParseMillis() {
        return longParseMillis;
    }

    static String duration(long millis) {
        return duration.apply(millis);
    }
}
