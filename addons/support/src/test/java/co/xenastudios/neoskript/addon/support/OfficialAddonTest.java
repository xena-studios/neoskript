/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addon.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialAddonTest {

    @TempDir
    Path plugins;

    private Path jar(String file, String entry, String yaml) throws IOException {
        Path jar = plugins.resolve(file);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(yaml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    @Test
    void findsTheOriginalByItsPluginNameWhateverTheFileIsCalled() throws IOException {
        Path db = jar("SkriptDB-1.4.0-all.jar", "plugin.yml", "main: com.btk5h.skriptdb.SkriptDB\nname: skript-db\n"
                + "version: 1.4.0\ndepend: [Skript]\n");
        jar("paper-plugin.jar", "paper-plugin.yml", "name: 'skript-yaml' # quoted, with a comment\nversion: 1\n");
        jar("other.jar", "plugin.yml", "name: SomethingElse\n");
        Files.writeString(plugins.resolve("broken.jar"), "not a zip");
        Files.writeString(plugins.resolve("readme.txt"), "name: skript-db");
        Map<String, Path> found = Originals.find(plugins, List.of("Skript-DB", "skript-yaml", "skript-gui"));
        assertEquals(Map.of("Skript-DB", db, "skript-yaml", plugins.resolve("paper-plugin.jar")), found);
    }

    @Test
    void findsAnOriginalUnderAnyOfItsNames() throws IOException {
        Path mirror = jar("skript-mirror-2.0.jar", "plugin.yml", "name: skript-mirror\n");
        assertEquals(Map.of("skript-mirror", mirror), Originals.find(plugins, List.of("skript-reflect", "skript-mirror")));
    }

    @Test
    void aMissingFolderOrNothingToLookForFindsNothing() {
        assertEquals(Map.of(), Originals.find(plugins.resolve("missing"), List.of("skript-db")));
        assertEquals(Map.of(), Originals.find(plugins, List.of()));
        assertNull(Originals.pluginName(plugins.resolve("missing.jar")));
    }

    @Test
    void itDoesntLoadNextToARunningOriginal() {
        OfficialAddon.Check check = OfficialAddon.check("NeoSkriptDb", Map.of("skript-db", "1.4.0"),
                Map.of("skript-db", plugins.resolve("skript-db.jar")), "1.0.0", "1.0.0");
        assertFalse(check.load());
        assertEquals(1, check.warnings().size());
        assertTrue(check.warnings().getFirst().startsWith("skript-db 1.4.0 is installed and loaded, and NeoSkriptDb replaces it"),
                check.warnings().toString());
    }

    @Test
    void itLoadsAndSaysToRemoveAnOriginalThatCantRun() {
        OfficialAddon.Check check = OfficialAddon.check("NeoSkriptDb", Map.of(),
                Map.of("skript-db", plugins.resolve("SkriptDB.jar")), "1.0.0", "1.0.0");
        assertTrue(check.load());
        assertEquals(List.of("plugins/SkriptDB.jar is skript-db, which needs Skript, so it doesn't load with"
                + " NeoSkript. NeoSkriptDb replaces it: scripts written for skript-db should work unchanged"
                + " (NeoSkriptDb's docs list any differences). Remove SkriptDB.jar."), check.warnings());
    }

    @Test
    void itWarnsOnAnOlderNeoSkriptOnly() {
        assertEquals(1, OfficialAddon.check("A", Map.of(), Map.of(), "1.2.0", "1.1.9").warnings().size());
        assertEquals(List.of(), OfficialAddon.check("A", Map.of(), Map.of(), "1.2.0", "1.3.0").warnings());
        assertEquals(List.of(), OfficialAddon.check("A", Map.of(), Map.of(), "1.2.0", "1.2.0").warnings());
        assertEquals(List.of(), OfficialAddon.check("A", Map.of(), Map.of(), null, "1.2.0").warnings());
    }

    @Test
    void versionsCompareByTheirNumbers() {
        assertTrue(OfficialAddon.older("1.2.9", "1.2.10"));
        assertFalse(OfficialAddon.older("1.2.10", "1.2.9"));
        assertTrue(OfficialAddon.older("1.0.0-beta.1", "1.0.0"));
        assertFalse(OfficialAddon.older("1.0.0", "1.0.0-beta.1"));
        assertTrue(OfficialAddon.older("0.1.0-SNAPSHOT", "0.1.0"));
        assertFalse(OfficialAddon.older("1.0", "1.0.0"));
        assertFalse(OfficialAddon.older("dev", "1.0.0"));
    }
}
