/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * NeoSkriptWeb's settings, from its {@code config.yml}. Each value is checked, and one that isn't valid is the
 * default, with a warning saying so.
 *
 * @param timeout how long a request may take, redirects and the whole answer included
 * @param connectTimeout how long connecting may take
 * @param maxResponseBytes the largest answer kept
 * @param maxRedirects how many redirects a request follows
 * @param maxInFlight how many requests can wait for an answer at once
 * @param maxPerMinute how many requests can start in a minute
 * @param blockPrivateAddresses whether requests to this machine and private networks are refused
 * @param maxResponseMemoryBytes the memory all answers in flight may take between them
 */
public record WebConfig(Duration timeout, Duration connectTimeout, long maxResponseBytes, int maxRedirects,
        int maxInFlight, int maxPerMinute, boolean blockPrivateAddresses, long maxResponseMemoryBytes) {

    /** The defaults, as in the {@code config.yml} the addon writes. */
    public static final WebConfig DEFAULTS = new WebConfig(Duration.ofSeconds(30), Duration.ofSeconds(10),
            8192L * 1024, 5, 64, 1200, false, 128L * 1024 * 1024);

    /**
     * Reads the settings.
     *
     * @param config the config file's contents
     * @param warn where a warning about a value that isn't valid goes
     * @return the settings
     */
    public static WebConfig read(ConfigurationSection config, Consumer<String> warn) {
        List<String> warnings = new ArrayList<>();
        int timeout = whole(config, "timeout-seconds", 30, 1, 600, warnings);
        int connect = whole(config, "connect-timeout-seconds", 10, 1, 120, warnings);
        int kilobytes = whole(config, "max-response-kilobytes", 8192, 1, 262_144, warnings);
        int redirects = whole(config, "max-redirects", 5, 0, 20, warnings);
        int inFlight = whole(config, "max-requests-in-flight", 64, 1, 1024, warnings);
        int perMinute = whole(config, "max-requests-per-minute", 1200, 1, 100_000, warnings);
        int memory = whole(config, "max-response-memory-megabytes", 128, 1, 8192, warnings);
        boolean block = DEFAULTS.blockPrivateAddresses;
        Object value = config.get("block-private-addresses");
        if (value instanceof Boolean flag) {
            block = flag;
        } else if (value != null) {
            warnings.add("block-private-addresses must be true or false, not '" + value + "'. Using " + block + ".");
        }
        warnings.forEach(w -> warn.accept("config.yml: " + w));
        return new WebConfig(Duration.ofSeconds(timeout), Duration.ofSeconds(connect), kilobytes * 1024L, redirects,
                inFlight, perMinute, block, memory * 1024L * 1024);
    }

    private static int whole(ConfigurationSection config, String key, int fallback, int min, int max,
            List<String> warnings) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer number && number >= min && number <= max) {
            return number;
        }
        warnings.add(key + " must be a whole number from " + min + " to " + max + ", not '" + value + "'. Using "
                + fallback + ".");
        return fallback;
    }
}
