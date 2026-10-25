/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

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
     * other settings NeoSkript has, by their path in NeoSkript's config.yml ({@link #SETTINGS}).
     */
    record Result(List<String> copied, int scriptCount, Boolean playerUuids, Map<String, Object> settings) {

        Result(List<String> copied, int scriptCount, Boolean playerUuids) {
            this(copied, scriptCount, playerUuids, Map.of());
        }
    }

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
        for (String name : COPIED) {
            Path source = skriptDir.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            copy(source, neoskriptDir.resolve(name), scripts);
            copied.add(name);
        }
        Files.writeString(neoskriptDir.resolve(MARKER),
                "Copied " + copied + " from " + skriptDir.toAbsolutePath() + " at " + Instant.now() + "\n");
        return new Result(copied, scripts[0], playerUuids(skriptDir.resolve("config.sk")),
                settings(skriptDir.resolve("config.sk")));
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
