/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Skript's config.sk options that NeoSkript's config.yml has too, read as Skript reads them, for copying to
 * config.yml the first time NeoSkript starts. So a server that changed one keeps Skript's behaviour.
 *
 * <p>An option config.sk doesn't have isn't copied: NeoSkript's default is the value in Skript's default config.
 * One whose value Skript can't use is copied as what Skript then runs with, its built-in default, as Skript logs an
 * error and uses that.
 */
final class SkriptSettings {

    /**
     * One option: its name in config.sk, its path in config.yml, how Skript reads its value (into what config.yml
     * writes, or {@code null} if Skript can't use it), and what Skript uses then.
     */
    record Option(String skript, String path, Function<String, Object> parse, Object fallback) {}

    static final List<Option> OPTIONS = List.of(
            bool("enable effect commands", "effect-commands.enabled", false),
            // Text as written; Skript reads an empty token too, but NeoSkript's config won't, so it isn't copied.
            new Option("effect command token", "effect-commands.token", value -> value.isEmpty() ? null : value, null),
            bool("allow ops to use effect commands", "effect-commands.allow-ops", false),
            bool("log effect commands", "effect-commands.log", false),
            bool("use player UUIDs in variable names", "variables.player-uuids-in-names", false),
            bool("player variable fix", "variables.player-variable-fix", true),
            new Option("date format", "text.date-format", SkriptSettings::dateFormat, "default"),
            // A negative accuracy rounds to whole numbers in Skript, as 0 does.
            new Option("number accuracy", "text.number-accuracy",
                    value -> integer(value) instanceof Integer n ? Math.clamp(n, 0, 100) : null, 2),
            bool("case sensitive", "text.case-sensitive", false),
            bool("color codes reset formatting", "text.color-codes-reset-formatting", true),
            // As Skript splits it: at each comma and space.
            new Option("safe tags", "text.safe-tags", value -> Arrays.asList(value.split(", ")), null),
            new Option("parse links in chat messages", "text.parse-links", value -> {
                NeoSkriptConfig.LinkParsing parsing = NeoSkriptConfig.LinkParsing.parse(value);
                return parsing == null ? null : parsing.name().toLowerCase(Locale.ROOT);
            }, "disabled"),
            // Skript's target looks 100 blocks for a distance below 1; config.yml takes 1 to 1000.
            new Option("maximum target block distance", "syntax.target-block-distance",
                    value -> integer(value) instanceof Integer n ? (n < 1 ? 100 : Math.min(n, 1000)) : null, 100),
            new Option("player name regex pattern", "syntax.player-name-pattern", value -> {
                try {
                    return NeoSkriptConfig.NamePattern.parse(value).toString();
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }, "[a-zA-Z0-9_]{1,16}"),
            new Option("plugin priority", "events.priority", value -> {
                String name = value.toLowerCase(Locale.ENGLISH);
                return List.of("lowest", "low", "normal", "high", "highest", "monitor").contains(name) ? name : null;
            }, "normal"),
            bool("listen to cancelled events by default", "events.listen-to-cancelled", false),
            new Option("runtime errors.frame duration", "runtime-errors.frame-duration", value -> {
                try {
                    return NeoSkriptConfig.Interval.parse(value).toString();
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }, "1 second"),
            count("runtime errors.total errors per frame", "runtime-errors.total-errors-per-frame", 8),
            count("runtime errors.errors from one line per frame", "runtime-errors.errors-from-one-line-per-frame", 2),
            count("runtime errors.error spam timeout limit", "runtime-errors.error-spam-timeout-limit", 4),
            count("runtime errors.error timeout length", "runtime-errors.error-timeout-length", 10),
            count("runtime errors.total warnings per frame", "runtime-errors.total-warnings-per-frame", 8),
            count("runtime errors.warnings from one line per frame", "runtime-errors.warnings-from-one-line-per-frame", 2),
            count("runtime errors.warning spam timeout limit", "runtime-errors.warning-spam-timeout-limit", 4),
            count("runtime errors.warning timeout length", "runtime-errors.warning-timeout-length", 10));

    private SkriptSettings() {}

    /**
     * The options config.sk has, by their path in config.yml, as config.yml writes them: booleans, whole numbers,
     * and text for the rest. Its {@code language} is copied if NeoSkript has that language, built in or in
     * {@code langFolder}, where Skript's own language files are copied; otherwise it's English, as in Skript.
     */
    static Map<String, Object> read(Path config, Path langFolder) throws IOException {
        Map<String, Object> settings = new LinkedHashMap<>();
        if (!Files.isRegularFile(config)) {
            return settings;
        }
        Map<String, String> entries;
        try {
            entries = SkriptConfigFile.read(Files.readString(config, StandardCharsets.UTF_8));
        } catch (java.nio.charset.CharacterCodingException e) {
            // Skript reads config.sk as UTF-8; one that isn't can't have been read as Skript reads it.
            throw new IOException("config.sk isn't UTF-8", e);
        }
        String language = entries.get("language");
        if (language != null) {
            String name = language.toLowerCase(Locale.ENGLISH);
            settings.put("language", LanguageFiles.load(name, langFolder) != null ? name : "english");
        }
        for (Option option : OPTIONS) {
            String value = entries.get(option.skript().toLowerCase(Locale.ENGLISH));
            if (value == null) {
                continue;
            }
            Object parsed = option.parse().apply(value);
            if (parsed == null) {
                parsed = option.fallback();
            }
            if (parsed != null) {
                settings.put(option.path(), parsed);
            }
        }
        return settings;
    }

    private static Option bool(String skript, String path, boolean fallback) {
        return new Option(skript, path, SkriptConfigFile::bool, fallback);
    }

    /** A count, from 0 up as config.yml takes it: Skript shows nothing for a negative limit, as for 0. */
    private static Option count(String skript, String path, int fallback) {
        return new Option(skript, path, value -> integer(value) instanceof Integer n ? Math.max(n, 0) : null, fallback);
    }

    /** A whole number as Skript reads one in config.sk, or {@code null}. */
    static Integer integer(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Skript's {@code date format}: {@code default}, or a pattern Java can use. */
    private static Object dateFormat(String value) {
        try {
            return NeoSkriptConfig.DatePattern.parse(value).text();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
