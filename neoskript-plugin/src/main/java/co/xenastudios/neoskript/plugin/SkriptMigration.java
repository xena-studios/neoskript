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
     * alias files that weren't copied.
     */
    record Result(List<String> copied, int scriptCount, Boolean playerUuids, Map<String, Object> settings,
            Path variablesBackup, List<String> notes) {

        Result(List<String> copied, int scriptCount, Boolean playerUuids) {
            this(copied, scriptCount, playerUuids, Map.of(), null, List.of());
        }
    }

    /** The marker's line saying Skript's alias files were dealt with, so they're never copied again. */
    static final String ALIASES_DONE = "aliases: copied";

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
        if (!Files.isDirectory(skriptDir) || Files.exists(neoskriptDir.resolve("scripts"))) {
            return new Result(List.of(), 0, null);
        }
        Files.createDirectories(neoskriptDir);
        List<String> copied = new ArrayList<>();
        int[] scripts = {0};
        for (String name : List.of("scripts", "variables.csv")) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            copy(source, neoskriptDir.resolve(name), name.equals("scripts") ? scripts : new int[1]);
            copied.add(name);
        }
        // Before NeoSkript first loads or saves them, the variables Skript saved are backed up as they were.
        Path variables = neoskriptDir.resolve("variables.csv");
        Path backup = copied.contains("variables.csv") ? VariableStorage.backup(variables) : null;
        // The alias files last, and never at the cost of the rest: a file already there is kept, and one that can't
        // be copied is reported.
        List<String> notes = new ArrayList<>();
        copied.addAll(copyAliases(skriptDir, neoskriptDir, notes));
        Files.writeString(neoskriptDir.resolve(MARKER), "Copied " + copied + " from " + skriptDir.toAbsolutePath()
                + " at " + Instant.now() + "\n" + ALIASES_DONE + "\n");
        return new Result(copied, scripts[0], playerUuids(skriptDir.resolve("config.sk")),
                settings(skriptDir.resolve("config.sk")), backup, List.copyOf(notes));
    }

    /**
     * Copies Skript's alias files ({@link #ALIASES}) that are there, file by file, keeping any NeoSkript already has.
     * Never throws: what couldn't be copied goes in {@code notes}. Returns what was copied, at least in part.
     */
    private static List<String> copyAliases(Path skriptDir, Path neoskriptDir, List<String> notes) {
        List<String> copied = new ArrayList<>();
        for (String name : ALIASES) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            Path target = neoskriptDir.resolve(name);
            try (java.util.stream.Stream<Path> files = Files.isDirectory(source) ? Files.walk(source) : java.util.stream.Stream.of(source)) {
                boolean any = false;
                for (Path file : files.sorted().toList()) {
                    Path to = Files.isDirectory(source) ? target.resolve(source.relativize(file).toString()) : target;
                    String shown = neoskriptDir.relativize(to).toString().replace('\\', '/');
                    try {
                        if (Files.isDirectory(file)) {
                            Files.createDirectories(to);
                            any = true;
                        } else if (Files.exists(to)) {
                            notes.add("Kept plugins/NeoSkript/" + shown + ", which was there already, rather than Skript's");
                        } else {
                            Files.copy(file, to, StandardCopyOption.COPY_ATTRIBUTES);
                            any = true;
                        }
                    } catch (IOException | RuntimeException e) {
                        notes.add("Couldn't copy Skript's " + shown + ": " + e);
                    }
                }
                if (any) {
                    copied.add(name);
                }
            } catch (IOException | RuntimeException e) {
                notes.add("Couldn't copy Skript's " + name + ": " + e);
            }
        }
        return copied;
    }

    /**
     * For a server that moved over before NeoSkript read alias files: Skript's alias files and its
     * {@code load default aliases}, once, unless NeoSkript has an aliases folder of its own by now. The marker records
     * it, so deleting them later doesn't bring them back.
     */
    private static Result aliasesLater(Path skriptDir, Path neoskriptDir) throws IOException {
        Path marker = neoskriptDir.resolve(MARKER);
        String recorded = Files.readString(marker);
        if (recorded.lines().anyMatch(ALIASES_DONE::equals)) {
            return new Result(List.of(), 0, null);
        }
        if (Files.exists(neoskriptDir.resolve("aliases")) || Files.exists(neoskriptDir.resolve("aliases-english.zip"))) {
            // NeoSkript has alias files of its own by now: they're the owner's, and Skript's are never copied.
            Files.writeString(marker, recorded + ALIASES_DONE + "\n");
            return new Result(List.of(), 0, null);
        }
        List<String> notes = new ArrayList<>();
        List<String> copied = copyAliases(skriptDir, neoskriptDir, notes);
        Files.writeString(marker, recorded + (copied.isEmpty() ? "" : "Copied " + copied + " from "
                + skriptDir.toAbsolutePath() + " at " + Instant.now() + "\n") + ALIASES_DONE + "\n");
        if (copied.isEmpty()) {
            return new Result(List.of(), 0, null, Map.of(), null, List.copyOf(notes));
        }
        Map<String, Object> settings = new java.util.LinkedHashMap<>(settings(skriptDir.resolve("config.sk")));
        settings.keySet().retainAll(List.of("aliases.load-defaults"));
        return new Result(copied, 0, null, settings, null, List.copyOf(notes));
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
