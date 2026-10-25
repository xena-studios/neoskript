/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptFilesTest {

    @TempDir
    Path root;
    Path scripts;

    @BeforeEach
    void scripts() throws Exception {
        scripts = root.resolve("scripts");
        Files.createDirectories(scripts.resolve("sub/deeper"));
        Files.writeString(scripts.resolve("a.sk"), "");
        Files.writeString(scripts.resolve("-off.sk"), "");
        Files.writeString(scripts.resolve("sub/b.sk"), "");
        Files.writeString(scripts.resolve("sub/deeper/c.sk"), "");
        Files.writeString(scripts.resolve("notes.txt"), "");
        Files.writeString(root.resolve("outside.sk"), "");
    }

    @Test
    void findsScriptsAsSkriptDoes() {
        assertEquals(scripts.resolve("a.sk"), ScriptFiles.find(scripts, "a"));
        assertEquals(scripts.resolve("a.sk"), ScriptFiles.find(scripts, "a.sk"));
        assertNull(ScriptFiles.find(scripts, "a.txt"));
        // A disabled script by its name without the prefix, or with it.
        assertEquals(scripts.resolve("-off.sk"), ScriptFiles.find(scripts, "off"));
        assertEquals(scripts.resolve("-off.sk"), ScriptFiles.find(scripts, "-off.sk"));
        assertEquals(scripts.resolve("sub/b.sk"), ScriptFiles.find(scripts, "sub/b"));
        assertEquals(scripts.resolve("sub/b.sk"), ScriptFiles.find(scripts, "sub\\b.sk"));
        // Folders end with a separator.
        assertEquals(scripts.resolve("sub"), ScriptFiles.find(scripts, "sub/"));
        assertNull(ScriptFiles.find(scripts, "sub"));
        // Other files, missing ones and ones outside the scripts folder aren't found.
        assertNull(ScriptFiles.find(scripts, "notes.txt"));
        assertNull(ScriptFiles.find(scripts, "missing"));
        assertNull(ScriptFiles.find(scripts, "../outside"));
        assertNull(ScriptFiles.find(scripts, "/"));
    }

    @Test
    void togglesWithThePrefix() throws Exception {
        Path off = ScriptFiles.toggle(scripts.resolve("a.sk"), false);
        assertEquals(scripts.resolve("-a.sk"), off);
        assertTrue(ScriptFiles.disabled(off));
        assertEquals(scripts.resolve("a.sk"), ScriptFiles.toggle(off, true));

        Set<Path> disabled = ScriptFiles.toggleAll(scripts.resolve("sub"), false);
        assertEquals(Set.of(scripts.resolve("sub/-b.sk"), scripts.resolve("sub/deeper/-c.sk")), disabled);
        // Folders keep their names.
        assertTrue(Files.isDirectory(scripts.resolve("sub/deeper")));
        assertEquals(List.of("-off.sk", "sub" + File.separator + "-b.sk", "sub" + File.separator + "deeper" + File.separator + "-c.sk"),
                ScriptFiles.disabledScripts(scripts));
        assertEquals(Set.of(scripts.resolve("-off.sk").resolveSibling("off.sk"), scripts.resolve("sub/b.sk"),
                scripts.resolve("sub/deeper/c.sk")), ScriptFiles.toggleAll(scripts, true));
        assertFalse(Files.exists(scripts.resolve("-off.sk")));
    }

    @Test
    void completesAsSkriptDoes() {
        String fs = File.separator;
        assertEquals(List.of("a.sk", fs + "sub" + fs, fs + "sub" + fs + "b.sk", fs + "sub" + fs + "deeper" + fs,
                fs + "sub" + fs + "deeper" + fs + "c.sk", "all", "config", "aliases", "scripts"),
                ScriptFiles.completions(scripts, new String[] {"reload", ""}));
        // Disabled scripts for enable, with and without the prefix.
        assertEquals(List.of("-off.sk", "off.sk", "all"), ScriptFiles.completions(scripts, new String[] {"enable", ""}));
        assertEquals(List.of("a.sk", "all"), ScriptFiles.completions(scripts, new String[] {"disable", "a"}));
        assertEquals(List.of(fs + "sub" + fs + "b.sk", "all"), ScriptFiles.completions(scripts, new String[] {"disable", "b"}));
    }
}
