/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * config.yml: skript-db's settings, with its names and meanings, and a few of NeoSkriptDb's own. Each value that
 * isn't valid falls back to its default on its own, with a warning naming it (skript-db drops the whole file).
 *
 * @param poolSize the most connections per database
 * @param workerThreads threads that run statements
 * @param maxLifetimeMillis how long a connection is kept
 * @param connectionTimeoutMillis how long a statement waits for a connection
 * @param queryTimeoutSeconds how long a statement may run, 0 for no limit
 * @param transactionTimeoutSeconds how long an abandoned transaction stays open
 * @param driverClassName the driver for every database, or {@code null} to pick it from the URL
 * @param maxRows the most rows stored in a list, 0 for no limit
 * @param idleCloseSeconds how long a database may go unused before its connections close, 0 for never
 */
public record Settings(int poolSize, int workerThreads, long maxLifetimeMillis, long connectionTimeoutMillis,
        int queryTimeoutSeconds, int transactionTimeoutSeconds, String driverClassName, int maxRows,
        int idleCloseSeconds) {

    /** The defaults: skript-db's, but with a 30-second query timeout. */
    public static final Settings DEFAULTS = new Settings(10, automaticThreads(), 1_800_000, 30_000, 30, 30, null,
            100_000, 600);

    /** How long a trigger waits for a statement when statements have no time limit. */
    static final long UNLIMITED_WAIT_MILLIS = 10 * 60 * 1000;

    /**
     * How long a trigger waits for a statement: the time it may wait for a connection and run, plus a margin, so
     * the database's own error arrives first.
     *
     * @return the wait, in milliseconds
     */
    public long waitMillis() {
        if (queryTimeoutSeconds == 0) {
            return UNLIMITED_WAIT_MILLIS;
        }
        return connectionTimeoutMillis + queryTimeoutSeconds * 1000L + 5_000;
    }

    private static int automaticThreads() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() + 2);
    }

    /**
     * Reads the settings, each from {@code values} by its name in config.yml.
     *
     * @param values a value by its name, or {@code null} where it isn't set
     * @param warnings where problems go, one line each
     * @return the settings
     */
    public static Settings read(Function<String, Object> values, List<String> warnings) {
        Reader reader = new Reader(values, warnings);
        int threads = (int) reader.number("thread-pool-size", 0, 0, 1024);
        int poolSize = (int) reader.number("pool-size", 10, 1, 1024);
        String driver = reader.text("sql-driver-class-name", "default");
        if (driver.isBlank() || driver.equalsIgnoreCase("default")) {
            driver = null;
        }
        return new Settings(poolSize, threads == 0 ? automaticThreads() : threads,
                reader.number("max-connection-lifetime", 1_800_000, 0, Long.MAX_VALUE),
                reader.number("connection-timeout", 30_000, 250, 24L * 60 * 60 * 1000),
                (int) reader.number("query-timeout", 30, 0, 24 * 60 * 60),
                (int) reader.number("transaction-timeout", 30, 1, 24 * 60 * 60), driver,
                (int) reader.number("max-rows", 100_000, 0, Integer.MAX_VALUE),
                (int) reader.number("idle-close", 600, 0, 7 * 24 * 60 * 60));
    }

    private record Reader(Function<String, Object> values, List<String> warnings) {

        Reader {
            warnings = warnings == null ? new ArrayList<>() : warnings;
        }

        long number(String key, long fallback, long min, long max) {
            Object value = values.apply(key);
            if (value == null) {
                return fallback;
            }
            long number;
            if (value instanceof Number n && (n instanceof Integer || n instanceof Long || n instanceof Short
                    || n instanceof Byte)) {
                number = n.longValue();
            } else if (value instanceof String text && text.strip().matches("-?\\d{1,18}")) {
                number = Long.parseLong(text.strip());
            } else {
                warnings.add(key + " is " + describe(value) + ", which isn't a whole number: using " + fallback);
                return fallback;
            }
            if (number < min || number > max) {
                warnings.add(key + " is " + number + ", but it has to be " + range(min, max) + ": using "
                        + fallback);
                return fallback;
            }
            return number;
        }

        String text(String key, String fallback) {
            Object value = values.apply(key);
            if (value == null) {
                return fallback;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value.toString().strip();
            }
            warnings.add(key + " is " + describe(value) + ", which isn't a text: using \"" + fallback + "\"");
            return fallback;
        }

        private static String describe(Object value) {
            return switch (value) {
                case String text -> "\"" + text + "\"";
                case java.util.Collection<?> list -> "a list";
                case java.util.Map<?, ?> map -> "a section";
                default -> value.getClass().getName().startsWith("org.bukkit.") ? "a section" : String.valueOf(value);
            };
        }

        private static String range(long min, long max) {
            return max == Long.MAX_VALUE || max >= Integer.MAX_VALUE ? "at least " + min
                    : "from " + min + " to " + max;
        }
    }
}
