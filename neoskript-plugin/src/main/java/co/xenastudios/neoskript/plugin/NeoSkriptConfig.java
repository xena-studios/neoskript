/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.plugin.config.Codec;
import co.xenastudios.neoskript.plugin.config.Comment;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import co.xenastudios.neoskript.plugin.config.ConfigSpec;
import co.xenastudios.neoskript.plugin.config.Range;
import co.xenastudios.neoskript.plugin.text.TextStyle;
import co.xenastudios.neoskript.plugin.text.Theme;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.EventPriority;

/**
 * config.yml, declared once: each setting's default, what's valid, and its comment. The same declaration writes
 * the default file, reads and checks the live one, and carries old files forward.
 *
 * <p>Version 1 is the file from before config.yml had a version, the theme and this layout: the same settings,
 * with the same meanings. Carrying it forward keeps every value and adds the theme. Version 3 moved
 * {@code variables.file} and {@code variables.storage} into the {@code databases} section, as its {@code default}
 * database.
 */
public record NeoSkriptConfig(
        @Comment({
            "The look of NeoSkript's messages: /sk, reload reports and update notices. They use these as tags, so",
            "changing a colour here restyles them all. Write a style as MiniMessage tags: <gray>, <#FF4D9D>, or",
            "<gold><bold>. Script problems and runtime errors keep Skript's own look, which tools read."})
        Theme theme,
        @Comment({
            "Send anonymous usage statistics to bStats: https://bstats.org/plugin/bukkit/NeoSkript/34868",
            "You can also turn bStats off for every plugin in plugins/bStats/config.yml. Applies on restart."})
        boolean metrics,
        @Comment({
            "Check Modrinth for newer NeoSkript versions when the server starts and once a day. A newer version is",
            "logged to the console and shown to players with skript.admin when they join. Development builds never check."})
        boolean updateChecker,
        Aliases aliases,
        @Comment({
            "The language of the messages scripts show from Skript's language files, as Skript's 'language': what",
            "nothing is in text (<none>), a command's default cooldown message, and how a script's problems are",
            "reported when it loads ('Line 5: (script.sk)'). Skript's languages are built in:",
            "english, german, french, spanish, dutch, polish, russian, turkish, catalan, japanese, korean and",
            "simplifiedchinese. A <name>.lang file in plugins/NeoSkript/lang adds a language, or changes one's",
            "messages. NeoSkript's own messages (/sk, reloads) are in English."})
        String language,
        Text text,
        Variables variables,
        @Comment({
            "Where global variables are saved, as the databases: section of Skript's config.sk. Each variable is saved",
            "in the first database, from the top, whose pattern (a regular expression) matches its whole name, and in no",
            "other. Variables whose names start with - are never saved. Types: csv (Skript's variables.csv), native",
            "(NeoSkript's own file, variables.nsv beside the file named), sqlite, mysql, or disabled. Files are relative",
            "to plugins/NeoSkript. Moving over from Skript copies its section and the files it names. Applies on restart.",
            "Every option, and how to share variables between servers: https://neoskript.dev/docs/server/variables/"})
        DatabaseSettings databases,
        Syntax syntax,
        Events events,
        RuntimeErrors runtimeErrors,
        EffectCommands effectCommands) {

    static final int VERSION = 3;

    static final NeoSkriptConfig DEFAULTS = new NeoSkriptConfig(
            Theme.DEFAULTS,
            true,
            true,
            new Aliases(true),
            "english",
            new Text(DatePattern.DEFAULT, 2, false, true,
                    co.xenastudios.neoskript.paper.TextComponentParser.DEFAULT_SAFE_TAGS, LinkParsing.DISABLED),
            new Variables(false, Interval.parse("5 minutes"), true, true),
            DatabaseSettings.DEFAULTS,
            new Syntax(100, NamePattern.DEFAULT),
            new Events(Priority.HIGH, false),
            new RuntimeErrors(Interval.parse("1 second"), 8, 2, 4, 10, 8, 2, 4, 10),
            new EffectCommands(false, "!", false, false));

    static final ConfigSpec<NeoSkriptConfig> SPEC = ConfigSpec.builder(NeoSkriptConfig.class, DEFAULTS)
            .header(
                    "NeoSkript's config. Docs: https://neoskript.dev/docs/configuration/",
                    "",
                    "Apply changes with /sk reload config. Most settings apply at once; those that say so wait for a",
                    "restart. A bad value falls back to its default, with a warning in the console.")
            .version(VERSION)
            .unversioned(1)
            // A file with databases is current even without config-version, such as one an owner wrote by hand. One
            // with only a theme is version 2, carried forward as version 1 is.
            .currentWith("databases")
            // Version 1 had the same settings, without the theme, which loading adds with its defaults.
            .migrate(1, root -> {})
            // Version 2 had variables.file and variables.storage: they're the default database now.
            .migrate(2, new co.xenastudios.neoskript.plugin.config.Migration() {
                @Override
                public void apply(org.bukkit.configuration.ConfigurationSection root) {
                    moveToDatabases(root, new ArrayList<>());
                }

                @Override
                public void apply(org.bukkit.configuration.ConfigurationSection root, List<String> warnings) {
                    moveToDatabases(root, warnings);
                }
            })
            .section(DatabaseSettings.class, DatabaseSettings.CODEC)
            .codec(TextStyle.class, TextStyle.CODEC)
            .codec(Interval.class, Interval.CODEC)
            .codec(DatePattern.class, DatePattern.CODEC)
            .codec(NamePattern.class, NamePattern.CODEC)
            .codec(LinkParsing.class, LinkParsing.CODEC)
            .build();

    public record Aliases(
            @Comment({
                "Load Skript's default aliases, as Skript's 'load default aliases' in config.sk. Off, items are only named",
                "after their Minecraft ids (red dye) and by the alias files in plugins/NeoSkript/aliases, so some, like",
                "potions of each kind, have no names. Moving over from Skript copies its setting. Applies on",
                "/sk reload aliases (which reads it from this file), /sk reload all or restart."})
            boolean loadDefaults) {}

    /** How scripts write and compare text, as Skript's options of the same names. All apply on /sk reload config. */
    public record Text(
            @Comment({
                "How dates are written as text, as Skript's 'date format': default, the server locale's short date and",
                "time (1/15/24, 10:30 AM), or a pattern such as yyyy-MM-dd HH:mm, written as Java's SimpleDateFormat:",
                "https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html"})
            DatePattern dateFormat,
            @Comment({
                "Skript's 'number accuracy': the most decimal places numbers are written with as text, without",
                "trailing zeros (2 writes 1/3 as 0.33, and 0.5 as 0.5). 0 or below rounds to whole numbers, and below 0",
                "'numbers from X to Y' steps by 10s, 100s and so on, as in Skript. From -1000 to 1000."})
            @Range(min = -1000, max = 1000) int numberAccuracy,
            @Comment({
                "Skript's 'case sensitive': whether comparing texts, 'contains', replace, split and finding text heed",
                "case, so \"Hello\" isn't \"hello\". Replace and split read it when a script loads."})
            boolean caseSensitive,
            @Comment({
                "Skript's 'color codes reset formatting': whether a colour (&a, <red>) turns off the bold, italics and",
                "so on before it, as in Minecraft's own codes."})
            boolean colorCodesResetFormatting,
            @Comment({
                "Skript's 'safe tags': the MiniMessage tags read in text that isn't the script's own, such as a",
                "player's chat message made 'colored'. Tag names, or groups of them (color, decorations). Other tags,",
                "such as click events, are only read in a script's own text."})
            List<String> safeTags,
            @Comment({
                "Skript's 'parse links in chat messages': whether text that looks like a link is clickable. disabled,",
                "strict (only http:// and https:// links) or lenient (anything with a dot, like example.com)."})
            LinkParsing parseLinks) {}

    /** Settings for particular syntax, as Skript's options of the same names. All apply on /sk reload config. */
    public record Syntax(
            @Comment({
                "Skript's 'maximum target block distance': how many blocks away 'targeted block', 'target' and",
                "'blocks in front of' look, from 0 to 1000."})
            @Range(min = 0, max = 1000) int targetBlockDistance,
            @Comment({
                "Skript's 'player name regex pattern': the text that can be read as an offline player's name, as a",
                "Java regular expression. Names that don't match aren't players (UUIDs always are)."})
            NamePattern playerNamePattern) {}

    public record Variables(
            @Comment({
                "Native storage only. If variables.nsv is damaged where no snapshot in backups/snapshots can repair it,",
                "NeoSkript refuses to load it. Set this to true and restart to load everything up to the last good save",
                "instead: this turns itself off first, then the damaged file is backed up and what's lost is logged."})
            boolean recover,
            @Comment({
                "How often changed variables are saved to csv and native databases, at least every 10 seconds. They're",
                "also saved when the server stops. SQL databases write changes within a second or so."})
            Interval saveInterval,
            @Comment({
                "Whether players are their UUIDs in variable names ({money::%player%}) or their names, as Skript's",
                "'use player UUIDs in variable names' (on in Skript's default config). Moving over from Skript copies",
                "its setting, so the variables it saved are found."})
            boolean playerUuidsInNames,
            @Comment({
                "Skript's 'player variable fix': a player in a variable who leaves and joins again is read as the",
                "player now online. Off, it stays the old player, whom messages and changes no longer reach."})
            boolean playerVariableFix) {}

    public record Events(
            @Comment({
                "The priority scripts listen to events at: lowest, low, normal, high, highest or monitor. As in Skript,",
                "the default is high, so scripts see what most other plugins did to an event, and can still cancel it.",
                "Applies on restart."})
            Priority priority,
            @Comment("Whether 'on <event>' triggers also run for events another plugin cancelled. Applies on restart.")
            boolean listenToCancelled) {}

    public record RuntimeErrors(
            @Comment({
                "As Skript's 'runtime errors' in config.sk. Frames are the duration over which runtime errors are",
                "collected; at the end of each, what wasn't printed is summed up."})
            Interval frameDuration,
            @Comment("The most errors printed per frame; more are hidden, and counted at the end of the frame.")
            @Range(min = 0) int totalErrorsPerFrame,
            @Comment("The most errors one line prints per frame, so a line in a loop can't fill the frame.")
            @Range(min = 0) int errorsFromOneLinePerFrame,
            @Comment("A line with this many errors in a frame is put in timeout: its errors are hidden for a while.")
            @Range(min = 0) int errorSpamTimeoutLimit,
            @Comment("How many frames a line stays in timeout.")
            @Range(min = 0) int errorTimeoutLength,
            @Comment("The same for warnings.")
            @Range(min = 0) int totalWarningsPerFrame,
            @Range(min = 0) int warningsFromOneLinePerFrame,
            @Range(min = 0) int warningSpamTimeoutLimit,
            @Range(min = 0) int warningTimeoutLength) {}

    public record EffectCommands(
            @Comment({
                "Skript's effect commands, as its 'enable effect commands': console commands and chat messages starting with",
                "the token are effects, run straight away, like '!set time to 6:00'. Players need the permission",
                "skript.effectcommands, which ops don't have unless allow-ops is on, as these commands can do anything.",
                "Moving over from Skript copies its settings for them."})
            boolean enabled,
            @Comment("Skript's 'effect command token'. It can be longer than a character, but shouldn't start with / (a command).")
            String token,
            @Comment("Skript's 'allow ops to use effect commands': ops can use them without the permission.")
            boolean allowOps,
            @Comment("Skript's 'log effect commands': players' effect commands are logged as '<player> issued effect command: <command>'.")
            boolean log) {}

    /** Skript's {@code parse links in chat messages}: {@code disabled}, {@code strict} or {@code lenient}. */
    public enum LinkParsing {
        DISABLED, STRICT, LENIENT;

        static final Codec<LinkParsing> CODEC = new Codec<>() {
            @Override
            public LinkParsing decode(Object raw) {
                LinkParsing parsing = parse(raw.toString());
                if (parsing == null) {
                    throw new IllegalArgumentException("should be disabled, strict or lenient");
                }
                return parsing;
            }

            @Override
            public Object encode(LinkParsing value) {
                return value.name().toLowerCase(java.util.Locale.ROOT);
            }
        };

        /** As Skript reads the option, where {@code true} is lenient and {@code false} disabled, or {@code null}. */
        static LinkParsing parse(String text) {
            return switch (text.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "false", "disabled" -> DISABLED;
                case "true", "lenient" -> LENIENT;
                case "strict" -> STRICT;
                default -> null;
            };
        }

        co.xenastudios.neoskript.paper.TextComponentParser.LinkParseMode mode() {
            return co.xenastudios.neoskript.paper.TextComponentParser.LinkParseMode.valueOf(name());
        }
    }

    /** Skript's {@code date format}: {@code default} or a {@link java.text.SimpleDateFormat} pattern, kept as written. */
    public record DatePattern(String text) {

        static final DatePattern DEFAULT = new DatePattern("default");

        static final Codec<DatePattern> CODEC = new Codec<>() {
            @Override
            public DatePattern decode(Object raw) {
                return parse(raw.toString());
            }

            @Override
            public Object encode(DatePattern value) {
                return value.text;
            }
        };

        /**
         * @throws IllegalArgumentException if it isn't {@code default} or a valid pattern
         */
        static DatePattern parse(String text) {
            String pattern = text.strip();
            if (!pattern.equalsIgnoreCase("default")) {
                try {
                    new java.text.SimpleDateFormat(pattern);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("isn't a date format: " + e.getMessage());
                }
            }
            return new DatePattern(pattern);
        }
    }

    /** Skript's {@code player name regex pattern}: a regular expression, kept as written. */
    public static final class NamePattern {

        static final NamePattern DEFAULT = parse(co.xenastudios.neoskript.runtime.Settings.DEFAULT_PLAYER_NAME_PATTERN);

        static final Codec<NamePattern> CODEC = new Codec<>() {
            @Override
            public NamePattern decode(Object raw) {
                return parse(raw.toString());
            }

            @Override
            public Object encode(NamePattern value) {
                return value.pattern.pattern();
            }
        };

        private final java.util.regex.Pattern pattern;

        private NamePattern(java.util.regex.Pattern pattern) {
            this.pattern = pattern;
        }

        /**
         * @throws IllegalArgumentException if it isn't a valid regular expression
         */
        static NamePattern parse(String text) {
            try {
                return new NamePattern(java.util.regex.Pattern.compile(text));
            } catch (java.util.regex.PatternSyntaxException | StackOverflowError e) {
                throw new IllegalArgumentException("isn't a regular expression: "
                        + (e.getMessage() == null ? e : e.getMessage().lines().findFirst().orElse("")));
            }
        }

        java.util.regex.Pattern pattern() {
            return pattern;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NamePattern that && pattern.pattern().equals(that.pattern.pattern());
        }

        @Override
        public int hashCode() {
            return pattern.pattern().hashCode();
        }

        @Override
        public String toString() {
            return pattern.pattern();
        }
    }

    /** The priorities Bukkit has, as config.yml writes them. */
    public enum Priority {
        LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR;

        EventPriority bukkit() {
            return EventPriority.valueOf(name());
        }
    }

    /** A length of time as Skript writes one, such as {@code 5 minutes}, kept as written. */
    public static final class Interval {

        static final Codec<Interval> CODEC = new Codec<>() {
            @Override
            public Interval decode(Object raw) {
                return parse(raw.toString());
            }

            @Override
            public Object encode(Interval value) {
                return value.text;
            }
        };

        private final String text;
        private final Timespan timespan;

        private Interval(String text, Timespan timespan) {
            this.text = text;
            this.timespan = timespan;
        }

        /**
         * @throws IllegalArgumentException if it isn't a length of time
         */
        static Interval parse(String text) {
            Timespan timespan = Timespan.parse(text.strip());
            if (timespan == null || timespan.isInfinite()) {
                throw new IllegalArgumentException("isn't a length of time, such as 5 minutes or 1 second");
            }
            return new Interval(text.strip(), timespan);
        }

        long millis() {
            return timespan.millis();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Interval that && timespan.equals(that.timespan);
        }

        @Override
        public int hashCode() {
            return timespan.hashCode();
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Version 2 to 3: {@code variables.file} and {@code variables.storage} become the {@code default} database, with
     * the same file and type.
     */
    /**
     * Whether NeoSkript's own file is in use beside a variables file, by its path in config.yml: set by the plugin
     * before config.yml is read, so carrying an unknown {@code variables.storage} forward keeps the file in use, as
     * version 2 did.
     */
    static volatile java.util.function.Predicate<String> nativeInUse = file -> false;

    private static void moveToDatabases(org.bukkit.configuration.ConfigurationSection root, List<String> warnings) {
        Object file = root.get("variables.file");
        Object storage = root.get("variables.storage");
        root.set("variables.file", null);
        root.set("variables.storage", null);
        if (root.contains("databases")) {
            return;
        }
        java.util.Map<String, Object> database = new java.util.LinkedHashMap<>(
                DatabaseSettings.settings(DatabaseSettings.DEFAULTS.all().getFirst()));
        if (!ConfigLoader.isBlank(file) && !String.valueOf(file).isBlank()) {
            database.put("file", String.valueOf(file).strip());
        }
        if (!ConfigLoader.isBlank(storage) && !String.valueOf(storage).isBlank()) {
            String text = String.valueOf(storage).strip();
            String type = switch (text.toLowerCase(java.util.Locale.ROOT)) {
                case "csv", "native" -> text.toLowerCase(java.util.Locale.ROOT);
                default -> null;
            };
            if (type == null) {
                // As version 2 did with an unknown storage: keep whichever file is in use, rather than move the
                // variables because of a typo.
                type = nativeInUse.test(String.valueOf(database.get("file"))) ? "native" : "csv";
                warnings.add("Unknown variables.storage '" + text + "' in config.yml (csv or native); the default "
                        + "database's type is " + type + ", the file in use.");
            }
            database.put("type", type);
        }
        root.createSection("databases").createSection("default", database);
    }

    /**
     * Sets {@code variables.recover} back to false once a recovery has run: through the loader, so the file keeps its
     * comments and other keys, and a file from a newer NeoSkript is left as it is.
     */
    static ConfigLoader.Change turnOffRecover(java.nio.file.Path file)
            throws co.xenastudios.neoskript.plugin.config.ConfigException {
        return ConfigLoader.set(SPEC, file, java.util.Map.of("variables.recover", false));
    }

    /**
     * The settings that only apply when the server restarts and that differ from {@code running}, the settings the
     * server started with.
     */
    List<String> restartOnlyChanges(NeoSkriptConfig running) {
        NeoSkriptConfig before = running;
        List<String> changed = new ArrayList<>();
        if (metrics != before.metrics) {
            changed.add("metrics");
        }
        if (!databases.equals(before.databases)) {
            changed.add("databases");
        }
        if (variables.recover != before.variables.recover) {
            changed.add("variables.recover");
        }
        if (events.priority != before.events.priority) {
            changed.add("events.priority");
        }
        if (events.listenToCancelled != before.events.listenToCancelled) {
            changed.add("events.listen-to-cancelled");
        }
        return changed;
    }
}
