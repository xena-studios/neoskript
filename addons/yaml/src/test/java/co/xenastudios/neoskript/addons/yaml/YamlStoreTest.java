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
            store.save(data, null, true);
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
        store.save(keep, null, true);
        store.await(path);
        // A folder where the file's temporary file would go: the next save can't be written.
        Path blocked = file("plugins/Test/blocked/keep.yml");
        Files.createDirectories(blocked.getParent());
        Files.writeString(blocked.getParent().resolve("nope"), "");
        Path notAFolder = blocked.getParent().resolve("nope").resolve("keep.yml");
        keep.set("a", 2L);
        store.save(keep, notAFolder, true);
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
                    store.save(shared, null, i % 2 == 0);
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
        store.save(late, null, false);
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
}
