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
 * with the same meanings. Carrying it forward keeps every value and adds the theme.
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
        Variables variables,
        Events events,
        RuntimeErrors runtimeErrors,
        EffectCommands effectCommands) {

    static final int VERSION = 2;

    static final NeoSkriptConfig DEFAULTS = new NeoSkriptConfig(
            Theme.DEFAULTS,
            true,
            true,
            new Aliases(true),
            new Variables("variables.csv", "csv", false, Interval.parse("5 minutes"), true),
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
            // A file with a theme is current even without config-version, such as one an owner wrote by hand.
            .currentWith("theme")
            // Version 1 had the same settings, without the theme, which loading adds with its defaults.
            .migrate(1, root -> {})
            .codec(TextStyle.class, TextStyle.CODEC)
            .codec(Interval.class, Interval.CODEC)
            .build();

    public record Aliases(
            @Comment({
                "Load Skript's default aliases, as Skript's 'load default aliases' in config.sk. Off, items are only named",
                "after their Minecraft ids (red dye) and by the alias files in plugins/NeoSkript/aliases, so some, like",
                "potions of each kind, have no names. Moving over from Skript copies its setting. Applies on",
                "/sk reload aliases (which reads it from this file), /sk reload all or restart."})
            boolean loadDefaults) {}

    public record Variables(
            @Comment({
                "Where global variables are saved, in Skript's variables.csv format (Skript can read it too).",
                "Relative to plugins/NeoSkript. Variables whose names start with - aren't saved. Applies on restart."})
            String file,
            @Comment({
                "csv saves to the file above, as Skript does. native saves to NeoSkript's own file beside it",
                "(variables.nsv): faster to load, each save writes only what changed, and a crash can't cut a save in",
                "half. Switching moves the variables over at the next start, after a backup in backups/. Switch back to",
                "csv to write variables.csv again, for example before downgrading or going back to Skript. Applies on",
                "restart. Docs: https://neoskript.dev/docs/server/variables/"})
            String storage,
            @Comment({
                "Native storage only. If variables.nsv is damaged where no snapshot in backups/snapshots can repair it,",
                "NeoSkript refuses to load it. Set this to true and restart to load everything up to the last good save",
                "instead: this turns itself off first, then the damaged file is backed up and what's lost is logged."})
            boolean recover,
            @Comment("How often changed variables are saved, at least every 10 seconds. They're also saved when the server stops.")
            Interval saveInterval,
            @Comment({
                "Whether players are their UUIDs in variable names ({money::%player%}) or their names, as Skript's",
                "'use player UUIDs in variable names' (on in Skript's default config). Moving over from Skript copies",
                "its setting, so the variables it saved are found."})
            boolean playerUuidsInNames) {}

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
        if (!variables.file.equals(before.variables.file)) {
            changed.add("variables.file");
        }
        if (!variables.storage.equals(before.variables.storage)) {
            changed.add("variables.storage");
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
