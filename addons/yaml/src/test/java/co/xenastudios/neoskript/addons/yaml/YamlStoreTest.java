/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlStoreTest {

    @TempDir
    Path server;

    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private YamlStore store;

    @BeforeEach
    void open() {
        store = new YamlStore(new ServerPaths(server), new FakeValues(), warnings::add);
    }

    @AfterEach
    void close() {
        store.close();
    }

    private Path file(String path) {
        return server.resolve(path);
    }

    @Test
    void aMissingFileIsMadeWithItsFolders() {
        assertTrue(store.load("config", file("plugins/Test/config.yml"), ""));
        assertTrue(Files.isRegularFile(file("plugins/Test/config.yml")));
        assertTrue(store.get("config").isEmpty());
    }

    @Test
    void savesAreWrittenWholeAndInOrder() throws Exception {
        Path path = file("plugins/Test/data.yml");
        store.load("data", path, "");
        YamlFile data = store.get("data");
        for (int i = 0; i < 200; i++) {
            data.set("count", (long) i);
            store.save(data, null, true, "test", "");
        }
        store.await(path);
        assertEquals("count: 199" + YamlText.LINE_BREAK, Files.readString(path));
        assertFalse(data.modified());
        try (Stream<Path> files = Files.list(path.getParent())) {
            assertEquals(List.of(path), files.toList(), "no temporary files are left behind");
        }
    }

    @Test
    void aFileThatCantBeReadIsLeftAloneAndWhatWasLoadedStays() throws IOException {
        Path path = file("broken.yml");
        Files.writeString(path, "a: 1\n");
        assertTrue(store.load("broken", path, ""));
        Files.writeString(path, "a: [1, 2\n");
        assertFalse(store.load("broken", path, ""));
        assertEquals(1, store.get("broken").get("a"));
        assertEquals("a: [1, 2\n", Files.readString(path));
        assertFalse(store.load("other", path, ""));
        assertNull(store.get("other"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("isn't valid YAML")), warnings.toString());
    }

    @Test
    void aSaveThatFailsLeavesTheFileAsItWasAndSaysSo() throws Exception {
        Path path = file("plugins/Test/keep.yml");
        store.load("keep", path, "");
        YamlFile keep = store.get("keep");
        keep.set("a", 1L);
        store.save(keep, null, true, "test", "");
        store.await(path);
        // A folder where the file's temporary file would go: the next save can't be written.
        Path blocked = file("plugins/Test/blocked/keep.yml");
        Files.createDirectories(blocked.getParent());
        Files.writeString(blocked.getParent().resolve("nope"), "");
        Path notAFolder = blocked.getParent().resolve("nope").resolve("keep.yml");
        keep.set("a", 2L);
        store.save(keep, notAFolder, true, "test", "");
        store.await(notAFolder);
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Couldn't save")), warnings.toString());
        assertEquals("a: 1" + YamlText.LINE_BREAK, Files.readString(path));
    }

    @Test
    void manyThreadsChangeAndSaveOneFile() throws Exception {
        Path path = file("shared.yml");
        store.load("shared", path, "");
        YamlFile shared = store.get("shared");
        ExecutorService threads = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int thread = t;
            done.add(threads.submit(() -> {
                start.await();
                for (int i = 0; i < 300; i++) {
                    shared.set("t" + thread + ".n" + (i % 20), (long) i);
                    shared.set("list", List.of(i, thread));
                    shared.remove("t" + thread + ".n" + ((i + 7) % 20));
                    shared.paths();
                    store.save(shared, null, i % 2 == 0, "test", "");
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : done) {
            future.get();
        }
        threads.shutdown();
        store.await(path);
        // The file on disk is one whole save: it reads back.
        assertTrue(store.load("check", path, ""));
        assertNotNull(store.get("check").get("list"));
    }

    @Test
    void relativePathsStayInsideTheServer() throws Exception {
        ServerPaths paths = store.paths();
        assertEquals(file("plugins/x/a.yml"), paths.resolve("plugins/x/a.yml", false));
        assertEquals(file("a.yml"), paths.resolve("/a.yml", false));
        assertEquals(file("plugins/a.yml"), paths.resolve("plugins/x/../a.yml", false));
        assertThrows(ServerPaths.Refused.class, () -> paths.resolve("../outside.yml", false));
        assertThrows(ServerPaths.Refused.class, () -> paths.resolve("plugins/../../outside.yml", false));
        assertThrows(ServerPaths.Refused.class, () -> paths.resolve("a\0b.yml", false));
        Path elsewhere = server.getParent().resolve("elsewhere.yml");
        assertEquals(elsewhere, paths.resolve(elsewhere.toString(), true));
    }

    @Test
    void idsAreFileNamesWithoutTheirExtension() {
        assertEquals("config", ServerPaths.id(Path.of("plugins/x/config.yml")));
        assertEquals(".hidden", ServerPaths.id(Path.of(".hidden")));
        assertEquals("a.b", ServerPaths.id(Path.of("a.b.yaml")));
        assertEquals("plugins/x/", ServerPaths.withSeparator("plugins/x"));
        assertEquals("plugins/x/", ServerPaths.withSeparator("plugins/x/"));
    }

    @Test
    void afterClosingSavesAreWrittenAtOnce() throws IOException {
        Path path = file("late.yml");
        store.load("late", path, "");
        store.close();
        YamlFile late = store.get("late");
        late.set("a", "b");
        store.save(late, null, false, "test", "");
        assertEquals("a: b" + YamlText.LINE_BREAK, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void pathsListEachNodeOnce() throws Exception {
        Files.writeString(file("nested.yml"), "a:\n  b:\n    c: 1\nd:\n");
        store.load("nested", file("nested.yml"), "");
        YamlFile nested = store.get("nested");
        // skript-yaml lists a.b twice, and leaves out d.
        assertEquals(List.of("a", "a.b", "a.b.c", "d"), nested.paths());
        nested.set("x.a.y", 1L);
        nested.remove("a");
        assertEquals(List.of("d", "x", "x.a", "x.a.y"), nested.paths());
    }

    @Test
    void anOlderSnapshotFinishingLastIsntWritten() throws Exception {
        // Region A copies the file first, but turns it into text slowly; region B changes it, copies it and is written
        // first. A's older copy, handed over last, must not be written over B's.
        Path path = file("raced.yml");
        store.load("raced", path, "");
        YamlFile raced = store.get("raced");
        raced.set("a", 1L);
        CountDownLatch inDump = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        store.afterDump = text -> {
            if (first.getAndSet(false)) {
                inDump.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            return text;
        };
        Thread regionA = new Thread(() -> store.save(raced, null, false, "raced", ""));
        regionA.start();
        assertTrue(inDump.await(10, java.util.concurrent.TimeUnit.SECONDS));
        raced.set("b", 2L);
        store.save(raced, null, false, "raced", "");
        store.await(path);
        release.countDown();
        regionA.join();
        store.await(path);
        assertEquals("a: 1" + YamlText.LINE_BREAK + "b: 2" + YamlText.LINE_BREAK, Files.readString(path));
    }

    @Test
    void closingWritesWhatsPendingAndLaterSavesDontWait() throws Exception {
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Path path = file("closing/f" + i + ".yml");
            paths.add(path);
            store.load("f" + i, path, "");
            YamlFile f = store.get("f" + i);
            f.set("n", (long) i);
            store.save(f, null, false, "f" + i, "");
        }
        store.close();
        for (int i = 0; i < 20; i++) {
            assertEquals("n: " + i + YamlText.LINE_BREAK, Files.readString(paths.get(i)));
        }
        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            YamlFile f = store.get("f" + i);
            f.set("n", (long) -i);
            store.save(f, null, false, "f" + i, "");
            assertEquals("n: " + -i + YamlText.LINE_BREAK, Files.readString(paths.get(i)));
        }
        assertTrue(System.nanoTime() - start < 5_000_000_000L, "saves after closing waited");
    }

    @Test
    void aFileThatCantBeReadAgainIsntSavedOver() throws Exception {
        Path path = file("edited.yml");
        Files.writeString(path, "a: 1\n");
        assertTrue(store.load("edited", path, ""));
        // Someone edits the file by hand, and breaks it.
        Files.writeString(path, "a: [1, 2\nb: 2\n");
        assertFalse(store.load("edited", path, ""));
        YamlFile edited = store.get("edited");
        edited.set("c", 3L);
        assertFalse(store.save(edited, null, true, "edited", ""));
        store.await(path);
        assertEquals("a: [1, 2\nb: 2\n", Files.readString(path));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Not saving 'edited'")), warnings.toString());
        // Another file is fine, and once the file is fixed and loaded, saving it is too.
        Path elsewhere = file("elsewhere.yml");
        assertTrue(store.save(edited, elsewhere, true, "edited", ""));
        store.await(elsewhere);
        assertTrue(Files.readString(elsewhere).contains("c: 3"));
        Files.writeString(path, "a: 1\n");
        assertTrue(store.load("edited", path, ""));
        assertTrue(store.save(store.get("edited"), null, true, "edited", ""));
    }

    @Test
    void aLinkedFileStaysLinked() throws Exception {
        Path shared = Files.createDirectories(server.resolve("shared")).resolve("config.yml");
        Files.writeString(shared, "a: 1\n");
        Path link = file("plugins/Test/config.yml");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, shared);
        } catch (UnsupportedOperationException | IOException noLinks) {
            return;
        }
        store.load("linked", link, "");
        YamlFile linked = store.get("linked");
        linked.set("a", 2L);
        store.save(linked, null, false, "linked", "");
        store.await(link);
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("a: 2" + YamlText.LINE_BREAK, Files.readString(shared));
    }

    @Test
    void hugeFilesAreRefusedBeforeTheyFillMemory() throws Exception {
        // The tests' heap is 512 MB (build.gradle.kts). A 57 MB list of zeros is refused by its size, unread; a 30 MB
        // one, 15 million values, is refused as it's read, at a million.
        for (int megabytes : new int[] {57, 30}) {
            Path path = file("zeros" + megabytes + ".yml");
            try (var out = new java.io.BufferedWriter(Files.newBufferedWriter(path), 1 << 20)) {
                out.write("a: [");
                for (long i = 0; i < megabytes * 1024L * 1024L / 2; i++) {
                    out.write("0,");
                }
                out.write("0]\n");
            }
            assertFalse(store.load("zeros", path, ""));
            assertNull(store.get("zeros"));
        }
        assertTrue(warnings.stream().anyMatch(w -> w.contains("larger than 32 MB")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("more than 1000000 values")), warnings.toString());
    }

    /** Writes a file of {@code megabytes} with {@code start}, then {@code fill} until it's that big, then {@code end}. */
    private Path big(String name, int megabytes, String start, String fill, String end) throws IOException {
        Path path = file(name);
        try (var out = new java.io.BufferedWriter(Files.newBufferedWriter(path), 1 << 20)) {
            out.write(start);
            for (long written = 0; written < megabytes * 1024L * 1024L; written += fill.length()) {
                out.write(fill);
            }
            out.write(end);
        }
        return path;
    }

    @Test
    void aLongValueLoadsInTimeThatGrowsWithItsLength() throws Exception {
        // SnakeYAML's own reader took 15 seconds for 8 MB and four minutes for 29 MB, on the script's thread.
        for (String[] kind : new String[][] {{"plain", "a: ", "x", "\n"}, {"quoted", "a: \"", "x", "\"\n"}}) {
            for (int megabytes : new int[] {8, 29}) {
                Path path = big(kind[0] + megabytes + ".yml", megabytes, kind[1], kind[2], kind[3]);
                long start = System.nanoTime();
                assertTrue(store.load("long", path, ""), warnings.toString());
                long millis = (System.nanoTime() - start) / 1_000_000;
                System.out.println("Loaded a " + megabytes + " MB " + kind[0] + " value in " + millis + " ms");
                assertTrue(millis < 10_000, kind[0] + " " + megabytes + " MB took " + millis + " ms");
                assertEquals(megabytes * 1024L * 1024L, ((String) store.get("long").get("a")).length());
                store.unload("long");
            }
        }
    }

    @Test
    void manyCommentedKeysAreRefusedBeforeTheyFillMemory() throws Exception {
        // 30 MB of '# c' over a key, under the size limit: about two million comments, each kept apart from the text.
        Path path = file("comments.yml");
        try (var out = new java.io.BufferedWriter(Files.newBufferedWriter(path), 1 << 20)) {
            long written = 0;
            for (int i = 0; written < 30L * 1024 * 1024; i++) {
                String pair = "# c\nk" + i + ": 1\n";
                out.write(pair);
                written += pair.length();
            }
        }
        assertTrue(Files.size(path) <= YamlText.MAX_CHARACTERS);
        assertFalse(store.load("comments", path, ""));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("more than 1000000 values")), warnings.toString());
    }

    @Test
    void aSaveTooBigToLoadAgainIsRefused() throws Exception {
        Path path = file("growing.yml");
        store.load("growing", path, "");
        YamlFile growing = store.get("growing");
        growing.set("a", 1L);
        assertTrue(store.save(growing, null, false, "growing", ""));
        store.await(path);
        List<Object> items = new ArrayList<>(1_000_000);
        for (int i = 0; i < 1_000_000; i++) {
            items.add((long) i);
        }
        growing.set("list", items);
        assertFalse(store.save(growing, null, false, "growing", ""));
        assertTrue(growing.modified());
        store.await(path);
        assertEquals("a: 1" + YamlText.LINE_BREAK, Files.readString(path));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Not saving 'growing'") && w.contains("values")),
                warnings.toString());
        growing.set("list", "x".repeat(YamlText.MAX_CHARACTERS));
        assertFalse(store.save(growing, null, false, "growing", ""));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Not saving 'growing'") && w.contains("MB")),
                warnings.toString());
    }

    @Test
    void savingCountsValuesAsLoadingDoes() throws Exception {
        String text = "## Header" + YamlText.LINE_BREAK + "## two" + YamlText.LINE_BREAK + YamlText.LINE_BREAK
                + "# about a" + YamlText.LINE_BREAK + "a:" + YamlText.LINE_BREAK + "    b: 1" + YamlText.LINE_BREAK
                + "    c:" + YamlText.LINE_BREAK + "    - x" + YamlText.LINE_BREAK + "    - k: v" + YamlText.LINE_BREAK
                + "odd: !unknown" + YamlText.LINE_BREAK + "    m: [1, 2]" + YamlText.LINE_BREAK + "e: ''" + YamlText.LINE_BREAK;
        YamlText.Read read = YamlText.read(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), new FakeValues());
        YamlFile file = new YamlFile(file("counted.yml"));
        file.replace(read.root(), read.header(), read.comments());
        YamlText.Written written = YamlText.write(file.snapshot(), true, new FakeValues());
        assertEquals(read.values(), written.values());
        YamlText.Read again = YamlText.read(written.text().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new FakeValues());
        assertEquals(written.values(), again.values());
    }

    @Test
    void oneFileHasOneQueueHoweverItsNamed() throws Exception {
        Path real = Files.createDirectories(server.resolve("real"));
        Path link = server.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException noLinks) {
            return;
        }
        // Neither the file nor its folder exists yet.
        assertEquals(Saver.key(real.resolve("sub/new.yml")), Saver.key(link.resolve("sub/new.yml")));
        assertEquals(Saver.key(real.resolve("new.yml")), Saver.key(link.resolve("./x/../new.yml")));
    }
}
