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
    void readsSkriptsPlayerUuidSetting() throws Exception {
        Path skript = skript();
        assertEquals(null, SkriptMigration.migrate(skript, plugins.resolve("NeoSkript")).playerUuids());
        Path config = skript.resolve("config.sk");
        Files.writeString(config, "language: english\n\tuse player UUIDs in variable names: false # names\n");
        assertEquals(Boolean.FALSE, SkriptMigration.playerUuids(config));
        Files.writeString(config, "use player UUIDs in variable names: true\n");
        assertEquals(Boolean.TRUE, SkriptMigration.playerUuids(config));
    }

    @Test
    void readsSkriptsEffectCommandSettings() throws Exception {
        Path skript = skript();
        assertEquals(java.util.Map.of(), SkriptMigration.migrate(skript, plugins.resolve("NeoSkript")).settings());
        Path config = skript.resolve("config.sk");
        Files.writeString(config, """
                enable effect commands: true
                effect command token: ##! # the token
                # If 'enable effect commands' is set to true, ...
                allow ops to use effect commands: false
                log effect commands: maybe
                """);
        assertEquals(java.util.Map.of("effect-commands.enabled", true, "effect-commands.token", "#!",
                "effect-commands.allow-ops", false), SkriptMigration.settings(config));
    }

    @Test
    void copiesSkriptsAliasFilesAndTheirSetting() throws Exception {
        Path skript = skript();
        Files.createDirectories(skript.resolve("aliases/sub"));
        Files.writeString(skript.resolve("aliases/gems.sk"), "gems:\n\truby¦s = minecraft:red_dye\n");
        Files.writeString(skript.resolve("aliases/sub/more.sk"), "more:\n\ttopaz = minecraft:gold_ingot\n");
        Files.writeString(skript.resolve("config.sk"), "load default aliases: false # our own\n");
        Path neoskript = plugins.resolve("NeoSkript");

        SkriptMigration.Result result = SkriptMigration.migrate(skript, neoskript);
        assertEquals(List.of("scripts", "variables.csv", "aliases"), result.copied());
        assertEquals(2, result.scriptCount(), "alias files aren't scripts");
        assertEquals("gems:\n\truby¦s = minecraft:red_dye\n", Files.readString(neoskript.resolve("aliases/gems.sk")));
        assertTrue(Files.exists(neoskript.resolve("aliases/sub/more.sk")));
        assertTrue(Files.exists(skript.resolve("aliases/gems.sk")), "copied, not moved");
        assertEquals(java.util.Map.of("aliases.load-defaults", Boolean.FALSE), result.settings());
    }

    @Test
    void copiesAliasFilesOnceForAServerThatMovedOverBefore() throws Exception {
        Path skript = skript();
        Path neoskript = plugins.resolve("NeoSkript");
        SkriptMigration.migrate(skript, neoskript);
        assertFalse(Files.exists(neoskript.resolve("aliases")));

        // A move from before NeoSkript read alias files left a marker without its alias line.
        Path marker = neoskript.resolve(SkriptMigration.MARKER);
        Files.writeString(marker, Files.readString(marker).replace(SkriptMigration.ALIASES_DONE + "\n", ""));
        Files.createDirectories(skript.resolve("aliases"));
        Files.writeString(skript.resolve("aliases/gems.sk"), "gems:\n\truby = minecraft:red_dye\n");
        Files.writeString(skript.resolve("config.sk"), "load default aliases: false\nenable effect commands: true\n");
        SkriptMigration.Result later = SkriptMigration.migrate(skript, neoskript);
        assertEquals(List.of("aliases"), later.copied());
        assertEquals(java.util.Map.of("aliases.load-defaults", Boolean.FALSE), later.settings(),
                "only the aliases' setting; the others were copied the first time");
        assertTrue(Files.exists(neoskript.resolve("aliases/gems.sk")));

        Files.delete(neoskript.resolve("aliases/gems.sk"));
        Files.delete(neoskript.resolve("aliases"));
        assertEquals(List.of(), SkriptMigration.migrate(skript, neoskript).copied(), "only once");
    }

    @Test
    void anAliasFileNeoSkriptAlreadyHasIsKeptAndTheMoveStillFinishes() throws Exception {
        Path skript = skript();
        Files.createDirectories(skript.resolve("aliases"));
        Files.writeString(skript.resolve("aliases/gems.sk"), "gems:\n\truby = minecraft:red_dye\n");
        Files.writeString(skript.resolve("aliases/more.sk"), "more:\n\ttopaz = minecraft:gold_ingot\n");
        Files.writeString(skript.resolve("config.sk"), "load default aliases: false\n");
        Path neoskript = plugins.resolve("NeoSkript");
        Files.createDirectories(neoskript.resolve("aliases"));
        Files.writeString(neoskript.resolve("aliases/gems.sk"), "mine:\n\truby = minecraft:redstone\n");

        SkriptMigration.Result result = SkriptMigration.migrate(skript, neoskript);
        assertEquals(List.of("scripts", "variables.csv", "aliases"), result.copied());
        assertEquals("mine:\n\truby = minecraft:redstone\n", Files.readString(neoskript.resolve("aliases/gems.sk")),
                "the owner's file is kept");
        assertTrue(Files.exists(neoskript.resolve("aliases/more.sk")));
        assertEquals(List.of("Kept plugins/NeoSkript/aliases/gems.sk, which was there already, rather than Skript's"),
                result.notes());
        assertTrue(result.variablesBackup() != null, "the variables are still backed up");
        assertTrue(Files.exists(neoskript.resolve(SkriptMigration.MARKER)), "the marker is still written");
        assertEquals(java.util.Map.of("aliases.load-defaults", Boolean.FALSE), result.settings(),
                "the settings are still copied");
        assertEquals(List.of(), SkriptMigration.migrate(skript, neoskript).copied());
    }

    @Test
    void aServerPathWithAliasesInItStillGetsTheAliasesLater() throws Exception {
        Path base = plugins.resolve("my-aliases-server");
        Path skript = base.resolve("Skript");
        Files.createDirectories(skript.resolve("scripts"));
        Files.writeString(skript.resolve("scripts/a.sk"), "on load:\n\tstop\n");
        Path neoskript = base.resolve("NeoSkript");
        // A marker from before NeoSkript read alias files, with the server's path in it.
        Files.createDirectories(neoskript);
        Files.writeString(neoskript.resolve(SkriptMigration.MARKER), "Copied [scripts] from "
                + skript.toAbsolutePath() + " at 2026-11-12T00:00:00Z\n");
        Files.createDirectories(skript.resolve("aliases"));
        Files.writeString(skript.resolve("aliases/gems.sk"), "gems:\n\truby = minecraft:red_dye\n");
        assertEquals(List.of("aliases"), SkriptMigration.migrate(skript, neoskript).copied());
        assertTrue(Files.exists(neoskript.resolve("aliases/gems.sk")));
    }
}
