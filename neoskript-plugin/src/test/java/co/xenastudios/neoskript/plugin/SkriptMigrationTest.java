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
    void copiesTheDatabasesSectionAndTheFilesItNames() throws Exception {
        // As on a server: paths in config.sk are relative to the server's folder.
        Path skript = plugins.resolve("plugins/Skript");
        Files.createDirectories(skript.resolve("scripts"));
        Files.writeString(skript.resolve("scripts/a.sk"), "on load:\n\tbroadcast \"a\"\n");
        Files.writeString(skript.resolve("variables.csv"), "# variables\n");
        Files.writeString(skript.resolve("variables.db"), "sqlite");
        Files.writeString(skript.resolve("variables.db-journal"), "journal");
        Files.writeString(skript.resolve("config.sk"), String.join("\n",
                "language: english",
                "databases:",
                "\t# Skript's example.",
                "\tdatabase 1:",
                "\t\ttype: SQLite",
                "\t\tpattern: db_.*",
                "\t\tmonitor changes: false",
                "\t\tmonitor interval: 20 seconds",
                "\t\tfile: ./plugins/Skript/variables",
                "\t\tbackup interval: 0",
                "\tshared:",
                "\t\ttype: CSV",
                "\t\tpattern: shared_.*",
                "\t\tfile: /srv/shared/variables.csv",
                "\tsynced:",
                "\t\ttype: MySQL",
                "\t\tpattern: synced_.*",
                "\t\thost: localhost",
                "\t\tport: 3306",
                "\t\tuser: root",
                "\t\tpassword: pass ## with a hash",
                "\t\tdatabase: skript",
                "\tdefault:",
                "\t\ttype: CSV",
                "\t\tpattern: .*",
                "\t\tfile: ./plugins/Skript/variables.csv",
                "\t\tbackup interval: 2 hours",
                "\t\tbackups to keep: -1",
                "",
                "version: 2.17.0",
                ""));
        Path neoskript = plugins.resolve("plugins/NeoSkript");
        SkriptMigration.Result result = SkriptMigration.migrate(skript, neoskript);
        assertEquals(List.of("scripts", "variables.db", "variables.csv"), result.copied());
        assertEquals("sqlite", Files.readString(neoskript.resolve("variables.db")));
        assertEquals("journal", Files.readString(neoskript.resolve("variables.db-journal")));
        assertTrue(Files.exists(neoskript.resolve("variables.csv")));
        @SuppressWarnings("unchecked")
        java.util.Map<String, java.util.Map<String, Object>> databases =
                (java.util.Map<String, java.util.Map<String, Object>>) result.settings().get("databases");
        assertEquals(List.of("database 1", "shared", "synced", "default"), List.copyOf(databases.keySet()));
        assertEquals("variables.db", databases.get("database 1").get("file"));
        assertEquals("20 seconds", databases.get("database 1").get("monitor-interval"));
        assertEquals(Path.of("/srv/shared/variables.csv").toAbsolutePath().toString(), databases.get("shared").get("file"),
                "a file outside Skript's folder is shared, so it's used where it is");
        assertEquals("pass # with a hash", databases.get("synced").get("password"));
        assertEquals("variables.csv", databases.get("default").get("file"));
        assertEquals("-1", databases.get("default").get("backups-to-keep"));
        assertTrue(Files.exists(skript.resolve("variables.db")), "Skript's files stay where they were");
    }
}
