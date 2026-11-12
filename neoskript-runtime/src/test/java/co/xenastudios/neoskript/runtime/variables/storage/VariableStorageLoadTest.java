/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A {@code variables.csv} that's damaged loads what it can and is never written over with less. */
class VariableStorageLoadTest {

    private static final String GOOD = """
            # === Skript's variable storage ===
            # Please do not modify this file manually!
            #
            # version: 2.17.0

            a, string, 0161
            b, string, 0162
            """;

    @TempDir
    Path dir;

    private final VariableStore store = new VariableStore();

    private Path file(String text) throws Exception {
        Path file = dir.resolve("variables.csv");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private VariableStorage storage(Path file) {
        return new VariableStorage(store, new VariableCodecs(), file);
    }

    private static byte[] bytes(Object value) {
        return ((RawValue) value).bytes();
    }

    /** Loads the file and checks it's as it was, backed up, and that a stop with nothing changed doesn't save. */
    private VariableStorage.Result loadDamaged(Path file, int skipped) throws Exception {
        byte[] original = Files.readAllBytes(file);
        VariableStorage storage = storage(file);
        VariableStorage.Result result = storage.load();
        assertEquals(skipped, result.skipped().size(), result.skipped().toString());
        assertTrue(storage.loaded());
        assertFalse(storage.changed(), "nothing to save when the server stops");
        assertArrayEquals(original, Files.readAllBytes(file), "the file is as it was");
        assertNotNull(result.backup());
        assertEquals(dir.resolve("backups"), result.backup().getParent());
        assertArrayEquals(original, Files.readAllBytes(result.backup()), "the backup is the original");
        assertArrayEquals(new byte[] {1, 'a'}, bytes(store.get("a")));
        assertArrayEquals(new byte[] {1, 'b'}, bytes(store.get("b")));
        return result;
    }

    @Test
    void aGoodFileIsntBackedUp() throws Exception {
        VariableStorage.Result result = storage(file(GOOD)).load();
        assertEquals(2, result.variables());
        assertEquals(List.of(), result.skipped());
        assertNull(result.backup());
        assertFalse(Files.exists(dir.resolve("backups")));
    }

    @Test
    void aTruncatedLastLineIsSkipped() throws Exception {
        VariableStorage.Result result = loadDamaged(file(GOOD + "c, str"), 1);
        assertEquals("invalid amount of commas in line 8 ('c, str')", result.skipped().getFirst().message());
        assertEquals("c", result.skipped().getFirst().name());
        assertNull(store.get("c"));
    }

    @Test
    void aLineCutOffInsideACharacterIsSkipped() throws Exception {
        Path file = dir.resolve("variables.csv");
        byte[] good = GOOD.getBytes(StandardCharsets.UTF_8);
        byte[] cut = "été, str".getBytes(StandardCharsets.UTF_8);
        byte[] text = java.util.Arrays.copyOf(good, good.length + cut.length - 1);
        System.arraycopy(cut, 0, text, good.length, cut.length - 1);
        text[text.length - 1] = (byte) 0xC3;
        Files.write(file, text);
        loadDamaged(file, 1);
    }

    @Test
    void anOddLengthValueIsSkipped() throws Exception {
        VariableStorage.Result result = loadDamaged(file(GOOD + "c, string, 016\n"), 1);
        assertTrue(result.skipped().getFirst().message().startsWith("invalid value in line 8 ('c, string, 016')"));
        assertNull(store.get("c"));
    }

    @Test
    void aValueThatIsntHexIsSkipped() throws Exception {
        loadDamaged(file(GOOD + "c, string, 01zz\n"), 1);
        assertNull(store.get("c"));
    }

    @Test
    void aLineWithTheWrongFieldCountIsSkippedAndTheRestLoad() throws Exception {
        loadDamaged(file(GOOD.replace("b, string", "x, y, z, w\nb, string") + "c, string, 0163\n"), 1);
        assertArrayEquals(new byte[] {1, 'c'}, bytes(store.get("c")));
        assertNull(store.get("x"));
    }

    @Test
    void aBadLaterLineForTheSameVariableKeepsItsEarlierValue() throws Exception {
        loadDamaged(file(GOOD + "a, string, 016"), 1);
    }

    @Test
    void savingAfterSkippingKeepsTheRestAndTheBackupKeepsTheLine() throws Exception {
        Path file = file(GOOD + "c, string, 016");
        VariableStorage storage = storage(file);
        VariableStorage.Result result = storage.load();
        storage.save();
        assertEquals(2, VariableFile.read(file).size());
        assertTrue(Files.readString(result.backup()).endsWith("c, string, 016"));
    }

    @Test
    void aFileThatCantBeReadIsNeverSavedOver() throws Exception {
        Path file = file(GOOD);
        byte[] original = Files.readAllBytes(file);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException notPosix) {
            assumeTrue(false, "needs POSIX permissions");
        }
        try {
            assumeTrue(!Files.isReadable(file), "the file is still readable (running as root?)");
            VariableStorage storage = storage(file);
            assertThrows(java.io.IOException.class, storage::load);
            assertFalse(storage.loaded());
            store.set("new", "value");
            assertFalse(storage.changed(), "nothing to save when the server stops");
            assertThrows(IllegalStateException.class, storage::save);
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void aStorageThatHasntLoadedNeverSaves() throws Exception {
        Path file = file(GOOD);
        VariableStorage storage = storage(file);
        assertFalse(storage.changed());
        assertThrows(IllegalStateException.class, storage::save);
        assertEquals(GOOD, Files.readString(file));
    }

    @Test
    void temporaryFilesFromAnInterruptedSaveAreRemoved() throws Exception {
        Path file = file(GOOD);
        Path temp = dir.resolve("variables.csv1234567890.tmp");
        Files.writeString(temp, "# half a save\na, str");
        Path other = dir.resolve("other.tmp");
        Files.writeString(other, "not ours");
        assertEquals(1, storage(file).load().leftovers());
        assertFalse(Files.exists(temp));
        assertTrue(Files.exists(other));
    }

    @Test
    void temporaryFilesAreKeptWhenTheFileIsMissing() throws Exception {
        Path temp = dir.resolve("variables.csv1234567890.tmp");
        Files.writeString(temp, GOOD);
        VariableStorage storage = storage(dir.resolve("variables.csv"));
        assertEquals(0, storage.load().leftovers());
        assertTrue(Files.exists(temp));
        assertTrue(storage.loaded());
    }
}
