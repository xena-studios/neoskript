/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
        assertEquals(neoskript.resolve("backups"), result.variablesBackup().getParent(),
                "the variables are backed up before they're first loaded");
        assertTrue(result.variablesBackup().getFileName().toString().matches("variables_[0-9_-]+\\.csv"));
        assertEquals("# variables\n", Files.readString(result.variablesBackup()));
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
    void readsSkriptsSettings() throws Exception {
        Path skript = skript();
        assertEquals(java.util.Map.of("language", "english"),
                SkriptMigration.migrate(skript, plugins.resolve("NeoSkript")).settings());
        Path config = skript.resolve("config.sk");
        Files.writeString(config, """
                enable effect commands: true
                effect command token: ##! # the token
                # If 'enable effect commands' is set to true, ...
                allow ops to use effect commands: false
                log effect commands: maybe
                	use player UUIDs in variable names: false # names
                """);
        // 'maybe' isn't a yes or no, so Skript runs with its default, off.
        assertEquals(java.util.Map.of("effect-commands.enabled", true, "effect-commands.token", "#!",
                "effect-commands.allow-ops", false, "effect-commands.log", false,
                "variables.player-uuids-in-names", false), SkriptSettings.read(config, null));
    }

    @Test
    void anUnreadableConfigSkStillCopiesTheScripts() throws Exception {
        Path skript = skript();
        Files.write(skript.resolve("config.sk"), new byte[] {(byte) 0xff, (byte) 0xfe, 'x', '\n'});
        SkriptMigration.Result result = SkriptMigration.migrate(skript, plugins.resolve("NeoSkript"));
        assertEquals(List.of("scripts", "variables.csv"), result.copied());
        assertEquals(java.util.Map.of(), result.settings());
        assertTrue(result.settingsProblem().contains("UTF-8"), result.settingsProblem());
    }

    @Test
    void copiesSkriptsLanguageFiles() throws Exception {
        Path skript = skript();
        Files.createDirectories(skript.resolve("lang"));
        Files.writeString(skript.resolve("lang/pirate.lang"), "none: <naught>\n");
        Files.writeString(skript.resolve("config.sk"), "language: Pirate\n");
        SkriptMigration.Result result = SkriptMigration.migrate(skript, plugins.resolve("NeoSkript"));
        assertEquals(List.of("scripts", "variables.csv", "lang"), result.copied());
        assertEquals("pirate", result.settings().get("language"));
        assertTrue(Files.exists(plugins.resolve("NeoSkript/lang/pirate.lang")));
    }
}
