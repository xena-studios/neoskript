/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Saving variables while they change from many threads, as Folia's regions change them, and saves that fail: the file
 * a save writes always loads back whole, and a save that can't be written leaves the old file as it was.
 */
class VariableStorageStressTest {

    @TempDir
    Path dir;

    @Test
    void savingWhileManyThreadsWrite() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.csv");
        VariableStorage storage = new VariableStorage(store, codecs(), file);
        storage.load();
        int threads = 16;
        int writes = 3_000;
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        List<Thread> writers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread writer = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < writes; i++) {
                        store.set("region" + id + "::" + i, (long) i);
                        store.addToList("shared::list", new Object[] {"v" + id + "-" + i});
                        if (i % 7 == 0) {
                            store.set("region" + id + "::" + (i / 2), null);
                        }
                        store.set("counter::" + id, "at " + i);
                    }
                } catch (Throwable e) {
                    failed.compareAndSet(null, e);
                }
            });
            writer.start();
            writers.add(writer);
        }
        start.countDown();
        int saves = 0;
        while (writers.stream().anyMatch(Thread::isAlive)) {
            storage.save();
            saves++;
            // Every file a save leaves loads back with nothing skipped.
            assertEquals(List.of(), VariableFile.load(file).problems());
        }
        for (Thread writer : writers) {
            writer.join();
        }
        if (failed.get() != null) {
            throw new AssertionError(failed.get());
        }
        storage.save();
        assertTrue(saves > 0);

        VariableStore loaded = new VariableStore();
        VariableStorage reloaded = new VariableStorage(loaded, codecs(), file);
        reloaded.load();
        Map<String, Object> expected = snapshot(store);
        Map<String, Object> actual = snapshot(loaded);
        assertEquals(expected.size(), actual.size());
        assertTrue(expected.equals(actual), "the saved variables load back as they were");
    }

    @Test
    void aSaveThatCantBeWrittenKeepsTheOldFile() throws Exception {
        VariableStore store = new VariableStore();
        Path data = Files.createDirectory(dir.resolve("data"));
        Path file = data.resolve("variables.csv");
        VariableStorage storage = new VariableStorage(store, codecs(), file);
        storage.load();
        store.set("kept", "before");
        storage.save();
        byte[] before = Files.readAllBytes(file);

        store.set("kept", "after");
        store.set("more", 1L);
        assertTrue(data.toFile().setWritable(false), "can't make the folder read-only here");
        try {
            assertThrows(IOException.class, storage::save);
        } finally {
            data.toFile().setWritable(true);
        }
        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(storage.changed(), "what wasn't saved is still to save");

        // Once it can be written again, the next save has everything.
        storage.save();
        VariableStore loaded = new VariableStore();
        new VariableStorage(loaded, codecs(), file).load();
        assertEquals("after", loaded.get("kept"));
        assertEquals(1L, loaded.get("more"));
    }

    @Test
    void aValueWithADamagedLengthIsKeptAsSaved() throws Exception {
        // Its length said 1 GB: decoding it ran out of memory, an error the load didn't catch, and NeoSkript didn't start.
        Path file = dir.resolve("variables.csv");
        Files.writeString(file, "# === Skript's variable storage ===\n\ngood, string, 80024142\nbad, string, 41414141"
                + "41".repeat(8) + "\n");
        VariableStore store = new VariableStore();
        VariableStorage.Result result = new VariableStorage(store, codecs(), file).load();
        assertEquals(2, result.variables());
        assertEquals(1, result.raw());
        assertEquals("AB", store.get("good"));
    }

    @Test
    void aBigFileLoads() throws Exception {
        // -Dneoskript.stress.variablesMb=300 for a file of the size big servers have; CI loads a small one.
        int megabytes = Integer.getInteger("neoskript.stress.variablesMb", 8);
        Path file = dir.resolve("variables.csv");
        long count = 0;
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("# === Skript's variable storage ===\n# Please do not modify this file manually!\n#\n"
                    + "# version: 2.17.0\n\n");
            long size = 0;
            while (size < megabytes * 1024L * 1024L) {
                // A player's balance and a long text, as servers keep.
                String line = "balance::" + java.util.UUID.nameUUIDFromBytes(Long.toString(count).getBytes())
                        + ", string, 8028" + "41".repeat(40) + "\n";
                out.write(line);
                size += line.length();
                count++;
            }
        }
        VariableStore store = new VariableStore();
        long started = System.nanoTime();
        VariableStorage.Result result = new VariableStorage(store, codecs(), file).load();
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertEquals(count, result.variables());
        assertEquals(List.of(), result.skipped());
        System.out.println("Loaded " + count + " variables (" + megabytes + " MB) in " + millis + " ms");
    }

    /** Text and whole numbers, as Skript saves them. */
    private static VariableCodecs codecs() {
        VariableCodecs codecs = new VariableCodecs();
        codecs.register("string", String.class, new co.xenastudios.neoskript.api.storage.VariableCodec<>() {
            @Override
            public void encode(String value, co.xenastudios.neoskript.api.storage.BinaryWriter out) {
                out.rawString(value);
            }

            @Override
            public String decode(co.xenastudios.neoskript.api.storage.BinaryReader in) {
                return in.rawString();
            }
        });
        codecs.register("long", Long.class, new co.xenastudios.neoskript.api.storage.VariableCodec<>() {
            @Override
            public void encode(Long value, co.xenastudios.neoskript.api.storage.BinaryWriter out) {
                out.rawLong(value);
            }

            @Override
            public Long decode(co.xenastudios.neoskript.api.storage.BinaryReader in) {
                return in.rawLong();
            }
        });
        return codecs;
    }

    private static Map<String, Object> snapshot(VariableStore store) {
        Map<String, Object> values = new TreeMap<>();
        store.forEach((name, value) -> {
            if (!name.startsWith("-")) {
                values.put(name, value);
            }
        });
        return values;
    }
}
