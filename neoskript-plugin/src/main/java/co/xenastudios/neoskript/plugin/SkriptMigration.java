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
 * Brings a Skript install's scripts and variables over to NeoSkript the first time it starts, so
 * replacing Skript.jar with NeoSkript.jar just works.
 *
 * <p>Files are copied, not moved, so Skript's folder is left as it was in case the server switches back.
 * It happens once: a marker file in NeoSkript's folder records it, and nothing is copied if NeoSkript
 * already has a scripts folder.
 */
final class SkriptMigration {

    static final String MARKER = ".migrated-from-skript";
    /** What's copied from {@code plugins/Skript}: the scripts and the saved variables. */
    static final List<String> COPIED = List.of("scripts", "variables.csv");

    /**
     * What was copied, relative to the folders, for the log (empty if nothing was), Skript's
     * {@code use player UUIDs in variable names} from its config.sk, or {@code null} if it doesn't say, and its
     * other settings NeoSkript has, by their path in NeoSkript's config.yml ({@link #SETTINGS}), and the backup of
     * the variables copied, made before NeoSkript first loads them (or {@code null}).
     */
    record Result(List<String> copied, int scriptCount, Boolean playerUuids, Map<String, Object> settings,
            Path variablesBackup) {

        Result(List<String> copied, int scriptCount, Boolean playerUuids) {
            this(copied, scriptCount, playerUuids, Map.of(), null);
        }
    }

    /** Skript's {@code databases:} section, read from config.sk: each database's options as Skript wrote them. */
    record SkriptDatabase(String name, Map<String, String> options) {}

    /** Skript's config.sk options that NeoSkript's config.yml has too, by Skript's name, with NeoSkript's path. */
    static final Map<String, String> SETTINGS = Map.of(
            "enable effect commands", "effect-commands.enabled",
            "effect command token", "effect-commands.token",
            "allow ops to use effect commands", "effect-commands.allow-ops",
            "log effect commands", "effect-commands.log");

    private SkriptMigration() {}

    static Result migrate(Path skriptDir, Path neoskriptDir) throws IOException {
        if (!Files.isDirectory(skriptDir) || Files.exists(neoskriptDir.resolve(MARKER))
                || Files.exists(neoskriptDir.resolve("scripts"))) {
            return new Result(List.of(), 0, null);
        }
        Files.createDirectories(neoskriptDir);
        List<String> copied = new ArrayList<>();
        int[] scripts = {0};
        List<SkriptDatabase> databases = databases(skriptDir.resolve("config.sk"));
        for (String name : COPIED) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source) || (name.equals("variables.csv") && !databases.isEmpty())) {
                continue;
            }
            copy(source, neoskriptDir.resolve(name), scripts);
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
        Files.writeString(neoskriptDir.resolve(MARKER),
                "Copied " + copied + " from " + skriptDir.toAbsolutePath() + " at " + Instant.now() + "\n");
        return new Result(copied, scripts[0], playerUuids(skriptDir.resolve("config.sk")), settings, backup);
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
        if (!(csv || sqlite) || !Files.isRegularFile(source) || copied.contains(relative)) {
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
