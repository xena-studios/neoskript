/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import java.io.IOException;
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
            bool("load default aliases", "aliases.load-defaults", true),
            bool("enable effect commands", "effect-commands.enabled", false),
            // Text as written; Skript reads an empty token too, but NeoSkript's config won't, so it isn't copied.
            new Option("effect command token", "effect-commands.token", value -> value.isEmpty() ? null : value, null),
            bool("allow ops to use effect commands", "effect-commands.allow-ops", false),
            bool("log effect commands", "effect-commands.log", false),
            bool("use player UUIDs in variable names", "variables.player-uuids-in-names", false),
            bool("player variable fix", "variables.player-variable-fix", true),
            new Option("date format", "text.date-format", SkriptSettings::dateFormat, "default"),
            // A negative accuracy rounds text to whole numbers, as 0 does, but 'numbers from' steps by it. Beyond 1000
            // either way, every number would be written with that many digits, which can stall the server.
            new Option("number accuracy", "text.number-accuracy",
                    value -> integer(value) instanceof Integer n ? Math.clamp(n, -1000, 1000) : null, 2),
            bool("case sensitive", "text.case-sensitive", false),
            bool("color codes reset formatting", "text.color-codes-reset-formatting", true),
            // As Skript splits it: at each comma and space.
            new Option("safe tags", "text.safe-tags", value -> Arrays.asList(value.split(", ")), null),
            // Matched exactly, as Skript does: 'Strict' is unknown to it, so links are disabled.
            new Option("parse links in chat messages", "text.parse-links", value -> switch (value) {
                case "false", "disabled" -> "disabled";
                case "true", "lenient" -> "lenient";
                case "strict" -> "strict";
                default -> null;
            }, "disabled"),
            // config.yml takes 0 to 1000: Skript's elements read a negative distance each their own way, and one above
            // 1000 can stall the server tracing a ray or listing blocks.
            new Option("maximum target block distance", "syntax.target-block-distance",
                    value -> integer(value) instanceof Integer n ? Math.clamp(n, 0, 1000) : null, 100),
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
            bool("use type properties", "syntax.type-properties", true),
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
            count("runtime errors.warning timeout length", "runtime-errors.warning-timeout-length", 10),
            new Option("verbosity", "verbosity", value -> {
                String name = value.strip().toLowerCase(Locale.ENGLISH).replace('_', ' ');
                return List.of("low", "normal", "high", "very high", "debug").contains(name) ? name.replace(' ', '-')
                        : null;
            }, "normal"),
            shown("disable variable will not be saved warnings", "warnings.variable-not-saved"),
            shown("disable variable missing and/or warnings", "warnings.missing-and-or"),
            shown("disable starting a variable's name with an expression warnings",
                    "warnings.variable-starting-with-expression"),
            shown("disable single colon in variable name warnings", "warnings.single-colon-in-variable-name"),
            shown("disable unreachable code warnings", "warnings.unreachable-code"),
            new Option("long parse time warning threshold", "warnings.long-parse-time", value -> {
                try {
                    return NeoSkriptConfig.Interval.parse(value).toString();
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }, "0 seconds"),
            shown("disable hooks.vault", "hooks.vault"),
            shown("disable hooks.regions.worldguard", "hooks.worldguard"),
            shown("disable hooks.regions.grief prevention", "hooks.grief-prevention"),
            bool("enable legacy tab completion event compatibility", "syntax.legacy-tab-completion", true));

    /**
     * Options that change nothing on NeoSkript when set away from Skript's default config, and why, for the log:
     * by name in config.sk, with the value that matters.
     */
    private static final List<String[]> NOT_COPIED = List.of(
            new String[] {"use type properties", "false", "NeoSkript only has Skript's type property syntax (name of,"
                    + " amount of and the others), so scripts are read as with it on"},
            new String[] {"enable timings", "true", "Paper has had no Timings since 1.19.4, where Skript only warns."
                    + " Use spark, built into Paper, to see what scripts cost"},
            new String[] {"soft api exceptions", "true", "it turns two failures Skript stops on into errors: aliases"
                    + " without an item Skript's own code needs ('type ... not found'), and addons' mistakes registering"
                    + " types. NeoSkript names every Minecraft item after its id whatever the alias files have, and"
                    + " doesn't load Skript addons"},
            new String[] {"disable hooks.regions.precious stones", "true", "NeoSkript doesn't hook into PreciousStones"},
            new String[] {"disable hooks.regions.residence", "true", "NeoSkript doesn't hook into Residence"});

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
        Map<String, String> entries = SkriptConfigFile.read(SkriptConfigFile.text(config));
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

    /**
     * Whether config.sk turns Skript's {@code case-insensitive variables} off, so {@code {coins::Bob}} and
     * {@code {coins::bob}} are two variables there. NeoSkript's names always ignore case, as Skript's do by default.
     */
    static boolean caseSensitiveVariables(Path config) throws IOException {
        if (!Files.isRegularFile(config)) {
            return false;
        }
        String value = SkriptConfigFile.read(SkriptConfigFile.text(config))
                .get("case-insensitive variables");
        return value != null && Boolean.FALSE.equals(SkriptConfigFile.bool(value));
    }

    /**
     * Skript's settings in config.sk that NeoSkript leaves as they are, as lines for the log: those set away from
     * Skript's default config that change nothing here, with why.
     */
    static List<String> notCopied(Path config) throws IOException {
        if (!Files.isRegularFile(config)) {
            return List.of();
        }
        Map<String, String> entries = SkriptConfigFile.read(SkriptConfigFile.text(config));
        List<String> notes = new java.util.ArrayList<>();
        for (String[] option : NOT_COPIED) {
            String value = entries.get(option[0]);
            Boolean set = value == null ? null : SkriptConfigFile.bool(value);
            if (set != null && set.toString().equals(option[1])) {
                notes.add("Skript's '" + option[0] + ": " + value + "' isn't copied: " + option[2] + ".");
            }
        }
        return notes;
    }

    /** A {@code disable ...} option, which config.yml turns round: off there is on in config.sk. */
    private static Option shown(String skript, String path) {
        return new Option(skript, path, value -> {
            Boolean disabled = SkriptConfigFile.bool(value);
            return disabled == null ? null : !disabled;
        }, true);
    }

    private static Option bool(String skript, String path, boolean fallback) {
        return new Option(skript, path, SkriptConfigFile::bool, fallback);
    }

    /** A count, from 0 up as config.yml takes it: Skript shows nothing for a negative limit, as for 0. */
    private static Option count(String skript, String path, int fallback) {
        return new Option(skript, path, value -> integer(value) instanceof Integer n ? Math.max(n, 0) : null, fallback);
    }

    /** Skript's integers in config.sk: digits with {@code _} between them, maybe followed by degrees. */
    private static final java.util.regex.Pattern INTEGER =
            java.util.regex.Pattern.compile("(-?\\d+(?:_\\d+)*)(?: (?:in )?deg(?:ree)?s?)?");

    /**
     * A whole number as Skript reads one in config.sk ({@code 1_000}, {@code 90 degrees}), or {@code null}. Skript
     * would read {@code 2 radians} as a decimal it can't use, and an exponent fails it, so they're {@code null}.
     */
    static Integer integer(String value) {
        java.util.regex.Matcher matcher = INTEGER.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1).replace("_", ""));
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
