/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkriptMigrationTest {

    @TempDir
    Path plugins;

    private Path skript() throws Exception {
        Path skript = plugins.resolve("Skript");
        Files.createDirectories(skript.resolve("scripts/sub"));
        Files.writeString(skript.resolve("scripts/a.sk"), "on load:\n\tbroadcast \"a\"\n");
        Files.writeString(skript.resolve("scripts/sub/-disabled.sk"), "on load:\n\tstop\n");
        Files.writeString(skript.resolve("variables.csv"), "# variables\n");
        Files.writeString(skript.resolve("config.sk"), "language: english\n");
        return skript;
    }

    @Test
    void copiesScriptsAndVariablesOnce() throws Exception {
        Path skript = skript();
        Path neoskript = plugins.resolve("NeoSkript");

        SkriptMigration.Result result = SkriptMigration.migrate(skript, neoskript);
        assertEquals(List.of("scripts", "variables.csv"), result.copied());
        assertEquals(2, result.scriptCount());
        assertEquals("on load:\n\tbroadcast \"a\"\n", Files.readString(neoskript.resolve("scripts/a.sk")));
        assertTrue(Files.exists(neoskript.resolve("scripts/sub/-disabled.sk")));
        assertTrue(Files.exists(neoskript.resolve("variables.csv")));
        assertFalse(Files.exists(neoskript.resolve("config.sk")), "Skript's config isn't NeoSkript's");
        assertTrue(Files.exists(skript.resolve("scripts/a.sk")), "Skript's files stay where they were");

        Files.delete(neoskript.resolve("scripts/a.sk"));
        assertEquals(List.of(), SkriptMigration.migrate(skript, neoskript).copied(), "only the first time");
        assertFalse(Files.exists(neoskript.resolve("scripts/a.sk")));
    }

    @Test
    void doesNothingWithoutSkriptOrWhenNeoSkriptHasScripts() throws Exception {
        Path neoskript = plugins.resolve("NeoSkript");
        assertEquals(List.of(), SkriptMigration.migrate(plugins.resolve("Skript"), neoskript).copied());

        Path skript = skript();
        Files.createDirectories(neoskript.resolve("scripts"));
        assertEquals(List.of(), SkriptMigration.migrate(skript, neoskript).copied());
        assertFalse(Files.exists(neoskript.resolve("variables.csv")));
    }

    @Test
    void readsSkriptsPlayerUuidSetting() throws Exception {
        Path skript = skript();
        assertEquals(null, SkriptMigration.migrate(skript, plugins.resolve("NeoSkript")).playerUuids());
        Path config = skript.resolve("config.sk");
        Files.writeString(config, "language: english\n\tuse player UUIDs in variable names: false # names\n");
        assertEquals(Boolean.FALSE, SkriptMigration.playerUuids(config));
        Files.writeString(config, "use player UUIDs in variable names: true\n");
        assertEquals(Boolean.TRUE, SkriptMigration.playerUuids(config));
    }
}
