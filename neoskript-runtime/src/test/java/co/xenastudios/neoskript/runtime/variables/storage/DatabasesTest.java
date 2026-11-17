/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Several databases, routed by pattern as in Skript. */
class DatabasesTest {

    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();
    private final Console console = new Console() {
        @Override
        public void info(String message) {}

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void error(String message) {
            warnings.add(message);
        }
    };

    @BeforeEach
    void fast() {
        SqlVariableStorage.tickMillis = 20;
        SqlVariableStorage.maxRetryMillis = 100;
    }

    @AfterEach
    void restore() {
        SqlVariableStorage.tickMillis = 1_000;
        SqlVariableStorage.maxRetryMillis = 30_000;
    }

    private static RawValue text(String text) {
        return new RawValue("string", text.getBytes(StandardCharsets.UTF_8));
    }

    private Databases.Definition csv(VariableStore store, String name, String pattern, String file) {
        return new Databases.Definition(name, "csv", pattern,
                routing -> new VariableStorage(store, new VariableCodecs(), dir.resolve(file), routing));
    }

    private Databases.Definition sqlite(VariableStore store, String name, String pattern, String file) {
        return new Databases.Definition(name, "sqlite", pattern, routing -> new SqlVariableStorage(store,
                new VariableCodecs(), SqlVariableStorage.Settings.sqlite(name, dir.resolve(file), "variables21", false,
                        20_000), routing, dir.resolve("pending"), console));
    }

    private Map<String, VariableFile.Entry> file(String name) throws IOException {
        return VariableFile.read(dir.resolve(name));
    }

    private static void write(Path file, String... names) throws IOException {
        List<VariableFile.Entry> entries = new ArrayList<>();
        for (String name : names) {
            entries.add(new VariableFile.Entry(name, "string", name.getBytes(StandardCharsets.UTF_8)));
        }
        VariableFile.write(file, entries);
    }

    @Test
    void eachVariableGoesToTheFirstDatabaseWhosePatternMatches() throws Exception {
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "shop", "shop_.*", "shop.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            store.set("shop_price", text("5"));
            store.set("money", text("10"));
            store.set("-temp", text("never"));
            databases.save();
        }
        assertEquals(List.of("shop_price"), List.copyOf(file("shop.csv").keySet()));
        assertEquals(List.of("money"), List.copyOf(file("variables.csv").keySet()));
    }

    @Test
    void aVariableNoPatternMatchesIsntSavedInAFile() throws Exception {
        write(dir.resolve("variables.csv"), "keep", "x_drop");
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "a", "a_.*", "a.csv"),
                csv(store, "default", "(?!x_).*", "variables.csv")), console)) {
            databases.load();
            assertNotNull(store.get("x_drop"));
            databases.save();
        }
        assertEquals(List.of("keep"), List.copyOf(file("variables.csv").keySet()));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("1 variables were possibly discarded")), warnings::toString);
    }

    @Test
    void aVariableInTwoDatabasesIsKeptFromTheLowerOne() throws Exception {
        VariableFile.write(dir.resolve("a.csv"), List.of(new VariableFile.Entry("both", "string", new byte[] {1})));
        VariableFile.write(dir.resolve("variables.csv"),
                List.of(new VariableFile.Entry("both", "string", new byte[] {2})));
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "a", "a_.*", "a.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            assertEquals(new RawValue("string", new byte[] {2}), store.get("both"));
            databases.save();
        }
        assertTrue(warnings.contains("The variable {both} was loaded twice from different databases (a and default), "
                + "only the one from default will be kept."), warnings::toString);
        assertTrue(file("a.csv").isEmpty());
        assertEquals(1, file("variables.csv").size());
    }

    @Test
    void aVariableMovesToTheDatabaseItNowBelongsTo() throws Exception {
        write(dir.resolve("variables.csv"), "db_points", "money");
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                sqlite(store, "sqlite", "db_.*", "variables.db"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            databases.save();
            // The SQL database writes on its own thread; the CSV lets go once it has.
            waitUntilWritten(databases);
            databases.save();
        }
        assertEquals(List.of("money"), List.copyOf(file("variables.csv").keySet()));
        VariableStore reloaded = new VariableStore();
        try (Databases databases = Databases.open(reloaded, new VariableCodecs(), List.of(
                sqlite(reloaded, "sqlite", "db_.*", "variables.db"),
                csv(reloaded, "default", ".*", "variables.csv")), console)) {
            databases.load();
            assertNotNull(reloaded.get("db_points"));
            assertNotNull(reloaded.get("money"));
        }
    }

    @Test
    void aDatabaseThatFailsKeepsItsVariablesOutOfTheOthers() throws Exception {
        Files.createDirectories(dir.resolve("broken.csv"));
        write(dir.resolve("variables.csv"), "money");
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "broken", "b_.*", "broken.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            assertTrue(String.join("\n", warnings).contains("Database 'broken' isn't used"), warnings::toString);
            store.set("b_thing", text("1"));
            store.set("other", text("2"));
            databases.save();
        }
        // b_thing belongs to the broken database, so it isn't put in variables.csv where it would shadow it later.
        assertEquals(List.of("money", "other"), List.copyOf(file("variables.csv").keySet()));
    }

    @Test
    void aFileKeepsAVariableThatBelongsToADatabaseThatCantLoad() throws Exception {
        Files.createDirectories(dir.resolve("broken.csv"));
        write(dir.resolve("variables.csv"), "b_old", "money");
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "broken", "b_.*", "broken.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            store.set("money", text("3"));
            databases.save();
        }
        assertEquals(List.of("b_old", "money"), List.copyOf(file("variables.csv").keySet()));
    }

    @Test
    void anInvalidPatternIsSkriptsError() throws Exception {
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "bad", "(unclosed", "bad.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            assertTrue(String.join("\n", warnings).contains("Invalid pattern '(unclosed'"), warnings::toString);
            store.set("x", text("1"));
            databases.save();
        }
        assertEquals(List.of("x"), List.copyOf(file("variables.csv").keySet()));
    }

    @Test
    void onlyOneDatabaseThatCantLoadMeansNoVariables() throws Exception {
        Files.createDirectories(dir.resolve("variables.csv"));
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "default", ".*", "variables.csv")), console)) {
            assertThrows(IOException.class, databases::load);
            assertFalse(databases.loaded());
        }
    }

    @Test
    void detachingUsesWhatTheOwningDatabaseHas() throws Exception {
        VariableStore store = new VariableStore();
        try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                csv(store, "a", "a_.*", "a.csv"),
                csv(store, "default", ".*", "variables.csv")), console)) {
            databases.load();
            store.set("a_x", text("1"));
            databases.save();
            assertNotNull(databases.previous("a_x"));
            assertNull(databases.previous("nothing"));
        }
    }

    @Test
    void aDatabaseThatLoadsLateNeverReplacesAnotherDatabasesVariables() throws Exception {
        SqlVariableStorage.startupTimeoutMillis = 500;
        try {
            // An old money row in the SQL table, from before the patterns changed; the CSV has the current money.
            VariableStore before = new VariableStore();
            try (Databases databases = Databases.open(before, new VariableCodecs(), List.of(
                    sqlite(before, "players", ".*", "players.db")), console)) {
                databases.load();
                before.set("money", text("stale"));
                before.set("player_1", text("p"));
                databases.save();
                waitUntilWritten(databases);
            }
            VariableFile.write(dir.resolve("variables.csv"),
                    List.of(new VariableFile.Entry("money", "string", "current".getBytes(StandardCharsets.UTF_8))));
            VariableStore store = new VariableStore();
            try (java.sql.Connection locker = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("players.db"))) {
                try (java.sql.Statement s = locker.createStatement()) {
                    s.execute("BEGIN EXCLUSIVE");
                }
                try (Databases databases = Databases.open(store, new VariableCodecs(), List.of(
                        sqlite(store, "players", "player_.*", "players.db"),
                        csv(store, "default", ".*", "variables.csv")), console)) {
                    databases.load();
                    assertEquals(text("current"), store.get("money"));
                    try (java.sql.Statement s = locker.createStatement()) {
                        s.execute("ROLLBACK");
                    }
                    SqlVariableStorage sql = (SqlVariableStorage) databases.byName().get("players").backend();
                    long until = System.currentTimeMillis() + 10_000;
                    while (!sql.loaded() && System.currentTimeMillis() < until) {
                        Thread.sleep(20);
                    }
                    assertTrue(sql.loaded());
                    assertEquals(text("current"), store.get("money"), "the late load left the CSV's money alone");
                    assertEquals(text("p"), store.get("player_1"));
                    databases.save();
                }
            }
            assertEquals("current", new String(file("variables.csv").get("money").bytes(), StandardCharsets.UTF_8));
        } finally {
            SqlVariableStorage.startupTimeoutMillis = 10_000;
        }
    }

    private static void waitUntilWritten(Databases databases) throws InterruptedException {
        for (Databases.Database database : databases.databases()) {
            if (database.backend() instanceof SqlVariableStorage sql) {
                long until = System.currentTimeMillis() + 10_000;
                while (!(sql.written() && sql.status().waiting() == 0) && System.currentTimeMillis() < until) {
                    Thread.sleep(20);
                }
            }
        }
    }

    @Test
    void purgingOldBackupsNeverRemovesTheOnesKeptForSafety() throws Exception {
        Path file = dir.resolve("variables.csv");
        write(file, "x");
        Path safety = VariableStorage.backup(file);
        Path older = dir.resolve("backups").resolve("variables_2020-01-01_00-00-00.csv");
        Files.writeString(older, "from an older NeoSkript");
        VariableStorage storage = new VariableStorage(new VariableStore(), new VariableCodecs(), file);
        storage.load();
        Path scheduled = storage.backupNow();
        assertTrue(scheduled.getFileName().toString().endsWith("_scheduled.csv"), scheduled.toString());
        assertEquals(1, VariableStorage.purgeBackups(file, 0));
        assertTrue(Files.exists(safety));
        assertTrue(Files.exists(older));
        assertFalse(Files.exists(scheduled));
    }
}
