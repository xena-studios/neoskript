/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableTest {

    @TempDir
    Path dir;

    @Test
    void aCopyNeverDeletesAnotherCopysTemporaryFile() throws IOException {
        Path from = Files.writeString(dir.resolve("variables.csv"), "a, string, 0000\n");
        Path to = dir.resolve("variables_2026-11-17_01-02-03_scheduled.csv");
        // Another copy of the same backup is writing its temporary file right now.
        Path temp = Durable.temporary(to);
        byte[] half = "half written".getBytes();
        Files.write(temp, half);

        assertThrows(FileAlreadyExistsException.class, () -> Durable.copy(from, to),
                "a taken temporary name counts as a taken backup name, so the caller numbers the next one");
        assertArrayEquals(half, Files.readAllBytes(temp), "the other copy's file is left alone");
        assertFalse(Files.exists(to), "and the half-written file is never renamed into place");
    }

    @Test
    void aCopyIsWholeAndLeavesNoTemporaryFile() throws IOException {
        Path from = Files.writeString(dir.resolve("variables.csv"), "a, string, 0000\n");
        Path to = dir.resolve("variables_2026-11-17_01-02-03_scheduled.csv");
        Durable.copy(from, to);
        assertEquals(Files.readString(from), Files.readString(to));
        assertFalse(Files.exists(Durable.temporary(to)));
    }
}
