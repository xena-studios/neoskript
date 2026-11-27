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
        SqlVariableStorage.startupTimeoutMillis = 10_000;
        SqlVariableStorage.pendingEveryMillis = 30_000;
    }

    private Path db() {
        return dir.resolve("variables.db");
    }

    private SqlVariableStorage storage(VariableStore store) {
        return storage(store, false);
    }

    private SqlVariableStorage storage(VariableStore store, boolean monitor) {
        return storage(store, monitor, "variables21");
    }

    /** Codecs with Skript's {@code long}, as NeoSkript registers it, so numbers come back as numbers. */
    private static VariableCodecs codecs() {
        VariableCodecs codecs = new VariableCodecs();
        codecs.register("long", Long.class, new co.xenastudios.neoskript.api.storage.VariableCodec<>() {
            @Override
            public void encode(Long value, co.xenastudios.neoskript.api.storage.BinaryWriter out) {
                out.rawLong(value);
            }

            @Override
            public Long decode(co.xenastudios.neoskript.api.storage.BinaryReader in) {
                return in.rawLong();
            }
        });
        return codecs;
    }

    private SqlVariableStorage storage(VariableStore store, boolean monitor, String table) {
        return new SqlVariableStorage(store, codecs(),
                SqlVariableStorage.Settings.sqlite("test", db(), table, monitor, 100), Routing.ALL,
                dir.resolve("pending"), log);
    }

    private Path pendingFile() {
        return storage(new VariableStore()).pendingFile();
    }

    private Path unloadedFile() {
        return storage(new VariableStore()).unloadedFile();
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
        Path pending = pendingFile();
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
        assertTrue(Files.exists(unloadedFile()));
        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next)) {
            storage.load();
            waitFor(() -> storage.written());
        }
        assertEquals(text("5000"), next.get("bal::alice"));
        assertEquals(text("100"), next.get("bal::bob"));
        assertTrue(rows().containsKey("bal::bob"));
        assertFalse(Files.exists(unloadedFile()));
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

    @Test
    void aLocalChangeNotYetWrittenIsntOverwrittenByAnotherServersOlderRow() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            one.set("x", text("first"));
            a.save();
            waitFor(() -> text("first").equals(two.get("x")));
            // b changes x, but its write is held back, as a slow write would be; meanwhile a writes an older idea of x.
            b.writesHeld = true;
            two.set("x", text("b local"));
            one.set("x", text("a remote"));
            one.set("y", text("unrelated"));
            a.save();
            // b reads a's rows: y arrives, x keeps b's own change.
            waitFor(() -> two.get("y") != null);
            Thread.sleep(400);
            assertEquals(text("b local"), two.get("x"));
            b.writesHeld = false;
            waitFor(() -> text("b local").equals(one.get("x")));
            assertEquals(text("b local"), two.get("x"));
        }
    }

    @Test
    void startupDoesntWaitLongerThanItsBoundAndTheDatabaseLoadsLater() throws Exception {
        VariableStore first = new VariableStore();
        try (SqlVariableStorage storage = storage(first)) {
            storage.load();
            first.set("a", text("1"));
            storage.save();
        }
        SqlVariableStorage.startupTimeoutMillis = 500;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            lock(locker);
            try (SqlVariableStorage storage = storage(store)) {
                long start = System.nanoTime();
                assertEquals(0, storage.load().variables());
                assertTrue(System.nanoTime() - start < 3_000_000_000L, "startup waited past its bound");
                assertFalse(storage.loaded());
                unlock(locker);
                waitFor(storage::loaded);
            }
        }
        assertEquals(text("1"), store.get("a"));
    }

    @Test
    void whatANeverReachedDatabaseWouldSaveIsKeptWhileTheServerRuns() throws Exception {
        SqlVariableStorage.pendingEveryMillis = 50;
        SqlVariableStorage.startupTimeoutMillis = 500;
        Properties quick = new Properties();
        quick.setProperty("busy_timeout", "100");
        SqlVariableStorage.extraProperties = quick;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            lock(locker);
            SqlVariableStorage storage = storage(store);
            storage.load();
            store.set("crash::safe", text("1"));
            // No clean shutdown: the file is there while the server still runs, in case it crashes.
            waitFor(() -> Files.exists(storage.unloadedFile()));
            assertTrue(VariableFile.read(storage.unloadedFile()).containsKey("crash::safe"));
            unlock(locker);
            storage.close();
        }
    }

    @Test
    void aPendingFileKeptForAnotherTableIsSetAsideNotApplied() throws Exception {
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        VariableStore store = new VariableStore();
        Path pending;
        try (Connection locker = open()) {
            try (SqlVariableStorage storage = storage(store, false, "first")) {
                storage.load();
                store.set("a", text("1"));
                storage.save();
                lock(locker);
                store.set("a", text("2"));
                pending = storage.pendingFile();
            }
            unlock(locker);
        }
        assertTrue(Files.exists(pending));
        // The same name for another table: the file names differ, and a file moved over is refused by its identity.
        Path other = storage(new VariableStore(), false, "second").pendingFile();
        assertFalse(other.equals(pending));
        Files.move(pending, other);
        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next, false, "second")) {
            storage.load();
            assertNull(next.get("a"));
        }
        assertFalse(Files.exists(other));
        try (var files = Files.list(other.getParent())) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().contains(".other-database-")));
        }
    }

    @Test
    void aKeptChangeDoesntOverrideARowAnotherServerWroteSince() throws Exception {
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            try (SqlVariableStorage storage = storage(store)) {
                storage.load();
                store.set("x", text("1"));
                store.set("z", text("1"));
                storage.save();
                lock(locker);
                store.set("x", text("2 kept"));
                store.set("z", text("2 kept"));
            }
            unlock(locker);
        }
        // Another server writes x after this one went away.
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("REPLACE INTO variables21 (rowid, name, "
                + "type, value, update_guid) VALUES ((SELECT MAX(rowid) + 1 FROM variables21), 'x', 'string', ?, 'other')")) {
            p.setBytes(1, "3 newer".getBytes(StandardCharsets.UTF_8));
            p.executeUpdate();
        }
        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next)) {
            storage.load();
            waitFor(storage::written);
        }
        assertEquals(text("3 newer"), next.get("x"));
        assertEquals(text("2 kept"), next.get("z"));
    }

    @Test
    void aLateLoadNeverAppliesThisRunsOwnSnapshotOverNewerScriptWrites() throws Exception {
        SqlVariableStorage.pendingEveryMillis = 50;
        SqlVariableStorage.startupTimeoutMillis = 500;
        Properties quick = new Properties();
        quick.setProperty("busy_timeout", "100");
        SqlVariableStorage.extraProperties = quick;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            lock(locker);
            try (SqlVariableStorage storage = storage(store)) {
                storage.load();
                store.set("x", text("1"));
                store.set("y", text("1"));
                waitFor(() -> {
                    try {
                        return Files.exists(storage.unloadedFile())
                                && VariableFile.read(storage.unloadedFile()).containsKey("y");
                    } catch (IOException e) {
                        return false;
                    }
                });
                // Changed after the snapshot.
                store.set("x", text("2 newer"));
                store.set("y", null);
                unlock(locker);
                waitFor(() -> storage.loaded() && storage.written() && storage.status().waiting() == 0);
                assertEquals(text("2 newer"), store.get("x"));
                assertNull(store.get("y"));
            }
        }
        assertArrayEquals("2 newer".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("x")[1]);
        assertFalse(rows().containsKey("y"));
        assertFalse(Files.exists(unloadedFile()));
    }

    @Test
    void whatAnEarlierStartKeptWhileUnreachedIsInMemoryAndADeletionThisRunSticks() throws Exception {
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        SqlVariableStorage.startupTimeoutMillis = 500;
        Properties quick = new Properties();
        quick.setProperty("busy_timeout", "100");
        SqlVariableStorage.extraProperties = quick;
        try (Connection locker = open()) {
            lock(locker);
            VariableStore first = new VariableStore();
            try (SqlVariableStorage storage = storage(first)) {
                storage.load();
                first.set("kept", text("1"));
                first.set("gone", text("1"));
            }
            assertTrue(Files.exists(unloadedFile()));
            VariableStore second = new VariableStore();
            try (SqlVariableStorage storage = storage(second)) {
                storage.load();
                // The earlier start's values are there for scripts straight away.
                assertEquals(text("1"), second.get("kept"));
                second.set("gone", null);
                unlock(locker);
                waitFor(() -> storage.loaded() && storage.written() && storage.status().waiting() == 0);
                assertNull(second.get("gone"));
            }
        }
        assertTrue(rows().containsKey("kept"));
        assertFalse(rows().containsKey("gone"));
    }

    @Test
    void aKeptChangeIsntAppliedOverARowRewrittenAtAReusedRowid() throws Exception {
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        VariableStore store = new VariableStore();
        try (Connection locker = open()) {
            try (SqlVariableStorage storage = storage(store)) {
                storage.load();
                store.set("a", text("1"));
                storage.save();
                store.set("x", text("1"));
                storage.save();
                store.set("m", text("1"));
                storage.save();
                lock(locker);
                store.set("x", text("2 kept"));
            }
            unlock(locker);
        }
        // Another writer deletes the top row and writes x, as SQLite does without an explicit rowid: x takes
        // rowid 3 again, no higher than anything this server knew.
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM variables21 WHERE name = 'x'");
            s.executeUpdate("DELETE FROM variables21 WHERE name = 'm'");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO variables21 (name, type, value, update_guid) "
                    + "VALUES ('x', 'string', ?, 'other')")) {
                p.setBytes(1, "3 newer".getBytes(StandardCharsets.UTF_8));
                p.executeUpdate();
            }
        }
        VariableStore next = new VariableStore();
        try (SqlVariableStorage storage = storage(next)) {
            storage.load();
            waitFor(storage::written);
        }
        assertEquals(text("3 newer"), next.get("x"));
        assertArrayEquals("3 newer".getBytes(StandardCharsets.UTF_8), (byte[]) rows().get("x")[1]);
    }

    private static Properties quickLocks() {
        Properties quick = new Properties();
        quick.setProperty("busy_timeout", "100");
        return quick;
    }

    @Test
    void aVariableDeletedAfterALateLoadWhoseFirstWriteFailedStaysDeleted() throws Exception {
        SqlVariableStorage.pendingEveryMillis = 50;
        SqlVariableStorage.startupTimeoutMillis = 500;
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        SqlVariableStorage.extraProperties = quickLocks();
        try (Connection locker = open()) {
            lock(locker);
            VariableStore first = new VariableStore();
            try (SqlVariableStorage storage = storage(first)) {
                storage.load();
                first.set("kept", text("1"));
                first.set("gone", text("1"));
            }
            assertTrue(Files.exists(unloadedFile()));
            VariableStore second = new VariableStore();
            try (SqlVariableStorage storage = storage(second)) {
                storage.load();
                // The database answers, and loads, but its first write doesn't go through.
                storage.writesHeld = true;
                unlock(locker);
                waitFor(storage::loaded);
                assertFalse(Files.exists(storage.unloadedFile()), "applied, so not kept to apply again");
                second.set("gone", null);
                lock(locker);
            }
            unlock(locker);
        }
        VariableStore third = new VariableStore();
        try (SqlVariableStorage storage = storage(third)) {
            storage.load();
            waitFor(storage::written);
        }
        assertEquals(text("1"), third.get("kept"));
        assertNull(third.get("gone"));
        assertFalse(rows().containsKey("gone"));
    }

    @Test
    void deletingEveryKeptVariableWhileStillUnreachedKeepsThemDeleted() throws Exception {
        SqlVariableStorage.pendingEveryMillis = 50;
        SqlVariableStorage.startupTimeoutMillis = 500;
        SqlVariableStorage.closeTimeoutMillis = 1_000;
        SqlVariableStorage.extraProperties = quickLocks();
        try (Connection locker = open()) {
            lock(locker);
            VariableStore first = new VariableStore();
            try (SqlVariableStorage storage = storage(first)) {
                storage.load();
                first.set("a", text("1"));
            }
            assertTrue(Files.exists(unloadedFile()));
            VariableStore second = new VariableStore();
            try (SqlVariableStorage storage = storage(second)) {
                storage.load();
                assertEquals(text("1"), second.get("a"));
                second.set("a", null);
            }
            assertFalse(Files.exists(unloadedFile()), "nothing left to keep");
            unlock(locker);
        }
        VariableStore third = new VariableStore();
        try (SqlVariableStorage storage = storage(third)) {
            storage.load();
            waitFor(storage::written);
        }
        assertNull(third.get("a"));
    }

    @Test
    void aSqliteBackupIsOnlyEverWholeAndWhatACrashLeftGoesAtLoad() throws Exception {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path leftover = backups.resolve("variables_2026-11-17_01-02-03_scheduled.db.copying.tmp");
        Path notOurs = backups.resolve("other_2026-11-17_01-02-03_scheduled.db.copying.tmp");
        Files.writeString(leftover, "part of a backup");
        Files.writeString(notOurs, "another file's");
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            assertFalse(Files.exists(leftover), "removed by its exact name");
            assertTrue(Files.exists(notOurs));
            store.set("kept", text("yes"));
            storage.save();
            Path backup = storage.backupNow();
            assertTrue(backup.getFileName().toString().endsWith("_scheduled.db"), backup.toString());
            assertFalse(Files.exists(Durable.temporary(backup)));
            try (Connection copy = DriverManager.getConnection("jdbc:sqlite:" + backup);
                    ResultSet rows = copy.createStatement().executeQuery("SELECT COUNT(*) FROM variables21")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            storage.cancelBackup();
            assertNull(storage.backupNow(), "none once the stop has begun");
        }
    }

    @Test
    void theStopCutsOffASqliteBackupRatherThanWaitingForIt() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.set("first", text("1"));
            storage.save();
            // A big file, so VACUUM INTO takes a while.
            try (Connection direct = DriverManager.getConnection("jdbc:sqlite:" + db())) {
                direct.setAutoCommit(false);
                try (Statement create = direct.createStatement()) {
                    create.execute("CREATE TABLE filler (data BLOB)");
                }
                try (PreparedStatement insert = direct.prepareStatement("INSERT INTO filler VALUES (?)")) {
                    byte[] data = new byte[4096];
                    for (int i = 0; i < 40_000; i++) {
                        data[0] = (byte) i;
                        insert.setBytes(1, data);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                direct.commit();
            }
            java.util.concurrent.ExecutorService other = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<Path> backup = other.submit(storage::backupNow);
                long until = System.nanoTime() + 10_000_000_000L;
                while (storage.backupStatement == null && !backup.isDone() && System.nanoTime() < until) {
                    Thread.onSpinWait();
                }
                org.junit.jupiter.api.Assumptions.assumeTrue(storage.backupStatement != null,
                        "the backup finished before it could be cut off");
                storage.cancelBackup();
                java.util.concurrent.ExecutionException failed = assertThrows(
                        java.util.concurrent.ExecutionException.class, () -> backup.get(10, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(failed.getCause().getMessage().contains("cut off"), failed.getCause().getMessage());
            } finally {
                other.shutdownNow();
            }
            try (var files = Files.list(dir.resolve("backups"))) {
                assertEquals(List.of(), files.toList(), "neither a backup nor part of one");
            }
            // The last write isn't held up, and still lands.
            store.set("last", text("2"));
            storage.save();
        }
        VariableStore back = new VariableStore();
        try (SqlVariableStorage again = storage(back)) {
            again.load();
            assertEquals(text("2"), back.get("last"));
        }
    }

    private SqlVariableStorage watching(VariableStore store, long monitorMillis) {
        return new SqlVariableStorage(store, codecs(),
                SqlVariableStorage.Settings.sqlite("test", db(), "variables21", true, monitorMillis), Routing.ALL,
                dir.resolve("pending"), log);
    }

    /** Two servers whose writes are held, so each changes {@code x} before seeing the other's change. */
    private void bothChange(SqlVariableStorage a, SqlVariableStorage b, Runnable onA, Runnable onB)
            throws Exception {
        java.util.concurrent.atomic.AtomicInteger aWrites = new java.util.concurrent.atomic.AtomicInteger();
        Runnable stop = a.whenWritten(aWrites::incrementAndGet);
        a.writesHeld = true;
        b.writesHeld = true;
        onA.run();
        onB.run();
        a.writesHeld = false;
        waitFor(() -> aWrites.get() > 0);
        stop.run();
        b.writesHeld = false;
    }

    @Test
    void twoServersAddingToOneNumberAtOnceBothCount() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            one.set("coins", 10L);
            a.save();
            waitFor(() -> Long.valueOf(10).equals(two.get("coins")));
            bothChange(a, b, () -> one.add("coins", new Object[] {1L}),
                    () -> two.subtract("coins", new Object[] {3L}));
            // a wrote 11; b, from 10, took 3 away: 8, not b's 7 or a's 11.
            waitFor(() -> Long.valueOf(8).equals(one.get("coins")) && Long.valueOf(8).equals(two.get("coins")));
            assertEquals(1, b.conflicts()[1]);
            // And a new number both add to at once: the two additions add up.
            bothChange(a, b, () -> one.add("fresh", new Object[] {2L}), () -> two.add("fresh", new Object[] {5L}));
            waitFor(() -> Long.valueOf(7).equals(one.get("fresh")) && Long.valueOf(7).equals(two.get("fresh")));
        }
    }

    @Test
    void additionsMadeWhileAMergedWriteRunsStayAdded() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            one.set("n", 0L);
            a.save();
            waitFor(() -> Long.valueOf(0).equals(two.get("n")));
            // Many additions on both servers while both write: none is lost.
            Thread adder = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    one.add("n", new Object[] {1L});
                    two.add("n", new Object[] {1L});
                    if (i % 20 == 0) {
                        try {
                            Thread.sleep(15);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            });
            adder.start();
            adder.join();
            waitFor(() -> Long.valueOf(400).equals(one.get("n")) && Long.valueOf(400).equals(two.get("n")));
        }
    }

    @Test
    void settingAVariableOnTwoServersAtOnceKeepsTheLastWriteEverywhere() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            one.set("coins", 10L);
            a.save();
            waitFor(() -> Long.valueOf(10).equals(two.get("coins")));
            // A set isn't an addition, even of numbers: the last write wins, as in Skript.
            bothChange(a, b, () -> one.add("coins", new Object[] {1L}), () -> two.set("coins", 50L));
            waitFor(() -> Long.valueOf(50).equals(one.get("coins")) && Long.valueOf(50).equals(two.get("coins")));
            bothChange(a, b, () -> one.set("motd", text("a")), () -> two.set("motd", text("b")));
            waitFor(() -> text("b").equals(one.get("motd")) && text("b").equals(two.get("motd")));
        }
    }

    @Test
    void valuesAddedToAListAtTheSameIndexOnTwoServersAreBothKept() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            bothChange(a, b, () -> one.addToList("queue", new Object[] {text("alice")}),
                    () -> two.addToList("queue", new Object[] {text("bob"), text("carol")}));
            // Both took index 1 (and b index 2 too): a's stays at 1, b's move up.
            waitFor(() -> one.list("queue").length == 3 && two.list("queue").length == 3);
            waitFor(() -> java.util.Arrays.equals(one.list("queue"), two.list("queue")));
            assertEquals(text("alice"), one.get("queue::1"));
            List<Object> all = List.of(one.list("queue"));
            assertTrue(all.contains(text("bob")) && all.contains(text("carol")), all.toString());
        }
    }

    @Test
    void anotherServersWriteIsReadAtOnceWhenItSaysSo() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        // A minute between checks: only being told reads it sooner.
        try (SqlVariableStorage a = watching(one, 60_000); SqlVariableStorage b = watching(two, 60_000)) {
            a.load();
            b.load();
            Runnable stop = a.whenWritten(b::checkForChanges);
            one.set("news", text("hello"));
            waitFor(() -> text("hello").equals(two.get("news")));
            stop.run();
            assertEquals(a.id(), b.id());
            // Many calls at once are one check; one after the database closed does nothing.
            for (int i = 0; i < 100; i++) {
                b.checkForChanges();
            }
        }
        VariableStore three = new VariableStore();
        SqlVariableStorage closed = watching(three, 60_000);
        closed.load();
        closed.close();
        closed.checkForChanges();
    }

    @Test
    void withoutMonitorChangesNothingWatchesTheStore() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store)) {
            storage.load();
            store.add("n", new Object[] {1L});
            storage.save();
            assertEquals(0, storage.conflicts()[0]);
        }
    }

    /** Two servers watching one file, loaded, with {@code name} at {@code start} on both. */
    private SqlVariableStorage[] pair(VariableStore one, VariableStore two, String name, Object start)
            throws Exception {
        SqlVariableStorage a = storage(one, true);
        SqlVariableStorage b = storage(two, true);
        a.load();
        b.load();
        one.set(name, start);
        a.save();
        waitFor(() -> start.equals(two.get(name)));
        return new SqlVariableStorage[] {a, b};
    }

    /** The number the table holds for {@code name}, as Skript's long. */
    private Long saved(String name) throws SQLException {
        Object[] row = rows().get(name);
        if (row == null || row[1] == null) {
            return null;
        }
        return java.nio.ByteBuffer.wrap((byte[]) row[1]).getLong();
    }

    @Test
    void aSetMadeWhileAMergedWriteCommitsIsntShiftedAndIsTheLastWrite() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            // b's merged write (11 + 2) commits; before memory catches up, a script sets coins to 0.
            java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
            b.afterCommit = () -> {
                if (once.compareAndSet(false, true)) {
                    two.set("coins", 0L);
                }
            };
            bothChange(a, b, () -> one.add("coins", new Object[] {1L}), () -> two.add("coins", new Object[] {2L}));
            // The set is newer than the merge: it stays as set, and is written over it.
            waitFor(() -> Long.valueOf(0).equals(one.get("coins")) && Long.valueOf(0).equals(two.get("coins")));
            Thread.sleep(300);
            assertEquals(0L, two.get("coins"));
            assertEquals(0L, saved("coins"));
        }
    }

    @Test
    void aMergedWriteInABatchWithARefusedRowCountsOnce() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            try (Connection c = open(); Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TRIGGER refuse BEFORE INSERT ON variables21 WHEN NEW.name = 'bad' "
                        + "BEGIN SELECT RAISE(ABORT, 'refused'); END");
            }
            bothChange(a, b, () -> one.add("coins", new Object[] {1L}), () -> {
                two.add("coins", new Object[] {2L});
                two.set("bad", text("x"));
            });
            waitFor(() -> Long.valueOf(13).equals(one.get("coins")) && Long.valueOf(13).equals(two.get("coins")));
            Thread.sleep(300);
            assertEquals(13L, two.get("coins"));
            assertEquals(13L, saved("coins"));
            assertEquals(1, b.conflicts()[1]);
        }
    }

    @Test
    void aWriteWhoseAnswerWasLostIsntCountedTwice() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            // a's write commits, but the answer is lost: a reconnects and finds its own write in the row.
            a.loseNextReply = true;
            one.add("coins", new Object[] {50L});
            one.addToList("queue", new Object[] {text("alice")});
            waitFor(() -> !a.loseNextReply && a.status().state() == SqlVariableStorage.State.CONNECTED
                    && Long.valueOf(60).equals(two.get("coins")));
            Thread.sleep(500);
            assertEquals(60L, one.get("coins"));
            assertEquals(60L, saved("coins"));
            assertEquals(1, one.list("queue").length);
            assertEquals(1, two.list("queue").length);
            // A merged write whose answer is lost: the merge counts once too.
            a.loseNextReply = true;
            bothChange(b, a, () -> two.add("coins", new Object[] {5L}), () -> one.add("coins", new Object[] {7L}));
            waitFor(() -> Long.valueOf(72).equals(one.get("coins")) && Long.valueOf(72).equals(two.get("coins")));
            Thread.sleep(500);
            assertEquals(72L, one.get("coins"));
            assertEquals(72L, saved("coins"));
        }
    }

    @Test
    void anAdditionMadeAsAnotherServersValueArrivesIsntLost() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 0L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
            b.beforeApplying = name -> {
                if (name.equals("coins") && once.compareAndSet(false, true)) {
                    two.add("coins", new Object[] {1L});
                }
            };
            one.set("coins", 5L);
            a.save();
            // b's addition, made just as a's 5 arrives, is merged with it rather than overwritten.
            waitFor(() -> Long.valueOf(6).equals(one.get("coins")) && Long.valueOf(6).equals(two.get("coins")));
        }
    }

    @Test
    void aSetToTheOldValueThenAnAdditionIsntMergedAsAnAddition() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "kills", 0L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            bothChange(a, b, () -> one.add("kills", new Object[] {7L}), () -> {
                two.set("kills", 0L);
                two.add("kills", new Object[] {1L});
            });
            // b set it, so b's write is the last write: 1, not a's 7 plus 1.
            waitFor(() -> Long.valueOf(1).equals(one.get("kills")) && Long.valueOf(1).equals(two.get("kills")));
            assertEquals(0, b.conflicts()[1]);
        }
    }

    @Test
    void aWholeListSetIsntMergedAsAnAppend() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        try (SqlVariableStorage a = storage(one, true); SqlVariableStorage b = storage(two, true)) {
            a.load();
            b.load();
            bothChange(a, b, () -> one.addToList("queue", new Object[] {text("alice")}),
                    () -> two.setList("queue", new Object[] {text("bob")}));
            // b set the list: the last write, not an append moved along.
            waitFor(() -> text("bob").equals(one.get("queue::1")) && text("bob").equals(two.get("queue::1")));
            Thread.sleep(300);
            assertEquals(1, one.list("queue").length);
        }
    }

    @Test
    void withMergeChangesOffTheLastWriteWinsAsInSkript() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage.Settings plain = SqlVariableStorage.Settings.sqlite("test", db(), "variables21", true, 100)
                .withMerge(false);
        try (SqlVariableStorage a = new SqlVariableStorage(one, codecs(), plain, Routing.ALL, dir.resolve("p1"), log);
                SqlVariableStorage b = new SqlVariableStorage(two, codecs(), plain, Routing.ALL, dir.resolve("p2"),
                        log)) {
            a.load();
            b.load();
            one.set("coins", 10L);
            a.save();
            waitFor(() -> Long.valueOf(10).equals(two.get("coins")));
            bothChange(a, b, () -> one.add("coins", new Object[] {1L}), () -> two.add("coins", new Object[] {2L}));
            waitFor(() -> Long.valueOf(12).equals(one.get("coins")) && Long.valueOf(12).equals(two.get("coins")));
            assertEquals(0, b.conflicts()[0]);
        }
    }

    @Test
    void aLostAnswerThenAnotherServersMergeIsCountedOnce() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            // a adds 50 and commits 60, but the answer is lost; before a is back, b adds 3 and merges to 63 under
            // its own update_guid. a must find its 60 committed, not merge 63 + 50.
            a.loseNextReply = true;
            one.add("coins", new Object[] {50L});
            waitFor(() -> !a.loseNextReply);
            waitFor(() -> {
                try {
                    return Long.valueOf(60).equals(saved("coins"));
                } catch (SQLException e) {
                    return false;
                }
            });
            two.add("coins", new Object[] {3L});
            b.save();
            assertEquals(63L, saved("coins"));
            waitFor(() -> a.status().state() == SqlVariableStorage.State.CONNECTED);
            waitFor(() -> Long.valueOf(63).equals(one.get("coins")) && Long.valueOf(63).equals(two.get("coins")));
            Thread.sleep(500);
            assertEquals(63L, one.get("coins"));
            assertEquals(63L, saved("coins"));
            // The same with a set: a's lost one doesn't overwrite b's later change.
            a.loseNextReply = true;
            one.set("motd", text("from a"));
            waitFor(() -> !a.loseNextReply);
            waitFor(() -> {
                try {
                    return rows().containsKey("motd");
                } catch (SQLException e) {
                    return false;
                }
            });
            two.set("motd", text("from b"));
            b.save();
            waitFor(() -> a.status().state() == SqlVariableStorage.State.CONNECTED);
            waitFor(() -> text("from b").equals(one.get("motd")));
            Thread.sleep(500);
            assertEquals(text("from b"), one.get("motd"));
            assertEquals(text("from b"), two.get("motd"));
        }
        // The marker rows are no variables: a fresh load doesn't see one.
        VariableStore three = new VariableStore();
        try (SqlVariableStorage c = storage(three, true)) {
            c.load();
            List<String> names = new ArrayList<>();
            three.forEach((name, value) -> names.add(name));
            assertTrue(names.stream().noneMatch(name -> name.startsWith("-")), names.toString());
            assertEquals(List.of("coins", "motd"), names.stream().sorted().toList());
        }
        assertTrue(rows().keySet().stream().noneMatch(name -> name.startsWith(SqlVariableStorage.MARKER)),
                "each server's marker goes when it stops");
    }

    @Test
    void aChangeMadeWhileALostAnswerIsSettledIsWrittenAndCountsAsChanged() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            a.loseNextReply = true;
            one.add("coins", new Object[] {50L});
            waitFor(() -> !a.loseNextReply);
            // While a is down, add 5 more: memory 65, the row 60.
            one.add("coins", new Object[] {5L});
            waitFor(() -> {
                try {
                    return Long.valueOf(65).equals(saved("coins"));
                } catch (SQLException e) {
                    return false;
                }
            });
            waitFor(() -> !a.changed());
            assertEquals(65L, one.get("coins"));
            waitFor(() -> Long.valueOf(65).equals(two.get("coins")));
        }
    }

    @Test
    void aVariableLeftForTheNextRoundKeepsTheStoreChanged() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store, true)) {
            storage.load();
            store.set("x", 1L);
            storage.underWay("x", true);
            storage.save();
            // x was under way: not written, and still changed, so it's written once it's done.
            assertTrue(storage.changed());
            assertFalse(rows().containsKey("x"));
            storage.underWay("x", false);
            waitFor(() -> {
                try {
                    return rows().containsKey("x");
                } catch (SQLException e) {
                    return false;
                }
            });
            waitFor(() -> !storage.changed());
        }
    }

    @Test
    void aListBeingSetIsntDeletedFromTheDatabaseMeanwhile() throws Exception {
        VariableStore store = new VariableStore();
        try (SqlVariableStorage storage = storage(store, true)) {
            storage.load();
            store.setList("top", new Object[] {text("a"), text("b")});
            storage.save();
            assertTrue(rows().containsKey("top::1") && rows().containsKey("top::2"));
            // A set of the list, caught after its old values went and before the new ones are in.
            storage.underWay("top::*", true);
            store.deleteList("top");
            storage.save();
            assertTrue(rows().containsKey("top::1") && rows().get("top::1")[1] != null, "not deleted meanwhile");
            assertTrue(storage.changed());
            store.setList("top", new Object[] {text("c")});
            storage.underWay("top::*", false);
            waitFor(() -> {
                try {
                    Map<String, Object[]> rows = rows();
                    return rows.get("top::2") != null && rows.get("top::2")[1] == null
                            && rows.get("top::1") != null && rows.get("top::1")[1] != null;
                } catch (SQLException e) {
                    return false;
                }
            });
        }
    }

    /**
     * a's commit is slow rather than lost: left open on its own connection while a carries on as if its answer timed
     * out. It's committed (or rolled back) from the test while a settles it; then, before a's next round, b adds 3 on
     * top under its own update_guid. a must count its own 50 once either way: 63, not 113.
     */
    private void stalledCommit(boolean commits) throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            one.addToList("queue", new Object[] {text("x")});
            a.save();
            waitFor(() -> two.list("queue").length == 1);
            java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch bWrote = new java.util.concurrent.CountDownLatch(1);
            a.afterSettle = () -> {
                a.afterSettle = null;
                settled.countDown();
                try {
                    bWrote.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            java.util.concurrent.CountDownLatch settling = new java.util.concurrent.CountDownLatch(1);
            a.beforeSettle = () -> {
                a.beforeSettle = null;
                settling.countDown();
            };
            a.stallNextCommit = true;
            one.add("coins", new Object[] {50L});
            one.addToList("queue", new Object[] {text("alice")});
            waitFor(() -> a.stalled != null);
            Connection stalled = a.stalled;
            // a is back and settling, while the stalled transaction is still open: settling waits for it.
            assertTrue(settling.await(10, java.util.concurrent.TimeUnit.SECONDS));
            Thread.sleep(400);
            assertEquals(1, settled.getCount(), "settled before the stalled commit landed");
            if (commits) {
                stalled.commit();
            } else {
                stalled.rollback();
            }
            stalled.close();
            assertTrue(settled.await(10, java.util.concurrent.TimeUnit.SECONDS));
            if (!commits) {
                // Nothing landed: a writes its 50 and its append again, once, before b's 3.
                bWrote.countDown();
                waitFor(() -> Long.valueOf(60).equals(two.get("coins")) && two.list("queue").length == 2);
            }
            two.add("coins", new Object[] {3L});
            b.save();
            bWrote.countDown();
            waitFor(() -> Long.valueOf(63).equals(one.get("coins")) && Long.valueOf(63).equals(two.get("coins")));
            Thread.sleep(1_000);
            assertEquals(63L, one.get("coins"));
            assertEquals(63L, saved("coins"));
            assertEquals(2, one.list("queue").length);
            assertEquals(2, two.list("queue").length);
        }
    }

    @Test
    void aSlowCommitThatLandsWhileItsSettledIsCountedOnce() throws Exception {
        stalledCommit(true);
    }

    @Test
    void aSlowCommitThatNeverLandsIsWrittenAgainOnce() throws Exception {
        stalledCommit(false);
    }

    @Test
    void aServerCutOffWithACommitUnsettledFindsItsMarkerWhenBack() throws Exception {
        VariableStore one = new VariableStore();
        VariableStore two = new VariableStore();
        SqlVariableStorage[] both = pair(one, two, "coins", 10L);
        try (SqlVariableStorage a = both[0]; SqlVariableStorage b = both[1]) {
            // b's commit (60, and an append) lands but the answer is lost, and b can't reach the database for a while.
            b.loseNextReply = true;
            two.add("coins", new Object[] {50L});
            two.addToList("queue", new Object[] {text("alice")});
            waitFor(() -> !b.loseNextReply);
            b.unreachable = true;
            waitFor(() -> Long.valueOf(60).equals(one.get("coins")));
            // Meanwhile a adds 5 on top and keeps watching the table: b's marker is b's alone to remove.
            one.add("coins", new Object[] {5L});
            a.save();
            Thread.sleep(1_000);
            assertTrue(rows().containsKey(SqlVariableStorage.MARKER + guidOf(b)), "b's marker is still there");
            b.unreachable = false;
            waitFor(() -> b.status().state() == SqlVariableStorage.State.CONNECTED);
            waitFor(() -> Long.valueOf(65).equals(two.get("coins")));
            Thread.sleep(1_000);
            assertEquals(65L, one.get("coins"));
            assertEquals(65L, two.get("coins"));
            assertEquals(65L, saved("coins"));
            assertEquals(1, one.list("queue").length);
            assertEquals(1, two.list("queue").length);
        }
    }

    /** A storage's update_guid, as its run file keeps it. */
    private String guidOf(SqlVariableStorage storage) throws SQLException {
        String run;
        try {
            run = Files.readString(storage.runFile()).strip();
        } catch (IOException e) {
            throw new SQLException(e);
        }
        return run;
    }

    @Test
    void aCrashedRunsMarkerIsRemovedWhenTheServerNextStarts() throws Exception {
        VariableStore store = new VariableStore();
        Path runFile;
        try (SqlVariableStorage storage = storage(store, true)) {
            storage.load();
            runFile = storage.runFile();
        }
        // A run that crashed: its update_guid kept, its marker row left behind.
        Files.writeString(runFile, "crashed-run");
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO variables21 (name, type, value, update_guid) VALUES (?, '7', NULL, 'crashed-run')")) {
            p.setString(1, SqlVariableStorage.MARKER + "crashed-run");
            p.executeUpdate();
        }
        // Another server's marker is never touched.
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO variables21 (name, type, value, update_guid) VALUES (?, '3', NULL, 'other')")) {
            p.setString(1, SqlVariableStorage.MARKER + "other");
            p.executeUpdate();
        }
        try (SqlVariableStorage storage = storage(new VariableStore(), true)) {
            storage.load();
            assertFalse(rows().containsKey(SqlVariableStorage.MARKER + "crashed-run"));
            assertTrue(rows().containsKey(SqlVariableStorage.MARKER + "other"));
            assertFalse(Files.readString(runFile).strip().equals("crashed-run"), "this run's own is kept now");
        }
    }

    @Test
    void aStalledCommitsConnectionIsKilledOnlyIfItsCertainlyThisRunsOwn() {
        String run = "3f6c2a1e-0000-4000-8000-000000000001";
        Map<String, String> ours = Map.of(SqlVariableStorage.RUN_ATTRIBUTE, run, SqlVariableStorage.SERIAL_ATTRIBUTE,
                "4", "_client_name", "MySQL Connector/J");
        assertTrue(SqlVariableStorage.mayKill(ours, run, 4));
        // Another of this run's connections, another run's (another server, or this one before a restart), a client
        // without the attributes, no such connection, or attributes that can't be read: never killed.
        assertFalse(SqlVariableStorage.mayKill(ours, run, 3));
        assertFalse(SqlVariableStorage.mayKill(Map.of(SqlVariableStorage.RUN_ATTRIBUTE, "another-run",
                SqlVariableStorage.SERIAL_ATTRIBUTE, "4"), run, 4));
        assertFalse(SqlVariableStorage.mayKill(Map.of("_client_name", "some app"), run, 4));
        assertFalse(SqlVariableStorage.mayKill(Map.of(), run, 4));
        assertFalse(SqlVariableStorage.mayKill(null, run, 4));
    }
}
