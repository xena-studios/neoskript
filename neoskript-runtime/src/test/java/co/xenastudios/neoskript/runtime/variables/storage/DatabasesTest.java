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

    /** Runs {@code backup} on another thread while this one holds {@code storage}'s lock, as a save would. */
    private static Path backupWhileLocked(Object storage, java.util.concurrent.Callable<Path> backup) throws Exception {
        java.util.concurrent.ExecutorService other = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            synchronized (storage) {
                return other.submit(backup).get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void aBackupDoesntHoldTheStorageWhileItCopies() throws Exception {
        Path file = dir.resolve("variables.csv");
        write(file, "x", "y");
        VariableStorage csv = new VariableStorage(new VariableStore(), new VariableCodecs(), file);
        csv.load();
        // A save, which holds the storage, isn't held up by a backup, nor the backup by it.
        Path copy = backupWhileLocked(csv, csv::backupNow);
        assertEquals(Files.readString(file), Files.readString(copy));

        VariableStore store = new VariableStore();
        Path nsv = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = new NativeVariableStorage(store, new VariableCodecs(), nsv)) {
            storage.load();
            store.set("a", new RawValue("string", "one".getBytes(StandardCharsets.UTF_8)));
            storage.save();
            Path snapshot = storage.backupNow();
            // Only opening the file and reading its header take the lock.
            assertTrue(Files.exists(snapshot));
            store.set("b", new RawValue("string", "two".getBytes(StandardCharsets.UTF_8)));
            storage.save();
            VariableStore back = new VariableStore();
            try (NativeVariableStorage read = new NativeVariableStorage(back, new VariableCodecs(), snapshot)) {
                read.load();
            }
            assertNotNull(back.get("a"));
            assertNull(back.get("b"), "the backup is the file as it was");
        }
    }

    @Test
    void aNativeBackupKeepsTheHeaderItStartedWith() throws Exception {
        // A save rewrites a commit in the header after the copy has started: the copy keeps the header it read.
        Path from = dir.resolve("source.bin");
        Files.write(from, "HEADERrecords-and-more".getBytes(StandardCharsets.UTF_8));
        Path to = dir.resolve("copy.bin");
        try (java.nio.channels.FileChannel in = java.nio.channels.FileChannel.open(from)) {
            Files.write(from, "CHANGErecords-and-more-appended".getBytes(StandardCharsets.UTF_8));
            Durable.copy(in, "HEADER".getBytes(StandardCharsets.UTF_8), 22, to, () -> false,
                    Files.getLastModifiedTime(from));
        }
        assertEquals("HEADERrecords-and-more", Files.readString(to));
    }

    @Test
    void aBackupCutOffLeavesNothing() throws Exception {
        Path from = dir.resolve("big.csv");
        try (java.nio.channels.FileChannel out = java.nio.channels.FileChannel.open(from,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            out.write(java.nio.ByteBuffer.wrap(new byte[] {1}), 20L << 20);
        }
        Path to = dir.resolve("backups").resolve("big_2026-11-17_01-02-03_scheduled.csv");
        Files.createDirectories(to.getParent());
        int[] checks = {0};
        try (java.nio.channels.FileChannel in = java.nio.channels.FileChannel.open(from)) {
            // Cut off once the first few megabytes are copied, as at the server's stop.
            assertThrows(IOException.class, () -> Durable.copy(in, null, in.size(), to, () -> checks[0]++ > 0,
                    Files.getLastModifiedTime(from)));
        }
        assertFalse(Files.exists(to));
        assertFalse(Files.exists(Durable.temporary(to)));

        Path file = dir.resolve("variables.csv");
        write(file, "x");
        VariableStorage storage = new VariableStorage(new VariableStore(), new VariableCodecs(), file);
        storage.load();
        storage.cancelBackup();
        assertNull(storage.backupNow(), "none after the stop has begun");
    }

    @Test
    void whatABackupCutOffByACrashLeftIsRemovedByItsExactName() throws Exception {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        List<String> leftovers = List.of("variables_2026-11-17_01-02-03_scheduled.csv.copying.tmp",
                "variables_2026-11-17_01-02-03-2.csv.copying.tmp", "variables_2026-11-17_01-02-03.csv1234567.tmp",
                // A scheduled backup's temporary file from before .copying.tmp, as the system named it.
                "variables_2026-11-17_01-02-03_scheduled.csv1234567.tmp");
        List<String> kept = List.of("variables_2026-11-17_01-02-03_scheduled.csv", "variables_notes.csv.copying.tmp",
                "other_2026-11-17_01-02-03_scheduled.csv.copying.tmp",
                "variables_2026-11-17_01-02-03_scheduled.csv.copying.tmp.old",
                "myvariables_2026-11-17_01-02-03.csv.copying.tmp",
                // An owner's own copy, as the native storage's test has it.
                "variables_2026-11-17_01-02-03_scheduled.csv.tmp");
        for (String name : leftovers) {
            Files.writeString(backups.resolve(name), "part");
        }
        for (String name : kept) {
            Files.writeString(backups.resolve(name), "keep");
        }
        Path file = dir.resolve("variables.csv");
        write(file, "x");
        new VariableStorage(new VariableStore(), new VariableCodecs(), file).load();
        for (String name : leftovers) {
            assertFalse(Files.exists(backups.resolve(name)), name);
        }
        for (String name : kept) {
            assertTrue(Files.exists(backups.resolve(name)), name);
        }
    }
}
