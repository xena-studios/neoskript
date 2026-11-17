/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Skript's {@code compress backups}, on the scheduled backups of {@code backup interval}. */
class CompressedBackupsTest {

    @TempDir
    Path folder;

    private List<String> backups() throws Exception {
        try (Stream<Path> files = Files.list(folder.resolve("backups"))) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aScheduledBackupIsGzippedInPlaceOfTheCopy() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.writeString(file, "# variables\nx, long, 0000000000000001\n");
        Path backup = VariableStorage.backup(file, VariableStorage.SCHEDULED);
        Path gzipped = VariableStorage.compress(backup, () -> false);
        assertEquals(backup.getFileName() + ".gz", gzipped.getFileName().toString());
        assertFalse(Files.exists(backup), "the uncompressed copy is removed");
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gzipped))) {
            assertEquals(Files.readString(file), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of(gzipped.getFileName().toString()), backups(), "no temporary file is left");
    }

    @Test
    void compressedBackupsCountForBackupsToKeep() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.writeString(file, "x\n");
        Path dir = Files.createDirectories(folder.resolve("backups"));
        Files.writeString(dir.resolve("variables_2026-01-01_00-00-00_scheduled.csv.gz"), "a");
        Files.writeString(dir.resolve("variables_2026-01-02_00-00-00_scheduled.csv"), "b");
        Files.writeString(dir.resolve("variables_2026-01-03_00-00-00_scheduled.csv.gz"), "c");
        // Not scheduled backups: never removed.
        Files.writeString(dir.resolve("variables_2025-01-01_00-00-00.csv.gz"), "d");
        assertEquals(2, VariableStorage.purgeBackups(file, 1));
        assertEquals(List.of("variables_2025-01-01_00-00-00.csv.gz", "variables_2026-01-03_00-00-00_scheduled.csv.gz"),
                backups());
    }

    @Test
    void whatAnInterruptedBackupLeftIsRemovedByItsExactName() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.writeString(file, "x\n");
        Path dir = Files.createDirectories(folder.resolve("backups"));
        for (String name : List.of("variables_2026-01-01_00-00-00_scheduled.csv.copying.tmp",
                "variables_2026-01-01_00-00-00_scheduled.csv.gz.copying.tmp",
                // Kept: not what a backup leaves.
                "variables_2026-01-01_00-00-00_scheduled.csv.gz", "variables_2026-01-01_00-00-00_scheduled.csv.gz.tmp",
                "variables_2026-01-01_00-00-00_scheduled.nsv.gz.copying.tmp", "notes.gz.copying.tmp",
                "other_2026-01-01_00-00-00_scheduled.csv.gz.copying.tmp")) {
            Files.writeString(dir.resolve(name), "x");
        }
        assertEquals(2, VariableStorage.removeBackupLeftovers(file));
        assertEquals(List.of("notes.gz.copying.tmp", "other_2026-01-01_00-00-00_scheduled.csv.gz.copying.tmp",
                "variables_2026-01-01_00-00-00_scheduled.csv.gz", "variables_2026-01-01_00-00-00_scheduled.csv.gz.tmp",
                "variables_2026-01-01_00-00-00_scheduled.nsv.gz.copying.tmp"), backups());
    }

    @Test
    void aFailedCompressionKeepsTheCopy() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.writeString(file, "x\n");
        Path backup = VariableStorage.backup(file, VariableStorage.SCHEDULED);
        // A compressed backup by that name is there already: it's never written over.
        Files.writeString(backup.resolveSibling(backup.getFileName() + ".gz"), "older");
        try {
            VariableStorage.compress(backup, () -> false);
        } catch (java.io.IOException expected) {
            // FileAlreadyExistsException
        }
        assertTrue(Files.exists(backup));
        assertFalse(Files.exists(backup.resolveSibling(backup.getFileName() + ".gz.copying.tmp")));
    }

    @Test
    void anotherCompressionsTemporaryFileIsNeverDeleted() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.writeString(file, "x\n");
        Path backup = VariableStorage.backup(file, VariableStorage.SCHEDULED);
        Path theirs = backup.resolveSibling(backup.getFileName() + ".gz.copying.tmp");
        Files.writeString(theirs, "being written");
        try {
            VariableStorage.compress(backup, () -> false);
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            // as a taken name
        }
        assertEquals("being written", Files.readString(theirs));
        assertTrue(Files.exists(backup));
    }

    @Test
    void stoppingTheServerCutsACompressionOff() throws Exception {
        Path file = folder.resolve("variables.csv");
        Files.write(file, new byte[20 << 20]);
        Path backup = VariableStorage.backup(file, VariableStorage.SCHEDULED);
        try {
            VariableStorage.compress(backup, () -> true);
        } catch (java.io.IOException expected) {
            // cut off
        }
        assertTrue(Files.exists(backup), "the copy stays");
        assertEquals(List.of(backup.getFileName().toString()), backups(), "nothing else is left");
    }
}
