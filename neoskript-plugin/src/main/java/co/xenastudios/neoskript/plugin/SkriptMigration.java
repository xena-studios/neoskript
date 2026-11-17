/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.runtime.variables.storage.VariableStorage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Brings a Skript install's scripts, variables and alias files over to NeoSkript the first time it starts, so
 * replacing Skript.jar with NeoSkript.jar just works.
 *
 * <p>Files are copied, not moved, so Skript's folder is left as it was in case the server switches back.
 * It happens once: a marker file in NeoSkript's folder records it, and nothing is copied if NeoSkript
 * already has a scripts folder.
 */
final class SkriptMigration {

    static final String MARKER = ".migrated-from-skript";
    /**
     * Written before anything is copied, and replaced by {@link #MARKER} once config.yml has Skript's settings too: if
     * that write fails, the next start copies the settings again (but not the files that are there).
     */
    static final String STARTED = ".migrating-from-skript";
    /** Written once the notice for a server that moved before the databases section was copied has been shown. */
    static final String DATABASES_NOTICE = ".skript-databases-notice";
    /**
     * What's copied from {@code plugins/Skript}: the scripts, the saved variables, and the alias files Skript loads
     * with its default aliases (its own copy of them, {@code aliases-english.zip}, if it has one).
     */
    static final List<String> COPIED = List.of("scripts", "variables.csv", "aliases", "aliases-english.zip");

    /** What's copied when NeoSkript moved over before it read alias files: only those. */
    static final List<String> ALIASES = List.of("aliases", "aliases-english.zip");

    /**
     * What was copied, relative to the folders, for the log (empty if nothing was), Skript's
     * {@code use player UUIDs in variable names} from its config.sk, or {@code null} if it doesn't say, and its
     * other settings NeoSkript has, by their path in NeoSkript's config.yml ({@link #SETTINGS}), and the backup of
     * the variables copied, made before NeoSkript first loads them (or {@code null}), and what to tell the owner about
     * alias files that weren't copied, and whether Skript's alias files were all dealt with ({@code false} if some
     * couldn't be read or copied, so they're tried again on the next start).
     */
    record Result(List<String> copied, int scriptCount, Boolean playerUuids, Map<String, Object> settings,
            Path variablesBackup, List<String> notes, boolean aliasesDone) {

        Result(List<String> copied, int scriptCount, Boolean playerUuids) {
            this(copied, scriptCount, playerUuids, Map.of(), null, List.of(), true);
        }

        Result(List<String> copied, int scriptCount, Boolean playerUuids, Map<String, Object> settings,
                Path variablesBackup, List<String> notes) {
            this(copied, scriptCount, playerUuids, settings, variablesBackup, notes, true);
        }
    }

    /** What copying Skript's alias files did: what was copied, at least in part, and whether nothing failed. */
    private record AliasCopy(List<String> copied, boolean complete) {}

    /** The marker's line saying Skript's alias files were dealt with, so they're never copied again. */
    static final String ALIASES_DONE = "aliases: copied";

    /** Skript's {@code databases:} section, read from config.sk: each database's options as Skript wrote them. */
    record SkriptDatabase(String name, Map<String, String> options) {}

    /** Skript's config.sk options that NeoSkript's config.yml has too, by Skript's name, with NeoSkript's path. */
    static final Map<String, String> SETTINGS = Map.of(
            "load default aliases", "aliases.load-defaults",
            "enable effect commands", "effect-commands.enabled",
            "effect command token", "effect-commands.token",
            "allow ops to use effect commands", "effect-commands.allow-ops",
            "log effect commands", "effect-commands.log");

    private SkriptMigration() {}

    static Result migrate(Path skriptDir, Path neoskriptDir) throws IOException {
        if (Files.isDirectory(skriptDir) && Files.exists(neoskriptDir.resolve(MARKER))) {
            return aliasesLater(skriptDir, neoskriptDir);
        }
        boolean retry = Files.exists(neoskriptDir.resolve(STARTED));
        if (!Files.isDirectory(skriptDir) || (!retry && Files.exists(neoskriptDir.resolve("scripts")))) {
            return new Result(List.of(), 0, null);
        }
        Files.createDirectories(neoskriptDir);
        if (!retry) {
            Files.writeString(neoskriptDir.resolve(STARTED), "Copying from " + skriptDir.toAbsolutePath() + " since "
                    + Instant.now() + "\n");
        }
        List<String> copied = new ArrayList<>();
        int[] scripts = {0};
        List<SkriptDatabase> databases = databases(skriptDir.resolve("config.sk"));
        for (String name : List.of("scripts", "variables.csv")) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source) || (name.equals("variables.csv") && !databases.isEmpty())
                    || (retry && Files.exists(neoskriptDir.resolve(name)))) {
                continue;
            }
            copy(source, neoskriptDir.resolve(name), name.equals("scripts") ? scripts : new int[1]);
            copied.add(name);
        }
        Map<String, Object> settings = settings(skriptDir.resolve("config.sk"));
        Path backup = null;
        if (databases.isEmpty()) {
            // Before NeoSkript first loads or saves them, the variables Skript saved are backed up as they were.
            Path variables = neoskriptDir.resolve("variables.csv");
            backup = copied.contains("variables.csv") ? VariableStorage.backup(variables) : null;
        } else {
            Map<String, Object> section = new java.util.LinkedHashMap<>();
            for (SkriptDatabase database : databases) {
                Map<String, Object> options = new java.util.LinkedHashMap<>();
                database.options().forEach((key, value) -> options.put(key.replace(' ', '-'), value));
                Object file = options.get("file");
                if (file != null) {
                    String type = String.valueOf(options.getOrDefault("type", "")).strip().toLowerCase(java.util.Locale.ROOT);
                    Moved moved = moveFile(skriptDir, neoskriptDir, String.valueOf(file), type, copied);
                    options.put("file", moved.file());
                    if (moved.backup() != null && backup == null) {
                        backup = moved.backup();
                    }
                }
                section.put(database.name(), options);
            }
            settings.put("databases", section);
        }
        // The alias files last, and never at the cost of the rest: a file already there is kept, and one that can't
        // be copied is reported.
        List<String> notes = new ArrayList<>();
        AliasCopy aliases = copyAliases(skriptDir, neoskriptDir, notes);
        copied.addAll(aliases.copied());
        // The marker is written by finish(), once config.yml has the settings.
        return new Result(copied, scripts[0], playerUuids(skriptDir.resolve("config.sk")), settings, backup,
                List.copyOf(notes), aliases.complete());
    }

    /**
     * Copies Skript's alias files ({@link #ALIASES}) that are there, file by file, keeping any NeoSkript already has
     * (the owner's). A file or folder that can't be read or copied is named in {@code notes} and the rest are still
     * copied. Never throws. Returns what was copied, at least in part, and whether nothing failed.
     */
    private static AliasCopy copyAliases(Path skriptDir, Path neoskriptDir, List<String> notes) {
        List<String> copied = new ArrayList<>();
        boolean complete = true;
        for (String name : ALIASES) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            Path target = neoskriptDir.resolve(name);
            AliasFiles files = new AliasFiles(source, target, neoskriptDir, notes);
            try {
                // A walk that goes on past what it can't read, rather than one that fails as a whole.
                Files.walkFileTree(source, files);
            } catch (IOException | RuntimeException e) {
                notes.add("Couldn't copy Skript's " + name + ": " + e);
                files.failed = true;
            }
            if (files.any) {
                copied.add(name);
            }
            complete &= !files.failed;
        }
        if (!complete) {
            notes.add("Skript's other alias files were copied. What couldn't be is tried again the next time the server"
                    + " starts.");
        }
        return new AliasCopy(copied, complete);
    }

    /** Copies one of Skript's alias folders (or its zip), file by file, noting what it can't. */
    private static final class AliasFiles extends java.nio.file.SimpleFileVisitor<Path> {
        private final Path source;
        private final Path target;
        private final Path neoskriptDir;
        private final List<String> notes;
        /** Whether a file was copied, and whether something couldn't be read or copied. */
        boolean any;
        boolean failed;

        AliasFiles(Path source, Path target, Path neoskriptDir, List<String> notes) {
            this.source = source;
            this.target = target;
            this.neoskriptDir = neoskriptDir;
            this.notes = notes;
        }

        private Path to(Path file) {
            return file.equals(source) ? target : target.resolve(source.relativize(file).toString());
        }

        private String shown(Path to) {
            return neoskriptDir.relativize(to).toString().replace('\\', '/');
        }

        @Override
        public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
            try {
                // A folder alone isn't something copied: only files count.
                Files.createDirectories(to(dir));
                return java.nio.file.FileVisitResult.CONTINUE;
            } catch (IOException | RuntimeException e) {
                notes.add("Couldn't copy Skript's " + shown(to(dir)) + ": " + e);
                failed = true;
                return java.nio.file.FileVisitResult.SKIP_SUBTREE;
            }
        }

        @Override
        public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
            Path to = to(file);
            try {
                if (Files.exists(to)) {
                    // The same file is one copied before (a move tried again): nothing to say.
                    if (Files.mismatch(file, to) != -1) {
                        notes.add("Kept plugins/NeoSkript/" + shown(to) + ", which was there already, rather than Skript's");
                    }
                } else {
                    Files.copy(file, to, StandardCopyOption.COPY_ATTRIBUTES);
                    any = true;
                }
            } catch (IOException | RuntimeException e) {
                notes.add("Couldn't copy Skript's " + shown(to) + ": " + e);
                failed = true;
            }
            return java.nio.file.FileVisitResult.CONTINUE;
        }

        @Override
        public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException e) {
            // A file or folder that can't be read: named, and the rest still copied.
            notes.add("Couldn't read Skript's " + shown(to(file)) + ": " + e);
            failed = true;
            return java.nio.file.FileVisitResult.CONTINUE;
        }

        @Override
        public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException e) {
            if (e != null) {
                notes.add("Couldn't read all of Skript's " + shown(to(dir)) + ": " + e);
                failed = true;
            }
            return java.nio.file.FileVisitResult.CONTINUE;
        }
    }

    /**
     * For a server that moved over before NeoSkript read alias files: Skript's alias files and its
     * {@code load default aliases}, as a first move copies them. Alias files NeoSkript already has (the owner's) are
     * kept, and Skript's others are added. The marker records it once everything was dealt with, so deleting them
     * later doesn't bring them back; if something couldn't be copied, it's tried again on the next start.
     */
    private static Result aliasesLater(Path skriptDir, Path neoskriptDir) throws IOException {
        Path marker = neoskriptDir.resolve(MARKER);
        String recorded = Files.readString(marker);
        if (recorded.lines().anyMatch(ALIASES_DONE::equals)) {
            return new Result(List.of(), 0, null);
        }
        List<String> notes = new ArrayList<>();
        AliasCopy aliases = copyAliases(skriptDir, neoskriptDir, notes);
        List<String> copied = aliases.copied();
        Files.writeString(marker, recorded + (copied.isEmpty() ? "" : "Copied " + copied + " from "
                + skriptDir.toAbsolutePath() + " at " + Instant.now() + "\n") + (aliases.complete() ? ALIASES_DONE + "\n" : ""));
        if (copied.isEmpty()) {
            return new Result(List.of(), 0, null, Map.of(), null, List.copyOf(notes), aliases.complete());
        }
        Map<String, Object> settings = new java.util.LinkedHashMap<>(settings(skriptDir.resolve("config.sk")));
        settings.keySet().retainAll(List.of("aliases.load-defaults"));
        return new Result(copied, 0, null, settings, null, List.copyOf(notes), aliases.complete());
    }

    /** Where a database's file is for NeoSkript, and the backup of what was copied. */
    private record Moved(String file, Path backup) {}

    /**
     * A database file config.sk names. One in Skript's folder is copied into NeoSkript's (CSV and SQLite files, with
     * SQLite's journal, once each, a backup made first), and the database then names the copy, relative to
     * NeoSkript's folder. One anywhere else, which servers may share, is left where it is and named by its full path.
     * A disabled database's file isn't copied.
     */
    private static Moved moveFile(Path skriptDir, Path neoskriptDir, String file, String type, List<String> copied)
            throws IOException {
        boolean sqlite = type.equals("sqlite");
        // As Skript: an SQLite file always ends in .db.
        String name = sqlite && !file.endsWith(".db") ? file + ".db" : file;
        Path server = skriptDir.toAbsolutePath().getParent().getParent();
        Path source = Path.of(name);
        source = (source.isAbsolute() ? source : server.resolve(source)).normalize();
        Path skript = skriptDir.toAbsolutePath().normalize();
        if (!source.startsWith(skript)) {
            return new Moved(source.toString(), null);
        }
        String relative = skript.relativize(source).toString().replace('\\', '/');
        Path target = neoskriptDir.resolve(relative);
        boolean csv = type.equals("csv") || type.equals("file") || type.equals("flatfile");
        if (!(csv || sqlite) || !Files.isRegularFile(source) || copied.contains(relative) || Files.exists(target)) {
            // A copy that's there already is from an earlier start that didn't finish, and may be in use: kept.
            return new Moved(relative, null);
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        if (sqlite) {
            for (String suffix : List.of("-journal", "-wal", "-shm")) {
                Path extra = source.resolveSibling(source.getFileName() + suffix);
                if (Files.isRegularFile(extra)) {
                    Files.copy(extra, target.resolveSibling(target.getFileName() + suffix),
                            StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        copied.add(relative);
        // Before NeoSkript first loads or saves it, the copy is backed up as Skript left it.
        return new Moved(relative, VariableStorage.backup(target));
    }

    /**
     * Marks the move from Skript done, once config.yml has Skript's settings: it never happens again. Until then, each
     * start copies the settings again.
     */
    static void finish(Path neoskriptDir, Path skriptDir, List<String> copied) throws IOException {
        finish(neoskriptDir, skriptDir, copied, true);
    }

    /**
     * Writes the marker once the first move is done. Without {@code aliasesDone} (some of Skript's alias files couldn't
     * be read or copied), it doesn't say the alias files were dealt with, so the next start tries them again.
     */
    static void finish(Path neoskriptDir, Path skriptDir, List<String> copied, boolean aliasesDone) throws IOException {
        if (!Files.exists(neoskriptDir.resolve(STARTED))) {
            return;
        }
        Files.writeString(neoskriptDir.resolve(MARKER), "Copied " + copied + " from " + skriptDir.toAbsolutePath()
                + " at " + Instant.now() + "\n" + (aliasesDone ? ALIASES_DONE + "\n" : ""));
        Files.deleteIfExists(neoskriptDir.resolve(STARTED));
    }

    /**
     * For a server that moved from Skript before NeoSkript copied the {@code databases:} section: if config.sk has SQL
     * databases and config.yml has none, what to tell the owner, once. {@code null} if there's nothing to say.
     */
    static String databasesNotice(Path skriptDir, Path neoskriptDir, boolean configHasSql) throws IOException {
        if (configHasSql || !Files.exists(neoskriptDir.resolve(MARKER))
                || Files.exists(neoskriptDir.resolve(DATABASES_NOTICE))) {
            return null;
        }
        List<String> sql = new ArrayList<>();
        for (SkriptDatabase database : databases(skriptDir.resolve("config.sk"))) {
            String type = database.options().getOrDefault("type", "").strip().toLowerCase(java.util.Locale.ROOT);
            if (type.equals("sqlite") || type.equals("mysql")) {
                sql.add(database.name() + " (" + type + ")");
            }
        }
        if (sql.isEmpty()) {
            return null;
        }
        Files.writeString(neoskriptDir.resolve(DATABASES_NOTICE), "Shown " + Instant.now() + "\n");
        return "Skript's config.sk has SQL databases (" + String.join(", ", sql) + ") that this server's config.yml "
                + "doesn't: it moved from Skript before NeoSkript copied the databases section. Their variables aren't "
                + "loaded. To use them, copy them under databases: in config.yml (Skript's option names work there) "
                + "and restart. See https://neoskript.dev/docs/server/variables/#databases. This is said once.";
    }

    /**
     * The {@code databases:} section of Skript's config.sk, in order, each database's options by Skript's names
     * ({@code monitor changes}). Empty if there's no config.sk or no such section.
     */
    static List<SkriptDatabase> databases(Path config) throws IOException {
        List<SkriptDatabase> databases = new ArrayList<>();
        if (!Files.isRegularFile(config)) {
            return databases;
        }
        boolean inside = false;
        String sectionIndent = null;
        String name = null;
        Map<String, String> options = null;
        for (String line : Files.readAllLines(config)) {
            String content = value(line);
            if (content.isEmpty()) {
                continue;
            }
            String indent = line.substring(0, line.length() - line.stripLeading().length());
            if (!inside) {
                inside = indent.isEmpty() && content.equals("databases:");
                continue;
            }
            if (indent.isEmpty()) {
                break;
            }
            if (sectionIndent == null || indent.equals(sectionIndent)) {
                sectionIndent = indent;
                if (name != null) {
                    databases.add(new SkriptDatabase(name, options));
                }
                name = content.endsWith(":") ? content.substring(0, content.length() - 1).strip() : null;
                options = new java.util.LinkedHashMap<>();
                continue;
            }
            int colon = content.indexOf(':');
            if (name != null && colon > 0) {
                options.put(content.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT),
                        content.substring(colon + 1).strip());
            }
        }
        if (name != null) {
            databases.add(new SkriptDatabase(name, options));
        }
        return databases;
    }

    /**
     * The {@link #SETTINGS} Skript's config.sk has, by NeoSkript's path: booleans for its yes/no options, and the
     * text of the others. Options it doesn't have, or whose value isn't valid, are left out.
     */
    static Map<String, Object> settings(Path config) throws IOException {
        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        if (!Files.isRegularFile(config)) {
            return settings;
        }
        for (String line : Files.readAllLines(config)) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String path = SETTINGS.get(line.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT));
            if (path == null || settings.containsKey(path)) {
                continue;
            }
            String value = value(line.substring(colon + 1));
            if (path.equals("effect-commands.token")) {
                if (!value.isEmpty()) {
                    settings.put(path, value);
                }
                continue;
            }
            switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "true", "yes", "on" -> settings.put(path, Boolean.TRUE);
                case "false", "no", "off" -> settings.put(path, Boolean.FALSE);
                default -> {}
            }
        }
        return settings;
    }

    /** An option's value as Skript's config reads it: up to a {@code #} comment, where {@code ##} is a {@code #}. */
    private static String value(String text) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '#') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '#') {
                    value.append('#');
                    i++;
                    continue;
                }
                break;
            }
            value.append(c);
        }
        return value.toString().strip();
    }

    /** Skript's {@code use player UUIDs in variable names} option, if its config has it. */
    static Boolean playerUuids(Path config) throws IOException {
        if (!Files.isRegularFile(config)) {
            return null;
        }
        for (String line : Files.readAllLines(config)) {
            String option = line.strip();
            if (option.startsWith("use player UUIDs in variable names:")) {
                String value = option.substring(option.indexOf(':') + 1).strip().toLowerCase(java.util.Locale.ROOT);
                int comment = value.indexOf('#');
                value = (comment >= 0 ? value.substring(0, comment) : value).strip();
                return switch (value) {
                    case "true", "yes", "on" -> Boolean.TRUE;
                    case "false", "no", "off" -> Boolean.FALSE;
                    default -> null;
                };
            }
        }
        return null;
    }

    private static void copy(Path source, Path target, int[] scripts) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                if (file.getFileName().toString().endsWith(".sk")) {
                    scripts[0]++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
