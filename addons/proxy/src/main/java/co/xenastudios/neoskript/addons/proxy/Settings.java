/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * config.yml. Each value that isn't valid falls back to its default on its own, with a warning naming it.
 *
 * @param serverName this server's name on the proxy, or empty to ask the proxy
 * @param secret what network messages over plugin messaging are signed with, or empty
 * @param pluginMessaging whether plugin messaging is on
 * @param proxyTimeoutMillis how long a question to the proxy waits for its answer
 * @param refreshMillis how often the originals' expressions ask the proxy again, at most
 * @param queueWithoutPlayers whether messages wait for a player when nobody's online
 * @param maxQueued the most messages waiting for a player
 * @param queuedForMillis how long one waits
 * @param redis whether Redis is on
 * @param redisHost Redis's host
 * @param redisPort Redis's port
 * @param redisUser Redis's user, or empty
 * @param redisPassword Redis's password, or empty
 * @param redisDatabase the database selected
 * @param redisSsl whether to connect with TLS
 * @param redisTimeoutMillis how long connecting and each reply may take
 * @param redisQueueOnOutage whether messages wait while Redis is down
 * @param redisMaxQueued the most messages waiting for Redis
 * @param redisQueuedForMillis how long one waits
 * @param redisChannelPrefix put in front of every Redis channel, to keep networks sharing a Redis apart
 * @param maxMessageBytes the longest network message, in bytes of UTF-8
 * @param networkDatabase the database in NeoSkript's config.yml that keeps {@code {network::...}} variables
 * @param networkNotify whether to tell the other servers each time this one writes network variables
 */
public record Settings(String serverName, String secret, boolean pluginMessaging, long proxyTimeoutMillis,
        long refreshMillis,
        boolean queueWithoutPlayers, int maxQueued, long queuedForMillis, boolean redis, String redisHost,
        int redisPort, String redisUser, String redisPassword, int redisDatabase, boolean redisSsl,
        long redisTimeoutMillis, boolean redisQueueOnOutage, int redisMaxQueued, long redisQueuedForMillis,
        String redisChannelPrefix, int maxMessageBytes, String networkDatabase, boolean networkNotify) {

    /** The defaults. */
    public static final Settings DEFAULTS = read(key -> null, new ArrayList<>());

    /**
     * Reads the settings by their names in config.yml ({@code redis.host}).
     *
     * @param values a value by its name, or {@code null} where it isn't set
     * @param warnings where problems go, one line each
     * @return the settings
     */
    public static Settings read(Function<String, Object> values, List<String> warnings) {
        Reader r = new Reader(values, warnings);
        return new Settings(r.text("server-name", ""), r.text("secret", ""),
                r.flag("plugin-messaging.enabled", true), r.number("plugin-messaging.timeout", 5_000, 100, 600_000),
                r.number("plugin-messaging.refresh", 1_000, 50, 600_000),
                r.flag("plugin-messaging.queue-without-players", true),
                (int) r.number("plugin-messaging.max-queued", 1_000, 0, 1_000_000),
                r.number("plugin-messaging.queued-for", 60, 1, 86_400) * 1000,
                r.flag("redis.enabled", false), r.text("redis.host", "localhost"),
                (int) r.number("redis.port", 6379, 1, 65_535), r.text("redis.username", ""),
                r.text("redis.password", ""), (int) r.number("redis.database", 0, 0, 1_000_000),
                r.flag("redis.ssl", false), r.number("redis.timeout", 2_000, 100, 600_000),
                r.choice("redis.on-outage", "queue", "queue", "drop").equals("queue"),
                (int) r.number("redis.max-queued", 1_000, 0, 1_000_000),
                r.number("redis.queued-for", 60, 1, 86_400) * 1000, r.text("redis.channel-prefix", ""),
                (int) r.number("max-message-bytes", 16_384, 1, Envelope.MAX_MESSAGE_BYTES),
                r.text("network-variables.database", "network"), r.flag("network-variables.notify", true));
    }

    private record Reader(Function<String, Object> values, List<String> warnings) {

        long number(String key, long fallback, long min, long max) {
            Object value = values.apply(key);
            if (value == null) {
                return fallback;
            }
            long number;
            if (value instanceof Integer || value instanceof Long || value instanceof Short) {
                number = ((Number) value).longValue();
            } else if (value instanceof String text && text.strip().matches("-?\\d{1,18}")) {
                number = Long.parseLong(text.strip());
            } else {
                warnings.add(key + " is " + describe(value) + ", which isn't a whole number: using " + fallback);
                return fallback;
            }
            if (number < min || number > max) {
                warnings.add(key + " is " + number + ", but it has to be from " + min + " to " + max + ": using "
                        + fallback);
                return fallback;
            }
            return number;
        }

        boolean flag(String key, boolean fallback) {
            Object value = values.apply(key);
            if (value == null) {
                return fallback;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
                return Boolean.parseBoolean(text);
            }
            warnings.add(key + " is " + describe(value) + ", which isn't true or false: using " + fallback);
            return fallback;
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

        String choice(String key, String fallback, String... allowed) {
            String value = text(key, fallback);
            for (String option : allowed) {
                if (option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
            warnings.add(key + " is \"" + value + "\", but it has to be " + String.join(" or ", allowed) + ": using "
                    + fallback);
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
    }
}
