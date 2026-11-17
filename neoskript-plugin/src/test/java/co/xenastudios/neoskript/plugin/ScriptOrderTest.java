/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Scripts load in Skript's order: each folder's subfolders first, then its own files, each sorted. */
class ScriptOrderTest {

    @TempDir
    Path scripts;

    @Test
    void subfoldersComeBeforeAFoldersOwnFiles() throws IOException {
        for (String name : List.of("a.sk", "c.sk", "b/x.sk", "b/a/y.sk", "b/z.sk", "d/w.sk")) {
            Path file = scripts.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
        assertEquals(List.of("b/a/y.sk", "b/x.sk", "b/z.sk", "d/w.sk", "a.sk", "c.sk"),
                ScriptManager.skriptOrder(scripts).stream()
                        .map(path -> scripts.relativize(path).toString().replace('\\', '/')).toList());
    }
}
