/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies of the native variable file as each compaction wrote it, in {@code backups/snapshots/} beside it, the
 * last {@value #KEEP} kept. Until the next compaction, the newest is byte for byte the start of the live file, up to
 * the end of its commit, so damage in that range can be repaired from it without losing anything.
 */
final class Snapshots {

    static final int KEEP = 3;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    /** A snapshot that reads whole: its file, its id, and where its saves end. */
    record Snapshot(Path path, byte[] fileId, long end, byte[] bytes) {}

    private Snapshots() {}

    static Path dir(Path file) {
        return file.toAbsolutePath().getParent().resolve("backups").resolve("snapshots");
    }

    private static String prefix(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)) + "_";
    }

    /** The highest generation among {@code file} and its snapshots, or 0 if there's none. */
    static long latestGeneration(Path file) throws IOException {
        long latest = Files.exists(file) ? Math.max(0, NativeVariableFile.generation(file)) : 0;
        for (Path path : list(file)) {
            latest = Math.max(latest, NativeVariableFile.generation(path));
        }
        return latest;
    }

    /** Copies {@code file}, just compacted, to the snapshots, and removes all but the newest {@value #KEEP}. */
    static Path save(Path file) throws IOException {
        Path dir = dir(file);
        Files.createDirectories(dir);
        String time = LocalDateTime.now().format(TIME);
        Path copy = null;
        for (int n = 1; copy == null; n++) {
            Path target = dir.resolve(prefix(file) + time + (n == 1 ? "" : "_" + n) + ".nsv");
            try {
                copy = Durable.copy(file, target);
            } catch (FileAlreadyExistsException taken) {
                // Two in the same millisecond: number the next.
            }
        }
        List<Path> all = list(file);
        for (int i = KEEP; i < all.size(); i++) {
            Durable.delete(all.get(i));
        }
        return copy;
    }

    /** The snapshots of {@code file}, newest first. */
    static List<Path> list(Path file) throws IOException {
        Path dir = dir(file);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        String prefix = prefix(file);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> found = new ArrayList<>(files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".nsv");
            }).toList());
            // Newest first by the generation each file carries, not its time, which a clock step can upset.
            java.util.Map<Path, Long> generations = new java.util.HashMap<>();
            for (Path path : found) {
                generations.put(path, NativeVariableFile.generation(path));
            }
            found.sort(Comparator.comparing((Path p) -> generations.get(p))
                    .thenComparing(p -> p.getFileName().toString()).reversed());
            return found;
        }
    }

    /** The newest snapshot of the file with {@code fileId} that reads whole, or {@code null}. */
    static Snapshot find(Path file, byte[] fileId) throws IOException {
        if (fileId == null) {
            return null;
        }
        for (Path path : list(file)) {
            try {
                byte[] bytes = NativeVariableFile.readFile(path);
                NativeVariableFile.Contents contents = NativeVariableFile.read(bytes);
                if (Arrays.equals(contents.fileId(), fileId) && contents.validEnd() == contents.length()
                        && contents.committed() == contents.length()) {
                    return new Snapshot(path, fileId, contents.committed(), bytes);
                }
            } catch (IOException e) {
                // Not usable: try the next.
            }
        }
        return null;
    }

    /** The newest snapshot of {@code file} that reads whole, of any file id, for the refusal message. */
    static Path newestGood(Path file) throws IOException {
        for (Path path : list(file)) {
            try {
                NativeVariableFile.read(NativeVariableFile.readFile(path));
                return path;
            } catch (IOException e) {
                // Try the next.
            }
        }
        return null;
    }
}
