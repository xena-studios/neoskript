/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadsTest {

    @Test
    void hashesFiles(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("hello.txt"), "hello");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Downloads.sha256(file));
    }

    @Test
    void usesACachedFileWithoutDownloading(@TempDir Path dir) throws Exception {
        Path cached = Files.writeString(dir.resolve("server.jar"), "hello");
        Path fetched = new Downloads(dir).fetch(
                "http://invalid.invalid/server.jar",
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                "server.jar");
        assertEquals(cached, fetched);
    }
}
