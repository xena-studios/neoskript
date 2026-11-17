/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import co.xenastudios.neoskript.runtime.variables.storage.NativeVariableFile;
import co.xenastudios.neoskript.runtime.variables.storage.NativeVariableStorage;
import co.xenastudios.neoskript.runtime.variables.storage.RawValue;
import co.xenastudios.neoskript.runtime.variables.storage.VariableBackend;
import co.xenastudios.neoskript.runtime.variables.storage.VariableBackends;
import co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs;
import co.xenastudios.neoskript.runtime.variables.storage.VariableStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NeoSkript's own variable file: round trips, saves of only what changed, crashes, compaction and moving over. */
class NativeVariableStorageTest {

    @TempDir
    Path dir;

    private final VariableCodecs codecs = new Library().codecs();

    private Path skriptFile() throws Exception {
        Path file = dir.resolve("variables.csv");
        try (var in = NativeVariableStorageTest.class.getResourceAsStream("/skript-variables.csv")) {
            Files.copy(in, file);
        }
        return file;
    }

    private NativeVariableStorage open(VariableStore store) throws IOException {
        NativeVariableStorage storage = new NativeVariableStorage(store, codecs, dir.resolve("variables.nsv"));
        storage.load();
        return storage;
    }

    /** Every variable, by name, as Skript's type name and bytes. */
    private Map<String, String> saved(VariableStore store) {
        Map<String, String> values = new LinkedHashMap<>();
        store.forEach((name, value) -> {
            if (!name.startsWith("-")) {
                VariableCodecs.Encoded encoded = codecs.encode(value);
                values.put(name, encoded.type() + " " + java.util.HexFormat.of().formatHex(encoded.bytes()));
            }
        });
        return values;
    }

    private VariableStore reload() throws IOException {
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = open(store)) {
            assertTrue(storage.loaded());
        }
        return store;
    }

    @Test
    void everyValueTypeRoundTrips() throws Exception {
        VariableStore original = new VariableStore();
        new VariableStorage(original, codecs, skriptFile()).load();
        original.set("x::text", "héllo ✓ 日本 " + "long ".repeat(100));
        original.set("x::long", Long.MIN_VALUE);
        original.set("x::double", -0.0);
        original.set("x::nan", Double.NaN);
        original.set("x::bool", false);
        original.set("x::timespan", new Timespan(123_456));
        original.set("x::raw", new RawValue("itemtype", new byte[] {1, 2, 3}));
        original.set("x::empty", "");
        original.set("x::" + "n".repeat(300), 1L);
        try (NativeVariableStorage storage = open(original)) {
            storage.save();
        }
        VariableStore loaded = reload();
        assertEquals(saved(original), saved(loaded));
        assertEquals("héllo ✓ 日本 " + "long ".repeat(100), loaded.get("x::text"));
        assertEquals(Long.MIN_VALUE, loaded.get("x::long"));
        assertEquals(-0.0, loaded.get("x::double"));
        assertEquals(new Timespan(123_456), loaded.get("x::timespan"));
        assertEquals(1234567890123L, loaded.get("t::big"));
        assertInstanceOf(RawValue.class, loaded.get("t::itemtype"), "kept as saved");
        assertEquals(new RawValue("itemtype", new byte[] {1, 2, 3}), loaded.get("x::raw"));
    }

    @Test
    void memoryVariablesAndUnsavableValuesArentSaved() throws Exception {
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = open(store)) {
            store.set("-memory", 1L);
            store.set("kept", 2L);
            store.set("unsavable", new Object());
            VariableStorage.Result result = storage.save();
            assertEquals(1, result.variables());
            assertEquals(1, result.unsaved());
        }
        VariableStore loaded = reload();
        assertNull(loaded.get("-memory"));
        assertNull(loaded.get("unsavable"));
        assertEquals(2L, loaded.get("kept"));
    }

    @Test
    void savesAppendOnlyWhatChanged() throws Exception {
        VariableStore store = new VariableStore();
        for (int i = 0; i < 5_000; i++) {
            store.set("bench::" + i, "value " + i);
        }
        Path file = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = open(store)) {
            storage.save();
            long full = Files.size(file);
            assertFalse(storage.changed());

            store.set("bench::42", "changed");
            store.set("bench::new", 7L);
            store.set("bench::43", null);
            assertTrue(storage.changed());
            storage.save();
            long grown = Files.size(file) - full;
            assertTrue(grown > 0 && grown < 100, "appended " + grown + " bytes");

            // Nothing changed since: nothing is written.
            store.set("bench::44", "value 44");
            storage.save();
            assertEquals(full + grown, Files.size(file));
        }
        VariableStore loaded = reload();
        assertEquals("changed", loaded.get("bench::42"));
        assertEquals(7L, loaded.get("bench::new"));
        assertNull(loaded.get("bench::43"));
        assertEquals("value 44", loaded.get("bench::44"));
        assertEquals(saved(store), saved(loaded));
    }

    @Test
    void deletingAListDeletesItsVariables() throws Exception {
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = open(store)) {
            store.setList("l", new Object[] {"a", "b", "c"});
            store.set("other", 1L);
            storage.save();
            store.deleteList("l");
            storage.save();
        }
        VariableStore loaded = reload();
        assertEquals(0, loaded.list("l").length);
        assertEquals(1L, loaded.get("other"));
    }

    @Test
    void aSaveCutOffAnywhereLeavesThePreviousSave() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        Map<String, String> before;
        long end;
        byte[] whole;
        try (NativeVariableStorage storage = open(store)) {
            for (int i = 0; i < 100; i++) {
                store.set("v::" + i, (long) i);
            }
            storage.save();
            before = saved(store);
            end = Files.size(file);
            store.set("v::1", "one");
            store.set("v::2", null);
            store.set("v::new", true);
            storage.save();
            whole = Files.readAllBytes(file);
        }
        Map<String, String> after = saved(store);
        assertTrue(whole.length > end);
        // A crash at every byte of the second save: each loads exactly the first.
        for (long cut = end; cut < whole.length; cut++) {
            Files.write(file, Arrays.copyOf(whole, (int) cut));
            VariableStore loaded = new VariableStore();
            try (NativeVariableStorage storage = open(loaded)) {
                assertEquals(before, saved(loaded), "cut at " + cut);
                assertEquals(cut - end, storage.recoveredBytes());
                if (cut > end) {
                    assertNotNull(storage.recoveryBackup());
                    assertArrayEquals(Arrays.copyOf(whole, (int) cut), Files.readAllBytes(storage.recoveryBackup()));
                }
            }
            assertEquals(end, Files.size(file), "the broken record is dropped");
        }
        // A save after recovering appends where the good records end.
        Files.write(file, Arrays.copyOf(whole, whole.length - 3));
        VariableStore recovered = new VariableStore();
        try (NativeVariableStorage storage = open(recovered)) {
            recovered.set("v::after", "crash");
            storage.save();
        }
        VariableStore loaded = reload();
        assertEquals("crash", loaded.get("v::after"));
        assertEquals(1L, loaded.get("v::1"), "the cut save isn't there");
        // And the whole file, uncut, has the second save.
        Files.write(file, whole);
        assertEquals(after, saved(reload()));
    }

    @Test
    void aDamagedRecordIsntApplied() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        long end;
        try (NativeVariableStorage storage = open(store)) {
            store.set("a", 1L);
            storage.save();
            end = Files.size(file);
            store.set("a", 2L);
            storage.save();
        }
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 0x01;
        Files.write(file, bytes);
        VariableStore loaded = new VariableStore();
        try (NativeVariableStorage storage = open(loaded)) {
            assertEquals(1L, loaded.get("a"));
            assertNotNull(storage.recoveryBackup());
        }
        assertEquals(end, Files.size(file));
    }

    @Test
    void anotherFileIsNeverWrittenOver() throws Exception {
        Path file = dir.resolve("variables.nsv");
        Files.writeString(file, "a, long, 0000000000000001\n");
        NativeVariableStorage storage = new NativeVariableStorage(new VariableStore(), codecs, file);
        assertThrows(IOException.class, storage::load);
        assertFalse(storage.loaded());
        assertFalse(storage.changed());
        assertThrows(IllegalStateException.class, storage::save);
        assertEquals("a, long, 0000000000000001\n", Files.readString(file));
    }

    @Test
    void compactionKeepsTheFileSmall() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        for (int i = 0; i < 2_000; i++) {
            store.set("list::" + i, "value " + i);
        }
        long smallest;
        try (NativeVariableStorage storage = open(store)) {
            storage.save();
            smallest = Files.size(file);
            // Many saves that each change everything: a log of them all would be about 500 times the size.
            for (int round = 0; round < 500; round++) {
                for (int i = 0; i < 2_000; i++) {
                    store.set("list::" + i, "value " + i + " in round " + round);
                }
                storage.save();
                assertTrue(Files.size(file) < 2 * 3 * smallest + (1 << 20) + 100_000, "round " + round);
            }
            store.set("list::7", null);
            storage.save();
        }
        assertEquals(saved(store), saved(reload()));
        assertNull(reload().get("list::7"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("variables.nsv"), files.map(p -> p.getFileName().toString()).toList(), "no leftovers");
        }
    }

    @Test
    void compactionWritesSkriptsOrder() throws Exception {
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = open(store)) {
            store.set("o::10", "ten");
            store.set("o::2", "two");
            store.set("o::a", "a");
            storage.save();
        }
        NativeVariableFile.Contents contents = NativeVariableFile.read(Files.readAllBytes(dir.resolve("variables.nsv")));
        assertEquals(List.of("o::2", "o::10", "o::a"), List.copyOf(contents.entries().keySet()));
    }

    @Test
    void savingFromManyThreadsLosesNothing() throws Exception {
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = open(store)) {
            Thread[] writers = new Thread[4];
            for (int t = 0; t < writers.length; t++) {
                int thread = t;
                writers[t] = Thread.ofPlatform().start(() -> {
                    for (int i = 0; i < 2_000; i++) {
                        store.set("t" + thread + "::" + i, (long) i);
                        store.update("counter", old -> old == null ? 1L : (Long) old + 1);
                    }
                });
            }
            java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
            Thread saver = Thread.ofPlatform().start(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        storage.save();
                    }
                } catch (Throwable e) {
                    failed.set(e);
                }
            });
            for (Thread writer : writers) {
                writer.join();
            }
            saver.join();
            assertNull(failed.get(), "a save while variables changed failed");
            storage.save();
        }
        VariableStore loaded = reload();
        assertEquals(8_000L, loaded.get("counter"));
        assertEquals(saved(store), saved(loaded));
    }

    @Test
    void switchingToNativeImportsTheCsvAndBackItWritesItAgain() throws Exception {
        Path csv = skriptFile();
        byte[] skript = Files.readAllBytes(csv);
        Path nsv = dir.resolve("variables.nsv");

        VariableStore store = new VariableStore();
        VariableBackends.Opened opened = VariableBackends.open(VariableBackends.Kind.NATIVE, store, codecs, csv);
        assertInstanceOf(NativeVariableStorage.class, opened.backend());
        assertFalse(Files.exists(csv), "only one file holds the variables");
        assertTrue(Files.exists(nsv));
        Path backup = onlyBackup(".csv");
        assertArrayEquals(skript, Files.readAllBytes(backup), "backed up before converting");
        try (VariableBackend backend = opened.backend()) {
            backend.load();
            assertEquals(1234567890123L, store.get("t::big"));
            store.set("from::native", "yes");
            backend.save();
        }

        // Back to csv: variables.csv is written again, and the native file goes to backups.
        VariableStore back = new VariableStore();
        VariableBackends.Opened csvAgain = VariableBackends.open(VariableBackends.Kind.CSV, back, codecs, csv);
        assertInstanceOf(VariableStorage.class, csvAgain.backend());
        assertFalse(Files.exists(nsv));
        assertTrue(Files.exists(onlyBackup(".nsv")));
        csvAgain.backend().load();
        assertEquals("yes", back.get("from::native"));

        // Without the change, CSV to native to CSV gives Skript's file byte for byte.
        back.set("from::native", null);
        csvAgain.backend().save();
        assertArrayEquals(skript, Files.readAllBytes(csv));
        VariableBackends.open(VariableBackends.Kind.NATIVE, new VariableStore(), codecs, csv);
        VariableBackends.open(VariableBackends.Kind.CSV, new VariableStore(), codecs, csv);
        assertArrayEquals(skript, Files.readAllBytes(csv));
    }

    /** A native file with one save of {@code only = "native"}. */
    private Path nativeFile() throws IOException {
        Path nsv = dir.resolve("variables.nsv");
        VariableStore store = new VariableStore();
        try (NativeVariableStorage storage = new NativeVariableStorage(store, codecs, nsv)) {
            storage.load();
            store.set("only", "native");
            storage.save();
        }
        return nsv;
    }

    private static void olderThan(Path file, Path other) throws IOException {
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(
                Files.getLastModifiedTime(other).toInstant().minusSeconds(60)));
    }

    @Test
    void anOlderCsvBesideTheNativeFileIsMovedToBackupsUnread() throws Exception {
        Path csv = skriptFile();
        Path nsv = nativeFile();
        olderThan(csv, nsv);
        VariableStore loaded = new VariableStore();
        VariableBackends.Opened opened = VariableBackends.open(VariableBackends.Kind.NATIVE, loaded, codecs, csv);
        assertTrue(opened.log().getFirst().contains("was last changed"), "says both times: " + opened.log());
        assertFalse(opened.warnings().isEmpty(), "warns before downgrading");
        opened.backend().load();
        assertEquals("native", loaded.get("only"));
        assertNull(loaded.get("t::big"));
        assertFalse(Files.exists(csv));
        assertTrue(Files.exists(onlyBackup(".csv")));
    }

    @Test
    void aNewerDifferentCsvBesideTheNativeFileStopsBothWays() throws Exception {
        // As after downgrading: an older NeoSkript saved variables.csv without reading variables.nsv.
        Path nsv = nativeFile();
        Path csv = skriptFile();
        olderThan(nsv, csv);
        byte[] csvBytes = Files.readAllBytes(csv);
        byte[] nativeBytes = Files.readAllBytes(nsv);
        for (VariableBackends.Kind kind : VariableBackends.Kind.values()) {
            IOException refused = assertThrows(IOException.class,
                    () -> VariableBackends.open(kind, new VariableStore(), codecs, csv));
            assertTrue(refused.getMessage().contains("was last changed"), refused.getMessage());
            assertArrayEquals(csvBytes, Files.readAllBytes(csv), "left alone");
            assertArrayEquals(nativeBytes, Files.readAllBytes(nsv), "left alone");
        }
    }

    @Test
    void anImportCutOffBeforeItRemovedTheCsvIsFinished() throws Exception {
        Path csv = skriptFile();
        byte[] skript = Files.readAllBytes(csv);
        Path nsv = dir.resolve("variables.nsv");
        VariableBackends.open(VariableBackends.Kind.NATIVE, new VariableStore(), codecs, csv);
        // As if the server stopped before removing variables.csv.
        Files.write(csv, skript);
        olderThan(csv, nsv);
        VariableStore loaded = new VariableStore();
        VariableBackends.Opened opened = VariableBackends.open(VariableBackends.Kind.NATIVE, loaded, codecs, csv);
        assertTrue(opened.log().getFirst().startsWith("Finished moving to"), opened.log().toString());
        assertFalse(Files.exists(csv));
        opened.backend().load();
        assertEquals(1234567890123L, loaded.get("t::big"));
    }

    @Test
    void anExportCutOffBeforeItRemovedTheNativeFileIsFinished() throws Exception {
        Path nsv = nativeFile();
        byte[] nativeBytes = Files.readAllBytes(nsv);
        Path csv = dir.resolve("variables.csv");
        VariableBackends.open(VariableBackends.Kind.CSV, new VariableStore(), codecs, csv);
        assertFalse(Files.exists(nsv));
        // As if the server stopped before removing variables.nsv: variables.csv is the newer, with the same variables.
        Files.write(nsv, nativeBytes);
        olderThan(nsv, csv);
        VariableStore loaded = new VariableStore();
        VariableBackends.Opened opened = VariableBackends.open(VariableBackends.Kind.CSV, loaded, codecs, csv);
        assertTrue(opened.log().getFirst().startsWith("Finished moving back"), opened.log().toString());
        assertFalse(Files.exists(nsv));
        opened.backend().load();
        assertEquals("native", loaded.get("only"));
    }

    @Test
    void aLargeSaveCutOffBetweenItsRecordsIsntHalfApplied() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        String kilobyte = "k".repeat(1000);
        Map<String, String> before;
        long end;
        byte[] whole;
        try (NativeVariableStorage storage = open(store)) {
            for (int i = 1; i <= 3_000; i++) {
                store.set("x::" + i, kilobyte + i);
            }
            storage.save();
            before = saved(store);
            end = Files.size(file);
            // delete {x::*}, set {x::1}, and enough more that the save takes several records.
            store.deleteList("x");
            store.set("x::1", "new");
            for (int i = 1; i <= 3_000; i++) {
                store.set("y::" + i, kilobyte + i);
            }
            storage.save();
            whole = Files.readAllBytes(file);
        }
        int first = java.nio.ByteBuffer.wrap(whole, (int) end, 4).getInt() + 8;
        assertTrue(end + first < whole.length - 8, "the save took more than one record");
        for (long cut : new long[] {end + first, end + first + 9, whole.length - 1}) {
            Files.write(file, Arrays.copyOf(whole, (int) cut));
            VariableStore loaded = new VariableStore();
            try (NativeVariableStorage storage = open(loaded)) {
                assertEquals(before, saved(loaded), "cut at " + cut);
                assertEquals(cut - end, storage.recoveredBytes());
            }
        }
        Files.write(file, whole);
        VariableStore loaded = reload();
        assertEquals("new", loaded.get("x::1"));
        assertNull(loaded.get("x::2"));
    }

    @Test
    void damageInTheMiddleIsRefusedAndNeverWrittenOver() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = open(store)) {
            for (int round = 0; round < 5; round++) {
                store.set("v::" + round, (long) round);
                storage.save();
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        // A flipped bit in the first save's entries, with four good saves after it.
        bytes[NativeVariableFile.HEADER_LENGTH + 8 + 3] ^= 0x10;
        Files.write(file, bytes);
        NativeVariableStorage storage = new NativeVariableStorage(new VariableStore(), codecs, file);
        IOException refused = assertThrows(IOException.class, storage::load);
        assertTrue(refused.getMessage().contains("isn't a save cut off by a crash"), refused.getMessage());
        assertFalse(storage.loaded());
        assertThrows(IllegalStateException.class, storage::save);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertFalse(Files.exists(dir.resolve("backups")), "nothing to recover from: it isn't touched");
    }

    @Test
    void aWholeRecordThatCantBeReadIsRefused() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = open(store)) {
            store.set("a", 1L);
            storage.save();
        }
        // A record of a kind a newer NeoSkript might write, with a good checksum, at the end of the file.
        byte[] payload = {7, 0, 0};
        java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(payload);
        java.nio.ByteBuffer record = java.nio.ByteBuffer.allocate(8 + payload.length);
        record.putInt(payload.length).putInt((int) crc.getValue()).put(payload);
        byte[] bytes = Files.readAllBytes(file);
        byte[] newer = Arrays.copyOf(bytes, bytes.length + record.capacity());
        System.arraycopy(record.array(), 0, newer, bytes.length, record.capacity());
        Files.write(file, newer);
        assertThrows(IOException.class, () -> open(new VariableStore()));
        assertArrayEquals(newer, Files.readAllBytes(file), "not cut off as if it were a crash");

        // Flags in the header's reserved bytes are a newer format too.
        bytes[6] = 1;
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> open(new VariableStore()));
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }

    @Test
    void aCompactionCutOffLeavesTheOldFileWhole() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = open(store)) {
            store.set("a", "kept");
            storage.save();
        }
        // What a compaction leaves when the server stops before the temporary file is moved over the old one.
        Path temp = dir.resolve("variables.nsv8429.tmp");
        Files.write(temp, Arrays.copyOf(Files.readAllBytes(file), 11));
        assertEquals("kept", reload().get("a"));
        assertFalse(Files.exists(temp), "the leftover is removed");
    }

    @Test
    void loadingManyAtOnceIsTheSameAsSettingEachInTurn() {
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < 300; i++) {
            names.add("l::" + i);
            names.add("l::" + i + "::sub");
            names.add("m::-" + i);
            names.add("m::" + i * 0.5);
            names.add("t::Name" + (char) ('a' + i % 26) + i);
            names.add("x" + i);
        }
        names.add("l");
        names.add("l::007");
        names.add("l::7");
        names.add("T::NAMEA0");
        for (long seed = 0; seed < 5; seed++) {
            List<String> shuffled = new java.util.ArrayList<>(names);
            java.util.Collections.shuffle(shuffled, new java.util.Random(seed));
            // Sorted once, as a compacted file is, and shuffled the other times.
            if (seed == 0) {
                VariableStore order = new VariableStore();
                shuffled.forEach(name -> order.set(name, 0L));
                shuffled.clear();
                order.forEach((name, value) -> shuffled.add(name));
            }
            List<Object> values = new java.util.ArrayList<>();
            for (int i = 0; i < shuffled.size(); i++) {
                values.add((long) i);
            }
            VariableStore oneByOne = new VariableStore();
            for (int i = 0; i < shuffled.size(); i++) {
                oneByOne.set(shuffled.get(i), values.get(i));
            }
            VariableStore atOnce = new VariableStore();
            atOnce.load(shuffled, values);
            assertEquals(saved(oneByOne), saved(atOnce), "seed " + seed);
            assertEquals(List.of(oneByOne.indices("l")), List.of(atOnce.indices("l")));
            assertEquals(oneByOne.list("l").length, atOnce.list("l").length);
            assertEquals(oneByOne.get("t::namea0"), atOnce.get("T::NameA0"), "found ignoring case");
            atOnce.set("l::150::new", "added after");
            atOnce.addToList("m", new Object[] {"appended"});
            oneByOne.set("l::150::new", "added after");
            oneByOne.addToList("m", new Object[] {"appended"});
            assertEquals(saved(oneByOne), saved(atOnce));
        }
    }

    @Test
    void storageSettingAndFileNames() {
        assertEquals(VariableBackends.Kind.CSV, VariableBackends.Kind.parse(null));
        assertEquals(VariableBackends.Kind.NATIVE, VariableBackends.Kind.parse(" Native "));
        assertNull(VariableBackends.Kind.parse("sqlite"));
        assertEquals(Path.of("a/variables.nsv"), VariableBackends.nativeFile(Path.of("a/variables.csv")));
        assertEquals(Path.of("vars.nsv"), VariableBackends.nativeFile(Path.of("vars")));
    }

    private Path onlyBackup(String extension) throws IOException {
        try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
            List<Path> found = files.filter(p -> p.toString().endsWith(extension)).toList();
            assertEquals(1, found.size(), found.toString());
            return found.getFirst();
        }
    }
}
