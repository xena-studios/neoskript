/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLite variable storage against a real SQLite file, in Skript's table layout. */
class SqlVariableStorageTest {

    @TempDir
    Path dir;

    private final List<String> console = new ArrayList<>();
    private final Console log = new Console() {
        @Override
        public void info(String message) {
            synchronized (console) {
                console.add("INFO " + message);
            }
        }

        @Override
        public void warn(String message) {
            synchronized (console) {
                console.add("WARN " + message);
            }
        }

        @Override
        public void error(String message) {
            synchronized (console) {
                console.add("ERROR " + message);
            }
        }
    };

    @BeforeEach
    void fast() {
        SqlVariableStorage.tickMillis = 20;
        SqlVariableStorage.maxRetryMillis = 100;
        SqlVariableStorage.closeTimeoutMillis = 5_000;
    }

    @AfterEach
    void restore() {
        SqlVariableStorage.tickMillis = 1_000;
        SqlVariableStorage.maxRetryMillis = 30_000;
        SqlVariableStorage.closeTimeoutMillis = 15_000;
        SqlVariableStorage.extraProperties = null;
    }

    private Path db() {
        return dir.resolve("variables.db");
    }

    private SqlVariableStorage storage(VariableStore store) {
        return storage(store, false);
    }

    private SqlVariableStorage storage(VariableStore store, boolean monitor) {
        return new SqlVariableStorage(store, new VariableCodecs(),
                SqlVariableStorage.Settings.sqlite("test", db(), "variables21", monitor, 100), Routing.ALL,
                dir.resolve("pending").resolve("test.pending.csv"), log);
    }

    private static RawValue text(String text) {
        return new RawValue("string", text.getBytes(StandardCharsets.UTF_8));
    }

    /** Holds an exclusive lock on the file, as another program writing to it would. */
    private static void lock(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("BEGIN EXCLUSIVE");
        }
    }

    private static void unlock(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("ROLLBACK");
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db());
    }

    /** The table's rows: name to {type, value, update_guid}. */
    private Map<String, Object[]> rows() throws SQLException {
        Map<String, Object[]> rows = new LinkedHashMap<>();
        try (Connection c = open(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT name, type, value, update_guid FROM variables21 ORDER BY rowid")) {
            while (r.next()) {
                rows.put(r.getString(1), new Object[] {r.getString(2), r.getBytes(3), r.getString(4)});
            }
        }
        return rows;
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void makesSkriptsTableExactly() throws Exception {
        try (SqlVariableStorage storage = storage(new VariableStore())) {
            storage.load();
        }
        try (Connection c = open(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'variables21'")) {
            assertTrue(r.next());
            // SQLiteStorage's query in Skript 2.17.0, as SQLite keeps it.
            assertEquals("CREATE TABLE variables21 (name         VARCHAR(380)  NOT NULL  PRIMARY KEY,"
                    + "type         VARCHAR(50),value        BLOB(10000),update_guid  CHAR(36)  NOT NULL)", r.getString(1));
        }
    }

    @Test
    void savesAndLoadsBackWithSkriptsEncoding() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("money::alice", text("100"));
            store.set("Spawn", new RawValue("location", new byte[] {1, 2, 3}));
            store.set("-temp", text("never saved"));
            storage.save();
        }
        Map<String, Object[]> rows = rows();
        assertEquals(List.of("money::alice", "spawn"), List.copyOf(rows.keySet()));
        assertEquals("location", rows.get("spawn")[0]);
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) rows.get("spawn")[1]);
        assertEquals(36, ((String) rows.get("spawn")[2]).length());

        VariableStore loaded = new VariableStore();
        try (SqlVariableStorage storage = storage(loaded)) {
            assertEquals(2, storage.load().variables());
        }
        assertEquals(text("100"), loaded.get("money::alice"));
        assertEquals(new RawValue("location", new byte[] {1, 2, 3}), loaded.get("spawn"));
    }

    @Test
    void writesOnlyWhatChangedAndDeletesRows() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("a", text("1"));
            store.set("b", text("2"));
            storage.save();
            String guid = (String) rows().get("a")[2];
            try (Connection c = open(); Statement s = c.createStatement()) {
                // Another server's write to b: an unchanged a and b aren't written over.
                s.executeUpdate("UPDATE variables21 SET update_guid = 'other' WHERE name = 'b'");
            }
            store.set("a", null);
            storage.save();
            Map<String, Object[]> rows = rows();
            assertFalse(rows.containsKey("a"));
            assertEquals("other", rows.get("b")[2]);
            assertFalse(guid.equals("other"));
        }
    }

    @Test
    void readsATableSkriptWroteAndKeepsWhatItCantRead() throws Exception {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate(SqlVariableStorage.Dialect.SQLITE.createTable("variables21"));
            s.executeUpdate("INSERT INTO variables21 (name, type, value, update_guid) VALUES "
                    + "('x', 'string', X'0568656C6C6F', 'skript'), "
                    + "('gone', NULL, NULL, 'skript'), "
                    + "('addon', 'someaddontype', X'00FF', 'skript')");
        }
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            VariableStorage.Result result = storage.load();
            assertEquals(2, result.variables());
            assertEquals(new RawValue("someaddontype", new byte[] {0, (byte) 0xFF}), store.get("addon"));
            assertNull(store.get("gone"));
            store.set("y", text("new"));
            storage.save();
        }
        // The value NeoSkript can't read is still there, byte for byte.
        assertArrayEquals(new byte[] {0, (byte) 0xFF}, (byte[]) rows().get("addon")[1]);
        assertTrue(rows().containsKey("y"));
    }

    @Test
    void rowidsOnlyGoUpSoWatchersNeverMissARow() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("a", text("1"));
            store.set("b", text("1"));
            storage.save();
            long before = maxRowid();
            // b has the highest rowid; replacing it must not give the new row the same one.
            store.set("b", text("2"));
            storage.save();
            assertTrue(maxRowid() > before);
        }
    }

    private long maxRowid() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT MAX(rowid) FROM variables21")) {
            r.next();
            return r.getLong(1);
        }
    }

    @Test
    void aLockedFileHoldsWritesBackUntilItsFree() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("a", text("1"));
            storage.save();
            try (Connection locker = open()) {
                lock(locker);
                store.set("a", text("2"));
                store.set("b", text("3"));
                waitFor(() -> storage.status().state() == SqlVariableStorage.State.DOWN);
                assertEquals(2, storage.status().waiting());
                assertThrows(IOException.class, storage::save);
                unlock(locker);
            }
            waitFor(() -> storage.status().state() == SqlVariableStorage.State.CONNECTED
                    && storage.status().waiting() == 0);
        }
        assertArrayEquals("2".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("a")[1]);
        assertTrue(rows().containsKey("b"));
        synchronized (console) {
            assertTrue(console.stream().anyMatch(line -> line.startsWith("ERROR Lost database 'test'")), console::toString);
            assertTrue(console.stream().anyMatch(line -> line.contains("is back: wrote the 2 changes")), console::toString);
        }
    }

    @Test
    void aFullDiskHoldsWritesBackUntilTheresRoom() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("small", text("1"));
            storage.save();
            // A page limit stands in for a full disk: SQLite fails the same way (SQLITE_FULL).
            Properties full = new Properties();
            full.setProperty("max_page_count", "3");
            SqlVariableStorage.extraProperties = full;
            try (Connection c = open(); Statement s = c.createStatement()) {
                s.execute("PRAGMA page_count");
            }
            store.set("a", text("x"));
            // Force a reconnect with the limit: lock the file briefly.
            try (Connection locker = open()) {
                lock(locker);
                waitFor(() -> storage.status().state() == SqlVariableStorage.State.DOWN);
                unlock(locker);
            }
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                big.append("filler ");
            }
            for (int i = 0; i < 20; i++) {
                store.set("big::" + i, text(big.toString()));
            }
            waitFor(() -> storage.status().problem() != null && storage.status().problem().contains("full"));
            assertEquals(SqlVariableStorage.State.DOWN, storage.status().state());
            SqlVariableStorage.extraProperties = null;
            waitFor(() -> storage.status().state() == SqlVariableStorage.State.CONNECTED
                    && storage.status().waiting() == 0);
        }
        assertEquals(22, rows().size());
    }

    @Test
    void aDatabaseUnreachableAtStartLoadsWhenItAnswersAndItsValuesWin() throws Exception {
        VariableStore before = new VariableStore();
        try (SqlVariableStorage storage = storage(before)) {
            storage.load();
            before.set("bal::alice", text("5000"));
            storage.save();
        }
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            lock(locker);
            try (SqlVariableStorage storage = storage(store)) {
                assertEquals(0, storage.load().variables());
                assertFalse(storage.loaded());
                synchronized (console) {
                    assertTrue(String.join("\n", console).contains("Couldn't reach database 'test'"), console::toString);
                }
                // Scripts run meanwhile: a first-join bonus for a name the database has, and a new name.
                store.set("bal::alice", text("100"));
                store.set("bal::bob", text("100"));
                assertThrows(IOException.class, storage::save);
                assertArrayEquals("5000".getBytes(StandardCharsets.UTF_8), rowsWhileLocked(locker).get("bal::alice"));
                unlock(locker);
                waitFor(storage::loaded);
                waitFor(() -> storage.status().waiting() == 0 && storage.written());
            }
        }
        assertEquals(text("5000"), store.get("bal::alice"));
        assertArrayEquals("5000".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("bal::alice")[1]);
        assertArrayEquals("100".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("bal::bob")[1]);
    }

    private static Map<String, byte[]> rowsWhileLocked(Connection c) throws SQLException {
        Map<String, byte[]> rows = new LinkedHashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT name, value FROM variables21")) {
            while (r.next()) {
                rows.put(r.getString(1), r.getBytes(2));
            }
        }
        return rows;
    }

    @Test
    void changesThatCouldntBeWrittenBeforeStoppingAreWrittenAtTheNextStart() throws Exception {
        VariableStore store = new VariableStore();
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        try (Connection locker = open()) {
            try (SqlVariableStorage storage = storage(store)) {
                storage.load();
                store.set("a", text("1"));
                store.set("b", text("2"));
                storage.save();
                lock(locker);
                store.set("a", text("changed"));
                store.set("b", null);
            }
            unlock(locker);
        }
        Path pending = dir.resolve("pending").resolve("test.pending.csv");
        assertTrue(Files.exists(pending));
        assertArrayEquals("1".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("a")[1]);

        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next)) {
            storage.load();
            assertEquals(text("changed"), next.get("a"));
            assertNull(next.get("b"));
            waitFor(() -> !Files.exists(pending));
        }
        assertArrayEquals("changed".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("a")[1]);
        assertFalse(rows().containsKey("b"));
    }

    @Test
    void variablesSetWhileItNeverLoadedAreKeptButTheDatabasesOwnValuesWin() throws Exception {
        VariableStore first = new VariableStore();
        try (SqlVariableStorage storage = storage(first)) {
            storage.load();
            first.set("bal::alice", text("5000"));
            storage.save();
        }
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            lock(locker);
            try (SqlVariableStorage storage = storage(store)) {
                storage.load();
                store.set("bal::alice", text("100"));
                store.set("bal::bob", text("100"));
            }
            unlock(locker);
        }
        assertTrue(Files.exists(dir.resolve("pending").resolve("test.unloaded.csv")));
        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next)) {
            storage.load();
            waitFor(() -> storage.written());
        }
        assertEquals(text("5000"), next.get("bal::alice"));
        assertEquals(text("100"), next.get("bal::bob"));
        assertTrue(rows().containsKey("bal::bob"));
        assertFalse(Files.exists(dir.resolve("pending").resolve("test.unloaded.csv")));
    }

    @Test
    void aRowTheDatabaseRefusesDoesntHoldUpTheRest() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            try (Connection c = open(); Statement s = c.createStatement()) {
                // A trigger standing in for a value the database refuses.
                s.executeUpdate("CREATE TRIGGER refuse BEFORE INSERT ON variables21 WHEN NEW.name = 'bad' "
                        + "BEGIN SELECT RAISE(ABORT, 'refused'); END");
            }
            store.set("good", text("1"));
            store.set("bad", text("2"));
            store.set("also", text("3"));
            storage.save();
            assertEquals(SqlVariableStorage.State.CONNECTED, storage.status().state());
        }
        assertEquals(List.of("good", "also"), List.copyOf(rows().keySet()).stream().sorted((a, b) -> a.equals("good") ? -1 : 1).toList());
        synchronized (console) {
            assertTrue(console.stream().anyMatch(line -> line.contains("Couldn't save {bad}")), console::toString);
        }
    }

    @Test
    void monitorChangesSeesAnotherServersWritesAndDeletions() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            one.set("shared", text("from a"));
            a.save();
            waitFor(() -> text("from a").equals(two.get("shared")));
            two.set("shared", text("from b"));
            two.set("other", text("b"));
            b.save();
            waitFor(() -> text("from b").equals(one.get("shared")) && one.get("other") != null);
            one.set("other", null);
            a.save();
            waitFor(() -> two.get("other") == null);
            // Nothing echoes back: b's next write has nothing to do.
            assertEquals(text("from b"), two.get("shared"));
        }
    }

    @Test
    void thePasswordNeverShows() {
        SqlVariableStorage.Settings settings = SqlVariableStorage.Settings.mysql("db", "localhost", 3306, "skript",
                "root", "hunter2", "variables21", false, 20_000);
        assertFalse(settings.toString().contains("hunter2"));
        assertFalse(settings.where().contains("hunter2"));
    }

    @Test
    void refusesATableNameThatIsntOne() {
        assertThrows(IllegalArgumentException.class, () -> SqlVariableStorage.Settings.sqlite("x", db(),
                "variables21; DROP TABLE x", false, 1));
    }

    @Test
    void tellsBadRowsFromAnOutage() {
        assertTrue(SqlVariableStorage.isBadRow(new SQLException("too long", "22001")));
        assertFalse(SqlVariableStorage.isBadRow(new SQLException("gone", "08S01")));
        assertFalse(SqlVariableStorage.isBadRow(new SQLException("locked", null, 5)));
    }

    @Test
    void writesNeverRunOnTheCallersThreadUnlessAsked() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("a", text("1"));
            // No save(): the database's own thread writes it.
            waitFor(() -> {
                try {
                    return rows().containsKey("a");
                } catch (SQLException e) {
                    return false;
                }
            });
        }
    }

    @Test
    void anUnusedPreparedStatementIsntNeeded() throws Exception {
        // Skript's REPLACE without the rowid works on the table NeoSkript made, so Skript can write to it.
        try (SqlVariableStorage storage = storage(new VariableStore())) {
            storage.load();
        }
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "REPLACE INTO variables21 (name, type, value, update_guid) VALUES (?, ?, ?, ?)")) {
            p.setString(1, "x");
            p.setString(2, "string");
            p.setBytes(3, new byte[] {1});
            p.setString(4, "skript");
            assertEquals(1, p.executeUpdate());
        }
    }
}
