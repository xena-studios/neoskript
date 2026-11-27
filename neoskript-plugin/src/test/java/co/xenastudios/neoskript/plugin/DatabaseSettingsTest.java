/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The databases: section of config.yml, with Skript's options and messages. */
class DatabaseSettingsTest {

    @TempDir
    Path folder;

    private ConfigLoader.Result<NeoSkriptConfig> load(String databases) throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, "config-version: 3\ndatabases:\n" + databases);
        return ConfigLoader.load(NeoSkriptConfig.SPEC, file);
    }

    @Test
    void readsSkriptsOptionsInEitherSpelling() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> result = load("""
                  synced:
                    type: MySQL
                    pattern: synced_.*
                    monitor changes: true
                    monitor-interval: 5 seconds
                    host: db.example
                    port: 3307
                    user: mc
                    password: secret
                    database: skript
                  local:
                    type: sqlite
                    pattern: db_.*
                    file: data/variables
                    monitor-changes: true
                    merge changes: false
                  off:
                    type: disabled
                  default:
                    type: CSV
                    pattern: .*
                    file: variables.csv
                    backup interval: 0
                    backups to keep: 3
                """);
        assertEquals(List.of(), result.warnings());
        List<DatabaseSettings.Database> all = result.config().databases().enabled();
        assertEquals(List.of("synced", "local", "default"), all.stream().map(DatabaseSettings.Database::name).toList());
        DatabaseSettings.Database synced = all.get(0);
        assertEquals(DatabaseSettings.Type.MYSQL, synced.type());
        assertTrue(synced.monitorChanges());
        assertEquals(5_000, synced.monitorInterval().millis());
        assertEquals(3307, synced.port());
        assertEquals("variables21", synced.table());
        assertNull(synced.problem());
        assertFalse(synced.toString().contains("secret"), "the password never shows");
        assertEquals("data/variables.db", all.get(1).file(), "SQLite files end in .db, as in Skript");
        assertTrue(synced.mergeChanges(), "merging is on unless turned off");
        assertFalse(all.get(1).mergeChanges());
        assertEquals(false, DatabaseSettings.settings(all.get(1)).get("merge-changes"));
        assertFalse(DatabaseSettings.settings(synced).containsKey("merge-changes"), "written only when off");
        assertNull(all.get(2).backupInterval());
        assertEquals(3, all.get(2).backupsToKeep());
    }

    @Test
    void backupsFollowSkriptsRulesWithNothingRaised() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> result = load("""
                  never:
                    type: csv
                    pattern: a_.*
                    file: a.csv
                    backup interval: 0
                    backups to keep: 0
                  zero:
                    type: csv
                    pattern: b_.*
                    file: b.csv
                    backup interval: 0 seconds
                    backups to keep: 0
                  zerokept:
                    type: csv
                    pattern: c_.*
                    file: c.csv
                    backup interval: 0 seconds
                    backups to keep: 3
                  short:
                    type: sqlite
                    pattern: d_.*
                    file: d
                    backup interval: 10 seconds
                    backups to keep: 2
                  tiny:
                    type: csv
                    pattern: e_.*
                    file: e.csv
                    backup interval: 0.01 seconds
                    backups to keep: 2
                  default:
                    type: csv
                    pattern: .*
                    file: variables.csv
                """);
        assertEquals(List.of(), result.warnings());
        java.util.Map<String, NeoSkriptPlugin.BackupPlan> plans = new java.util.LinkedHashMap<>();
        for (DatabaseSettings.Database database : result.config().databases().enabled()) {
            plans.put(database.name(), NeoSkriptPlugin.BackupPlan.of(database.backupInterval(), database.backupsToKeep()));
        }
        // 'backup interval: 0' is off, as Skript checks before anything else: with backups-to-keep 0 too, nothing
        // is removed, at startup or on a reload.
        assertEquals(new NeoSkriptPlugin.BackupPlan(false, 0), plans.get("never"));
        // Any other interval with backups-to-keep 0 removes them, as Skript's backupPurge(file, 0) does.
        assertEquals(new NeoSkriptPlugin.BackupPlan(true, 0), plans.get("zero"));
        // Under a tick, Skript's task doesn't start, and nothing is removed.
        assertEquals(new NeoSkriptPlugin.BackupPlan(false, 0), plans.get("zerokept"));
        assertEquals(new NeoSkriptPlugin.BackupPlan(false, 0), plans.get("tiny"));
        // Not raised to a minute: every 10 seconds, as written.
        assertEquals(new NeoSkriptPlugin.BackupPlan(false, 10_000), plans.get("short"));
        assertEquals(new NeoSkriptPlugin.BackupPlan(false, 0), plans.get("default"), "left out, there are none");
        String written = Files.readString(folder.resolve("config.yml"));
        assertTrue(written.contains("10 seconds"), written);
    }

    @Test
    void aDatabaseThatCantBeUsedIsKeptWithWhy() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> result = load("""
                  typo:
                    type: mysqll
                    pattern: synced_.*
                  nopattern:
                    type: csv
                    file: other.csv
                  nopassword:
                    type: mysql
                    pattern: x_.*
                    host: localhost
                    port: 99999
                    user: root
                    database: skript
                  twice:
                    type: csv
                    pattern: .*
                    file: variables.csv
                  default:
                    type: csv
                    pattern: .*
                    file: ./variables.csv
                    colour: blue
                """);
        List<DatabaseSettings.Database> all = result.config().databases().enabled();
        assertEquals(5, all.size());
        assertEquals("Invalid database type 'mysqll'", all.get(0).problem());
        assertEquals("synced_.*", all.get(0).pattern(), "it still owns its pattern");
        assertTrue(all.get(1).problem().contains("missing the entry for 'pattern'"));
        assertTrue(all.get(2).problem().contains("'password'"));
        assertTrue(all.get(2).problem().contains("'port'"));
        assertNull(all.get(3).problem());
        assertTrue(all.get(4).problem().contains("already used by another database"));
        String warnings = String.join("\n", result.warnings());
        assertTrue(warnings.contains("databases.default.colour isn't a database setting"), warnings);
        assertTrue(warnings.contains("databases.typo: Invalid database type 'mysqll'. Its variables aren't saved"),
                warnings);
    }

    @Test
    void noDatabasesIsSkriptsErrorAndTheDefault() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> result = load("""
                  never:
                    type: disabled
                """);
        assertEquals(DatabaseSettings.DEFAULTS, result.config().databases());
        assertTrue(result.warnings().getFirst().contains("Please enable at least the default database"),
                result.warnings().toString());
    }

    @Test
    void version2StorageBecomesTheDefaultDatabase() throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, "config-version: 2\ntheme: {}\nvariables:\n  file: data/vars.csv\n  storage: native\n"
                + "  save-interval: 1 minute\n");
        ConfigLoader.Result<NeoSkriptConfig> result = ConfigLoader.load(NeoSkriptConfig.SPEC, file);
        DatabaseSettings.Database database = result.config().databases().enabled().getFirst();
        assertEquals("default", database.name());
        assertEquals(DatabaseSettings.Type.NATIVE, database.type());
        assertEquals("data/vars.csv", database.file());
        assertEquals(60_000, result.config().variables().saveInterval().millis());
        String text = Files.readString(file);
        assertFalse(text.contains("storage:"), text);
        assertTrue(text.contains("databases:\n  default:\n    type: \"native\"\n"), text);
        // The harness's way of writing it: only the setting, no version.
        Files.writeString(file, "variables:\n  storage: native\n");
        assertEquals(DatabaseSettings.Type.NATIVE,
                ConfigLoader.load(NeoSkriptConfig.SPEC, file).config().databases().enabled().getFirst().type());
    }

    @Test
    void anEditedSectionKeepsTheOwnersComments() throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, "variables:\n  file: variables.csv\n  storage: csv\ntheme: {}\nconfig-version: 2\ndatabases:\n  # Mine.\n  shop:\n    type: csv\n"
                + "    pattern: shop_.*\n    file: shop.csv\n  default:\n    type: csv\n    pattern: .*\n"
                + "    file: variables.csv\n");
        ConfigLoader.load(NeoSkriptConfig.SPEC, file);
        String text = Files.readString(file);
        assertTrue(text.contains("  # Mine.\n  shop:\n"), text);
        assertFalse(text.contains("storage:"), text);
    }

    @Test
    void anUnknownVersion2StorageKeepsTheFileInUse() throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, "config-version: 2\ntheme: {}\nvariables:\n  storage: nativ\n");
        ConfigLoader.Result<NeoSkriptConfig> result = ConfigLoader.load(NeoSkriptConfig.SPEC, file);
        DatabaseSettings.Database database = result.config().databases().enabled().getFirst();
        assertNull(database.problem(), "the variables still load");
        assertEquals(DatabaseSettings.Type.CSV, database.type());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Unknown variables.storage 'nativ'")),
                result.warnings().toString());

        java.util.function.Predicate<String> before = NeoSkriptConfig.nativeInUse;
        try {
            NeoSkriptConfig.nativeInUse = name -> name.equals("variables.csv");
            Files.writeString(file, "config-version: 2\ntheme: {}\nvariables:\n  storage: nativ\n");
            assertEquals(DatabaseSettings.Type.NATIVE,
                    ConfigLoader.load(NeoSkriptConfig.SPEC, file).config().databases().enabled().getFirst().type());
        } finally {
            NeoSkriptConfig.nativeInUse = before;
        }
    }

    @Test
    void twoDatabasesOnOneTableOrFileAreRefused() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> result = load("""
                  a:
                    type: mysql
                    pattern: a_.*
                    host: DB.example
                    port: 3306
                    user: x
                    password: y
                    database: skript
                  b:
                    type: mysql
                    pattern: b_.*
                    host: db.example
                    port: 3306
                    user: other
                    password: z
                    database: skript
                    table: VARIABLES21
                  c:
                    type: sqlite
                    pattern: c_.*
                    file: data/../vars
                  d:
                    type: sqlite
                    pattern: d_.*
                    file: vars.db
                  default:
                    type: csv
                    pattern: .*
                    file: variables.csv
                """);
        List<DatabaseSettings.Database> all = result.config().databases().enabled();
        assertNull(all.get(0).problem());
        assertTrue(all.get(1).problem().contains("already used by another database"), all.get(1).problem());
        assertNull(all.get(2).problem());
        assertTrue(all.get(3).problem().contains("already used by another database"), all.get(3).problem());
    }
}
