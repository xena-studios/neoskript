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

    /** What was copied, relative to the folders, for the log. Empty if nothing was. */
    record Result(List<String> copied, int scriptCount) {}

    private SkriptMigration() {}

    static Result migrate(Path skriptDir, Path neoskriptDir) throws IOException {
        if (!Files.isDirectory(skriptDir) || Files.exists(neoskriptDir.resolve(MARKER))
                || Files.exists(neoskriptDir.resolve("scripts"))) {
            return new Result(List.of(), 0);
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
        return new Result(copied, scripts[0]);
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
