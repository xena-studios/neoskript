/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Saves global variables in an SQL database, as Skript's SQLite and MySQL storage does: the table {@code variables21}
 * with Skript's exact layout, each value in the same binary format as {@code variables.csv}, so Skript and NeoSkript
 * can take turns on the same database.
 *
 * <p>Everything the database is asked happens on this database's own thread, never the server's. Every second or
 * so, if variables changed, it works out what changed since the last write (as the native storage does) and writes
 * that in one transaction. Nothing is queued: a write that fails, because the database is down or the file is
 * locked, is simply worked out again and retried, so a long outage costs no memory, and nothing is lost while the
 * server runs. If the server stops before the database is back, the changes it couldn't write are kept in a file
 * ({@link #pendingFile}) and written at the next start.
 *
 * <p>Nothing is written until the table has been read. If the database can't be reached at startup, the variables
 * it holds aren't loaded and nothing is written to it; it's tried again in the background, and once it answers its
 * variables are loaded (replacing values scripts set for those names meanwhile) and writing starts.
 *
 * <p>With {@code monitor changes}, the table is read for other servers' changes every {@code monitor interval},
 * through Skript's {@code update_guid}. Unlike Skript, a variable deleted on one server is deleted on the others: a
 * deletion is written as a row without a value (which Skript ignores when it reads), and such rows are removed a
 * while later, as Skript removes them.
 */
public final class SqlVariableStorage implements VariableBackend {

    /** Skript's limits ({@code SQLStorage}). */
    static final int MAX_NAME_LENGTH = 380;

    /** Skript's table. */
    public static final String DEFAULT_TABLE = "variables21";

    /** What a table name may be, so it can go in a query as it is. */
    public static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,63}");

    /** Rows per batch in a write. */
    private static final int BATCH = 500;

    /** How long a final write may take when the server stops. Lower in tests. */
    static volatile long closeTimeoutMillis = 15_000;

    /**
     * How long loading may wait for the database when the server starts, on the server's thread. Past it, the database
     * counts as down and loads later. Lower in tests.
     */
    static volatile long startupTimeoutMillis = 10_000;

    /** How often the background thread looks for changes. Lower in tests. */
    static volatile long tickMillis = 1_000;

    /** The longest wait between attempts to reach a database that's down. Lower in tests. */
    static volatile long maxRetryMillis = 30_000;

    /** More connection properties, for tests (a page limit standing in for a full disk). */
    static volatile Properties extraProperties;

    /** How often a database that's down is mentioned again. */
    private static final long REMIND_MILLIS = 5 * 60_000;

    /** How often the pending changes are written to {@link #pendingFile} while the database is down. */
    static volatile long pendingEveryMillis = 30_000;

    /** The kind of database. */
    public enum Dialect {
        SQLITE("SQLite", "org.sqlite.JDBC"),
        MYSQL("MySQL", "com.mysql.cj.jdbc.Driver");

        final String display;
        final String driver;

        Dialect(String display, String driver) {
            this.display = display;
            this.driver = driver;
        }

        /** The table, as Skript creates it. */
        String createTable(String table) {
            return switch (this) {
                case SQLITE -> "CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "name         VARCHAR(" + MAX_NAME_LENGTH + ")  NOT NULL  PRIMARY KEY,"
                        + "type         VARCHAR(50),"
                        + "value        BLOB(10000),"
                        + "update_guid  CHAR(36)  NOT NULL"
                        + ")";
                case MYSQL -> "CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "rowid        BIGINT  NOT NULL  AUTO_INCREMENT  PRIMARY KEY,"
                        + "name         VARCHAR(" + MAX_NAME_LENGTH + ")  NOT NULL  UNIQUE,"
                        + "type         VARCHAR(50),"
                        + "value        BLOB(10000),"
                        + "update_guid  CHAR(36)  NOT NULL"
                        + ") CHARACTER SET ucs2 COLLATE ucs2_bin";
            };
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * Where the database is, from config.yml. The password is never shown: {@link #toString} and {@link #where} leave
     * it out.
     *
     * @param name the database's name in config.yml
     * @param file SQLite's file
     * @param monitorMillis how often other servers' changes are read, if {@code monitor}
     */
    public record Settings(String name, Dialect dialect, Path file, String host, int port, String database,
            String user, String password, String table, boolean monitor, long monitorMillis) {

        public Settings {
            if (table == null || !TABLE_NAME.matcher(table).matches()) {
                throw new IllegalArgumentException("table '" + table + "' isn't a table name (letters, digits and _)");
            }
        }

        public static Settings sqlite(String name, Path file, String table, boolean monitor, long monitorMillis) {
            return new Settings(name, Dialect.SQLITE, file, null, 0, null, null, null, table, monitor, monitorMillis);
        }

        public static Settings mysql(String name, String host, int port, String database, String user, String password,
                String table, boolean monitor, long monitorMillis) {
            return new Settings(name, Dialect.MYSQL, null, host, port, database, user, password, table, monitor,
                    monitorMillis);
        }

        /** Where it is, without the password: the file, or user@host:port/database. */
        public String where() {
            return dialect == Dialect.SQLITE ? String.valueOf(file)
                    : user + "@" + host + ":" + port + "/" + database;
        }

        @Override
        public String toString() {
            return dialect + " " + where() + ", table " + table;
        }
    }

    /** Where a database stands, for {@code /sk info}. */
    public enum State {
        /** Not reached yet: its variables aren't loaded. */
        CONNECTING,
        /** Loaded, and writes go through. */
        CONNECTED,
        /** Loaded, but it stopped answering: changes wait until it's back. */
        DOWN,
        /** Closed when the server stopped. */
        CLOSED
    }

    /**
     * What {@code /sk info} shows.
     *
     * @param variables how many variables the table has, as far as this server knows
     * @param waiting changes that couldn't be written yet, as of the last try
     * @param problem why it's down or not reached yet, or {@code null}
     * @param since when it went down or started trying
     */
    public record Status(State state, int variables, int waiting, String problem, Instant since) {}

    /** What was last written for a variable, or read for it. */
    private static final class Saved {
        Object value;
        final String type;
        final byte[] bytes;
        /** The update_guid of the row as last read or written: which server's write it is. */
        final String guid;
        long seen;

        Saved(Object value, String type, byte[] bytes, String guid) {
            this.value = value;
            this.type = type;
            this.bytes = bytes;
            this.guid = guid;
        }
    }

    /** One change to write: a value, or a deletion ({@code type} {@code null}). */
    private record Op(String name, String type, byte[] bytes, Saved saved) {}

    private final VariableStore store;
    private final VariableStore loadInto;
    private final Routing routing;
    private final VariableCodecs codecs;
    private final Settings settings;
    private final Console console;
    private final Path pendingFile;
    private final Path unloadedFile;
    /** Which database this is, for the pending files: never the password. */
    private final String identity;
    /** Skript's {@code update_guid}: which server wrote a row. One per start. */
    private final String guid = UUID.randomUUID().toString();
    private final Map<String, Saved> saved = new ConcurrentHashMap<>();
    /** Values the database refused (too long a name, say), by name, so they aren't tried again until they change. */
    private final Map<String, byte[]> refused = new ConcurrentHashMap<>();
    private final ScheduledExecutorService thread;
    private final List<String> notes = new ArrayList<>();

    // Only touched on the database's thread, or before it starts.
    private Connection connection;
    private long round;
    private long writtenAt = Long.MIN_VALUE;
    private long lastWriteNanos;
    private long nextWriteAt;
    private long nextAttemptAt;
    private long retryMillis = 1_000;
    private long remindAt;
    private long pendingWrittenAt;
    private long pendingMods = Long.MIN_VALUE;
    private long lastRowId = -1;
    /** Rows read by the last monitor check, so a check that reads them again doesn't apply them twice. */
    private final java.util.Set<Long> recentRows = new java.util.HashSet<>();
    private long recheckFrom = -1;
    private long nextMonitorAt;
    private final List<long[]> cleanups = new ArrayList<>();
    /** Whether the pending file was applied at load and still has to be written. */
    private boolean pendingApplied;
    /** Set once closing starts: a load still running then is thrown away rather than applied. */
    private volatile boolean closing;
    /** Set when loading at startup took too long: whatever it reads afterwards is thrown away. */
    private volatile boolean abandoned;
    /** The connection being opened or read at startup, so a load that takes too long can be cut off. */
    private volatile Connection connecting;
    /** For tests: hold writes back, as a slow write would. */
    volatile boolean writesHeld;

    private volatile boolean loaded;
    private volatile boolean written;
    private volatile boolean closed;
    private volatile State state = State.CONNECTING;
    private volatile String problem;
    private volatile Instant since = Instant.now();
    private volatile int waiting;

    /**
     * @param pendingDir where changes that couldn't be written before the server stopped are kept until the next
     *     start, in files named after the database and a hash of {@link #identity}
     */
    public SqlVariableStorage(VariableStore store, VariableCodecs codecs, Settings settings, Routing routing,
            Path pendingDir, Console console) {
        this.store = store;
        this.loadInto = routing.loadInto() != null ? routing.loadInto() : store;
        this.routing = routing;
        this.codecs = codecs;
        this.settings = settings;
        this.identity = identity(settings);
        String base = settings.name().replaceAll("[^A-Za-z0-9_-]", "_") + "-" + hash(identity);
        this.pendingFile = pendingDir.resolve(base + ".pending.csv");
        this.unloadedFile = pendingDir.resolve(base + ".unloaded.csv");
        this.console = console;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread t = new Thread(task, "NeoSkript database '" + settings.name() + "'");
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        this.thread = executor;
    }

    public Settings settings() {
        return settings;
    }

    /** Where changes waiting for this database are kept when the server stops. */
    public Path pendingFile() {
        return pendingFile;
    }

    /** Where the variables of a database that was never reached are kept when the server stops. */
    Path unloadedFile() {
        return unloadedFile;
    }

    /** What makes this database this one: its type, where it is and its table. Never the user or password. */
    static String identity(Settings settings) {
        return switch (settings.dialect()) {
            case SQLITE -> "sqlite|" + settings.file().toAbsolutePath().normalize() + "|"
                    + settings.table().toLowerCase(Locale.ROOT);
            case MYSQL -> "mysql|" + settings.host().toLowerCase(Locale.ROOT) + "|" + settings.port() + "|"
                    + settings.database() + "|" + settings.table().toLowerCase(Locale.ROOT);
        };
    }

    private static String hash(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** SQLite's file, or {@code null} for MySQL. */
    @Override
    public Path file() {
        return settings.file();
    }

    @Override
    public String describe() {
        return "database '" + settings.name() + "' (" + settings.dialect() + ", " + settings.where() + ")";
    }

    @Override
    public boolean loaded() {
        return loaded;
    }

    /** Whether a write has gone through since the table was read. */
    public boolean written() {
        return written;
    }

    public Status status() {
        return new Status(state, saved.size(), waiting, problem, since);
    }

    @Override
    public synchronized List<String> notes() {
        List<String> drained = List.copyOf(notes);
        notes.clear();
        return drained;
    }

    private synchronized void note(String line) {
        notes.add(line);
    }

    // ---- Loading -------------------------------------------------------------------------------------------------

    /**
     * Reads the table into the store, on the calling thread. If the database can't be reached, nothing is loaded and
     * this returns no variables: the database's thread keeps trying, and loads it when it answers. Either way the
     * thread starts here.
     */
    @Override
    public VariableStorage.Result load() throws IOException {
        if (loaded) {
            throw new IllegalStateException(describe() + " is already loaded");
        }
        if (settings.dialect() == Dialect.SQLITE) {
            try {
                // What backups cut off by a crash left: the file's own, so it's never needed.
                VariableStorage.removeBackupLeftovers(settings.file());
            } catch (IOException | RuntimeException e) {
                synchronized (this) {
                    notes.add("Couldn't remove unfinished backups of " + describe() + ": " + e.getMessage());
                }
            }
        }
        VariableStorage.Result result;
        // On the database's thread, so a database that doesn't answer can't hold the server's thread past the bound.
        Future<VariableStorage.Result> first = thread.submit(() -> connectAndLoad(loadInto, false));
        Exception failure;
        try {
            result = first.get(startupTimeoutMillis, TimeUnit.MILLISECONDS);
            failure = null;
        } catch (TimeoutException e) {
            synchronized (this) {
                abandoned = true;
            }
            abort(connecting);
            first.cancel(true);
            failure = new SQLException("no answer within " + startupTimeoutMillis / 1000 + " seconds");
            result = null;
        } catch (ExecutionException e) {
            failure = e.getCause() instanceof Exception cause ? cause : e;
            result = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = e;
            result = null;
        }
        if (result == null) {
            Exception cause = failure;
            // What an earlier start kept goes into memory first, and the kept file isn't written again for an
            // interval: until then memory may not hold it all yet (with several databases, it's merged in after
            // this returns), and an emptier file written meanwhile would lose it.
            loadUnloaded(loadInto);
            synchronized (this) {
                pendingWrittenAt = System.currentTimeMillis();
            }
            // The thread may still be finishing the abandoned load: what happens next happens there, in order.
            thread.execute(() -> {
                closeConnection();
                down(cause, true);
            });
            if (cause instanceof MissingDriver) {
                console.error("Can't use " + describe() + ": " + message(cause) + ". Its variables aren't loaded, "
                        + "and nothing is written to it.");
            } else {
                console.error("Couldn't reach " + describe() + ": " + message(cause) + ". Its variables aren't "
                        + "loaded, and nothing is written to it, until it answers; NeoSkript keeps trying in the "
                        + "background.");
            }
            result = new VariableStorage.Result(0, 0, 0);
        }
        thread.scheduleWithFixedDelay(this::tick, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * Connects, makes the table if it isn't there, and reads it into {@code into}. With {@code late}, scripts are
     * running: the database's values replace theirs for the names it has, and the console hears which changed.
     */
    private VariableStorage.Result connectAndLoad(VariableStore into, boolean late) throws SQLException, IOException {
        Connection c = connect();
        connecting = c;
        List<VariableFile.Problem> skipped = new ArrayList<>();
        Map<String, Saved> read = new LinkedHashMap<>();
        Map<String, String> spelling = new java.util.HashMap<>();
        int raw = 0;
        long maxRow = -1;
        try {
            try (Statement create = c.createStatement()) {
                create.executeUpdate(settings.dialect().createTable(settings.table()));
            } catch (SQLException e) {
                if (!cantCreate(e)) {
                    throw e;
                }
                throw new SQLException("Could not create the variables table '" + settings.table() + "' in the "
                        + "database '" + settings.name() + "': " + e.getMessage() + ". Please create the table "
                        + "yourself using the following query: " + settings.dialect().createTable(settings.table())
                                .replace(",", ", ").replaceAll("\\s+", " "), e.getSQLState(), e.getErrorCode(), e);
            }
            c.commit();
            try (Statement select = c.createStatement();
                    ResultSet rows = select.executeQuery("SELECT name, type, value, rowid, update_guid FROM "
                            + settings.table())) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    String type = rows.getString(2);
                    byte[] bytes = rows.getBytes(3);
                    maxRow = Math.max(maxRow, rows.getLong(4));
                    if (name == null) {
                        // Skript's message.
                        skipped.add(new VariableFile.Problem(0, "", "<unknown>", "Variable with NULL name found in the "
                                + "database '" + settings.name() + "', ignoring it"));
                        continue;
                    }
                    if (bytes == null || type == null) {
                        // A deletion other servers haven't seen yet, or are about to tidy away.
                        continue;
                    }
                    Object value = codecs.decode(type, bytes);
                    if (value instanceof RawValue) {
                        raw++;
                    }
                    // Two rows for one name that differ only in case (MySQL's ucs2_bin tells them apart, Skript's names
                    // don't): the last read wins, as in Skript.
                    String key = name.toLowerCase(Locale.ROOT);
                    read.remove(key);
                    read.put(key, new Saved(value, type, bytes, rows.getString(5)));
                    spelling.put(key, name);
                }
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            connecting = null;
            try {
                c.close();
            } catch (SQLException closing) {
                e.addSuppressed(closing);
            }
            throw e;
        }
        List<String> names = new ArrayList<>(read.size());
        List<Object> values = new ArrayList<>(read.size());
        int replaced = 0;
        int leftAlone = 0;
        List<String> replacedNames = new ArrayList<>();
        for (Map.Entry<String, Saved> entry : read.entrySet()) {
            String key = entry.getKey();
            if (late && (!routing.owns().test(key) || routing.loadedElsewhere().test(key))) {
                // Scripts are running: a row this database doesn't own, or one another database loaded at startup,
                // follows the order rule startup used and never replaces what's in memory. The row stays, and is
                // let go of as at startup once the database that owns it has saved it.
                leftAlone++;
                continue;
            }
            names.add(spelling.get(key));
            values.add(entry.getValue().value);
        }
        Map<String, VariableFile.Entry> pending = readPending(read, late);
        synchronized (this) {
            if ((!late && abandoned) || closing) {
                connecting = null;
                try {
                    c.close();
                } catch (SQLException e) {
                    // Gone already.
                }
                throw new SQLException("the load took too long and was abandoned");
            }
            if (late) {
                for (int i = 0; i < names.size(); i++) {
                    Object before = into.get(names.get(i));
                    if (before != null && !before.equals(values.get(i))) {
                        replaced++;
                        if (replacedNames.size() < 10) {
                            replacedNames.add(names.get(i));
                        }
                    }
                    into.set(names.get(i), values.get(i));
                }
            } else {
                into.load(names, values);
            }
            saved.clear();
            saved.putAll(read);
            lastRowId = maxRow;
            recheckFrom = maxRow;
            recentRows.clear();
            if (!pending.isEmpty()) {
                applyPending(into, pending);
            }
            // What was kept while it was never reached is in memory now: from here on memory holds the truth, and
            // the pending file covers what isn't written yet. Kept, the file would bring back variables scripts
            // delete before the next write goes through.
            try {
                Files.deleteIfExists(unloadedFile);
            } catch (IOException e) {
                console.warn("Couldn't remove " + unloadedFile + ", which is loaded now: " + e.getMessage()
                        + ". Remove it by hand, or its variables come back at the next start.");
            }
            connecting = null;
        }
        connection = c;
        loaded = true;
        state = State.CONNECTED;
        problem = null;
        since = Instant.now();
        retryMillis = 1_000;
        nextMonitorAt = System.currentTimeMillis() + settings.monitorMillis();
        if (late) {
            String names2 = replacedNames.isEmpty() ? ""
                    : " The saved values replaced what scripts had set meanwhile for " + replaced + " of them: "
                            + String.join(", ", replacedNames) + (replaced > replacedNames.size() ? ", ..." : "") + ".";
            String others = leftAlone == 0 ? "" : " " + leftAlone + " of its rows belong to another database, or were "
                    + "loaded from one at startup, and were left as they are in memory.";
            console.info("Reached " + describe() + " and loaded " + (read.size() - leftAlone) + " variables." + names2
                    + others);
        }
        return new VariableStorage.Result(read.size(), raw, 0, List.copyOf(skipped), null, 0);
    }

    // ---- The database's thread -----------------------------------------------------------------------------------

    private void tick() {
        if (closed) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (connection == null) {
                if (now < nextAttemptAt) {
                    remind(now);
                    keepPending(now);
                    return;
                }
                reconnect();
                if (connection == null) {
                    return;
                }
            }
            // Changes are written before other servers' are read, as Skript writes each change at once: a remote
            // row is then never older than what's in memory, unless writing is slow or fails, which monitor() checks.
            if (!writesHeld && now >= nextWriteAt && (store.modifications() != writtenAt || pendingApplied)) {
                write();
            }
            if (connection != null && settings.monitor() && now >= nextMonitorAt) {
                monitor();
                nextMonitorAt = System.currentTimeMillis() + settings.monitorMillis();
            }
            if (connection != null) {
                runCleanups(now);
            }
        } catch (RuntimeException | LinkageError e) {
            // Never let the thread die: the next tick tries again.
            console.error("Unexpected problem with " + describe() + ": " + e);
        }
    }

    private void reconnect() {
        try {
            if (!loaded) {
                connectAndLoad(store, true);
                written = false;
                return;
            }
            connection = connect();
            int waitingBefore = waiting;
            state = State.CONNECTED;
            problem = null;
            since = Instant.now();
            retryMillis = 1_000;
            if (!write()) {
                return;
            }
            console.info(describe() + " is back" + (waitingBefore == 0 ? "." : ": wrote the " + waitingBefore
                    + " changes that were waiting."));
        } catch (SQLException | IOException | RuntimeException e) {
            closeConnection();
            down(e, false);
        }
    }

    /** Says the database is down, the first time, and when to try again. */
    private void down(Exception e, boolean quiet) {
        long now = System.currentTimeMillis();
        String reason = message(e);
        boolean first = state == State.CONNECTED || (state == State.CONNECTING && problem == null);
        if (state == State.CONNECTED) {
            state = State.DOWN;
            since = Instant.now();
        }
        problem = reason;
        nextAttemptAt = e instanceof MissingDriver ? Long.MAX_VALUE : now + retryMillis;
        retryMillis = Math.min(retryMillis * 2, maxRetryMillis);
        if (first && !quiet) {
            if (loaded) {
                console.error("Lost " + describe() + ": " + reason + ". " + waitingText() + "NeoSkript keeps trying, "
                        + "and nothing is lost while the server runs.");
            } else {
                console.error("Couldn't reach " + describe() + ": " + reason + ". Its variables aren't loaded, and "
                        + "nothing is written to it, until it answers.");
            }
        }
        if (first) {
            remindAt = now + REMIND_MILLIS;
        }
        keepPending(now);
    }

    private String waitingText() {
        return waiting == 0 ? "" : waiting + (waiting == 1 ? " change is" : " changes are") + " waiting to be written. ";
    }

    private void remind(long now) {
        if (now < remindAt) {
            return;
        }
        remindAt = now + REMIND_MILLIS;
        String since = Duration.between(this.since, Instant.now()).toMinutes() + " minutes";
        if (loaded) {
            console.warn(describe() + " is still down (" + problem + ", for " + since + "). " + waitingText()
                    + "/sk info shows its state.");
        } else {
            console.warn(describe() + " still can't be reached (" + problem + ", for " + since + "). Its variables "
                    + "aren't loaded. /sk info shows its state.");
        }
    }

    // ---- Writing -------------------------------------------------------------------------------------------------

    /** The changes since the last write: each variable this database saves whose value differs from what it has. */
    private List<Op> changes(long current, List<String> kept, List<String> notSaved, int[] counts) {
        List<Op> ops = new ArrayList<>();
        java.util.Set<String> upserted = new java.util.HashSet<>();
        store.forEach((variable, value) -> {
            if (variable.startsWith("-")) {
                return;
            }
            String name = variable.toLowerCase(Locale.ROOT);
            Saved last = saved.get(name);
            if (!routing.owns().test(name)) {
                // Another database's, or none's. Skript keeps such rows, so this does too, unless the database that
                // owns it has saved it.
                if (last != null && !routing.movedAway().test(name)) {
                    last.seen = current;
                }
                return;
            }
            if (last != null && last.value == value && immutable(value)) {
                last.seen = current;
                counts[0]++;
                return;
            }
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(value);
            } catch (RuntimeException | LinkageError failed) {
                // A codec that fails: the variable keeps what was saved, as in variables.csv.
                if (last != null) {
                    last.seen = current;
                    kept.add(name);
                } else {
                    notSaved.add(name);
                }
                return;
            }
            if (encoded == null) {
                counts[1]++;
                return;
            }
            counts[0]++;
            if (last != null && last.type.equals(encoded.type()) && Arrays.equals(last.bytes, encoded.bytes())) {
                last.seen = current;
                last.value = value;
                return;
            }
            byte[] before = refused.get(name);
            if (before != null && Arrays.equals(before, encoded.bytes())) {
                if (last != null) {
                    last.seen = current;
                }
                return;
            }
            refused.remove(name);
            if (settings.dialect() == Dialect.MYSQL && variable.length() > MAX_NAME_LENGTH) {
                refuse(name, encoded.bytes(), "its name is longer than the " + MAX_NAME_LENGTH + " characters a "
                        + "database can hold");
                if (last != null) {
                    last.seen = current;
                }
                return;
            }
            Saved next = new Saved(value, encoded.type(), encoded.bytes(), guid);
            next.seen = current;
            upserted.add(name);
            ops.add(new Op(name, encoded.type(), encoded.bytes(), next));
        });
        for (Map.Entry<String, Saved> entry : saved.entrySet()) {
            if (entry.getValue().seen != current && !upserted.contains(entry.getKey())) {
                ops.add(new Op(entry.getKey(), null, null, null));
            }
        }
        return ops;
    }

    private void refuse(String name, byte[] bytes, String why) {
        refused.put(name, bytes);
        console.error("Couldn't save {" + name + "} to " + describe() + ": " + why + ". It keeps its value until the "
                + "server stops, but isn't saved.");
    }

    /**
     * Writes what changed in one transaction. Returns whether it went through; if not, the database is marked down
     * and the same changes are worked out again next time.
     */
    private boolean write() {
        if (!loaded || connection == null) {
            return false;
        }
        long start = System.nanoTime();
        long modifications = store.modifications();
        long current = ++round;
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        int[] counts = new int[2];
        List<Op> ops = changes(current, kept, notSaved, counts);
        lastResult = new VariableStorage.Result(counts[0], 0, counts[1], List.of(), null, 0, List.copyOf(kept),
                List.copyOf(notSaved));
        if (ops.isEmpty()) {
            finishWrite(modifications, start);
            return true;
        }
        waiting = ops.size();
        try {
            try {
                apply(ops);
                connection.commit();
            } catch (SQLException e) {
                rollbackQuietly();
                if (!isBadRow(e)) {
                    throw e;
                }
                // One of the rows: write them one by one, and leave out the ones the database refuses.
                for (Op op : ops) {
                    try {
                        apply(List.of(op));
                        connection.commit();
                    } catch (SQLException one) {
                        rollbackQuietly();
                        if (!isBadRow(one)) {
                            throw one;
                        }
                        refuse(op.name(), op.bytes() == null ? new byte[0] : op.bytes(), message(one));
                        continue;
                    }
                    record(op);
                }
                ops = List.of();
            }
        } catch (SQLException e) {
            closeConnection();
            down(e, false);
            return false;
        }
        for (Op op : ops) {
            record(op);
        }
        finishWrite(modifications, start);
        return true;
    }

    private volatile VariableStorage.Result lastResult = new VariableStorage.Result(0, 0, 0);

    private void record(Op op) {
        if (op.type() == null) {
            saved.remove(op.name());
        } else {
            saved.put(op.name(), op.saved());
        }
    }

    private void finishWrite(long modifications, long start) {
        writtenAt = modifications;
        waiting = 0;
        written = true;
        lastWriteNanos = System.nanoTime() - start;
        // A write that took long waits ten times as long before the next, so it never takes more than a tenth of a
        // thread however many variables there are.
        nextWriteAt = System.currentTimeMillis() + lastWriteNanos / 100_000;
        if (pendingApplied || Files.exists(pendingFile) || Files.exists(unloadedFile)) {
            try {
                Files.deleteIfExists(pendingFile);
                // What was kept while it was never reached is in memory since startup, and written now.
                Files.deleteIfExists(unloadedFile);
                pendingApplied = false;
            } catch (IOException e) {
                console.warn("Couldn't remove " + pendingFile + ", which is written now: " + e.getMessage()
                        + ". Remove it by hand, or it's applied again at the next start.");
            }
        }
    }

    private void apply(List<Op> ops) throws SQLException {
        String replace = settings.dialect() == Dialect.SQLITE
                // The next rowid given explicitly, so rowids only go up: SQLite would reuse the one REPLACE just
                // deleted when it was the highest, and servers watching for changes would miss the row.
                ? "REPLACE INTO " + settings.table() + " (rowid, name, type, value, update_guid) VALUES ((SELECT "
                        + "COALESCE(MAX(rowid), 0) + 1 FROM " + settings.table() + "), ?, ?, ?, ?)"
                : "REPLACE INTO " + settings.table() + " (name, type, value, update_guid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement write = connection.prepareStatement(replace);
                PreparedStatement delete = connection.prepareStatement("DELETE FROM " + settings.table()
                        + " WHERE name = ?")) {
            int writes = 0;
            int deletes = 0;
            for (Op op : ops) {
                if (op.type() == null && !settings.monitor()) {
                    delete.setString(1, op.name());
                    delete.addBatch();
                    if (++deletes % BATCH == 0) {
                        delete.executeBatch();
                    }
                    continue;
                }
                // With monitor changes, a deletion is a row without a value, so other servers see it.
                write.setString(1, op.name());
                write.setString(2, op.type());
                write.setBytes(3, op.bytes());
                write.setString(4, guid);
                write.addBatch();
                if (++writes % BATCH == 0) {
                    write.executeBatch();
                }
            }
            if (writes % BATCH != 0) {
                write.executeBatch();
            }
            if (deletes % BATCH != 0) {
                delete.executeBatch();
            }
        } catch (java.sql.BatchUpdateException e) {
            // The cause says which row; the batch exception often doesn't.
            throw e.getNextException() != null ? e.getNextException() : e;
        }
    }

    /**
     * Whether making the table failed because it can't be made as written (no permission, a character set the server
     * doesn't have), so Skript's advice to make it by hand applies, rather than because the database is busy or gone.
     */
    private static boolean cantCreate(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("42"))
                || (e.getClass().getName().startsWith("com.mysql") && switch (e.getErrorCode()) {
                    case 1115, 1273, 1142, 1044 -> true;
                    default -> false;
                });
    }

    /**
     * Whether the database refused a row's data (too long, say), rather than failing as a whole: the rest can still
     * be written. Everything else (no connection, a locked file, a full disk) is worth trying again.
     */
    static boolean isBadRow(SQLException e) {
        String state = e.getSQLState();
        if (state != null && (state.startsWith("22") || state.startsWith("23"))) {
            return true;
        }
        String driver = e.getClass().getName();
        if (driver.startsWith("com.mysql")) {
            // Incorrect string value (a character ucs2 can't hold), data too long, out of range, cannot be null,
            // packet too big, incorrect value.
            return switch (e.getErrorCode()) {
                case 1366, 1406, 1264, 1048, 1153, 1292 -> true;
                default -> false;
            };
        }
        // SQLite: SQLITE_TOOBIG (18), SQLITE_CONSTRAINT (19), SQLITE_MISMATCH (20).
        int code = e.getErrorCode() & 0xFF;
        return driver.startsWith("org.sqlite") && (code == 18 || code == 19 || code == 20);
    }

    // ---- Monitoring other servers' changes -----------------------------------------------------------------------

    /**
     * Reads the rows other servers wrote since the last check, as Skript does through {@code update_guid}, and applies
     * them. Rows from the last check are read again, as a transaction that started before them may commit after: a
     * row already applied isn't applied twice.
     */
    private void monitor() {
        long from = recheckFrom;
        long max = lastRowId;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        try {
            try (PreparedStatement query = connection.prepareStatement("SELECT name, type, value, rowid, update_guid FROM "
                    + settings.table() + " WHERE rowid > ? AND update_guid != ? ORDER BY rowid")) {
                query.setLong(1, from);
                query.setString(2, guid);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        long row = rows.getLong(4);
                        seen.add(row);
                        max = Math.max(max, row);
                        if (recentRows.contains(row)) {
                            continue;
                        }
                        String name = rows.getString(1);
                        if (name == null) {
                            continue;
                        }
                        String type = rows.getString(2);
                        byte[] bytes = rows.getBytes(3);
                        String key = name.toLowerCase(Locale.ROOT);
                        // Between this check and store.set below a script could still change the variable, and the
                        // remote row would win; the window is a few microseconds, and Skript has the same one.
                        if (dirty(key, name)) {
                            // Changed here since the last write, which hasn't happened yet: the local change is newer,
                            // and is written over this row, as Skript would have written it at once.
                            continue;
                        }
                        if (bytes == null || type == null) {
                            saved.remove(key);
                            store.set(name, null);
                            continue;
                        }
                        Object value = codecs.decode(type, bytes);
                        saved.put(key, new Saved(value, type, bytes, rows.getString(5)));
                        store.set(name, value);
                    }
                }
            }
            // After the statement is closed: an open one keeps SQLite's read lock.
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            closeConnection();
            down(e, false);
            return;
        }
        recentRows.clear();
        recentRows.addAll(seen);
        recheckFrom = lastRowId;
        lastRowId = max;
        // Deletions other servers have seen by now are tidied away, as Skript does: twice the interval, plus five
        // seconds, after the check.
        cleanups.add(new long[] {System.currentTimeMillis() + 2 * settings.monitorMillis() + 5_000, from});
    }

    /** Whether {@code name}'s value in memory differs from what this database last wrote or read for it. */
    private boolean dirty(String key, String name) {
        Object current = store.get(name);
        Saved last = saved.get(key);
        if (current == null) {
            return last != null;
        }
        if (last == null) {
            return true;
        }
        if (last.value == current && immutable(current)) {
            return false;
        }
        try {
            VariableCodecs.Encoded encoded = codecs.encode(current);
            return encoded == null || !encoded.type().equals(last.type) || !Arrays.equals(encoded.bytes(), last.bytes);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private void runCleanups(long now) {
        while (!cleanups.isEmpty() && cleanups.getFirst()[0] <= now) {
            long below = cleanups.removeFirst()[1];
            if (below < 0) {
                continue;
            }
            try (PreparedStatement clean = connection.prepareStatement("DELETE FROM " + settings.table()
                    + " WHERE value IS NULL AND rowid < ?")) {
                clean.setLong(1, below);
                clean.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                rollbackQuietly();
                // Not worth more than a mention: it's tried again after the next check.
            }
        }
    }

    // ---- Changes kept across a restart ---------------------------------------------------------------------------

    /**
     * While down, the changes waiting are written to {@link #pendingFile} now and then, in case the server stops or
     * crashes. While never reached, the variables it would save go to {@link #unloadedFile} the same way.
     */
    private void keepPending(long now) {
        if (now - pendingWrittenAt < pendingEveryMillis || store.modifications() == pendingMods) {
            return;
        }
        pendingWrittenAt = now;
        pendingMods = store.modifications();
        writePending(!loaded);
    }

    /**
     * Writes the changes this database hasn't got to {@link #pendingFile}, in {@code variables.csv}'s format, a
     * deletion as a line without a type, with the database's identity, and for each name the state of its row as this
     * server last saw it ({@code update_guid} and a hash of the value), so the next start only applies a kept change
     * over a row nobody has changed since. If it was never loaded, that's every variable it would save, to
     * {@link #unloadedFile}: what an earlier start kept this way was put in memory when this one started, so memory
     * holds all of it.
     */
    private synchronized boolean writePending(boolean unloaded) {
        Map<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        List<String> comments = new ArrayList<>();
        comments.add("NeoSkript: changes not yet written to " + describe());
        comments.add("database: " + identity);
        comments.add("written: " + Instant.now());
        if (unloaded) {
            store.forEach((variable, value) -> {
                String name = variable.toLowerCase(Locale.ROOT);
                if (variable.startsWith("-") || !routing.owns().test(name)) {
                    return;
                }
                try {
                    VariableCodecs.Encoded encoded = codecs.encode(value);
                    if (encoded != null) {
                        entries.remove(name);
                        entries.put(name, new VariableFile.Entry(name, encoded.type(), encoded.bytes()));
                    }
                } catch (RuntimeException | LinkageError e) {
                    // Not saved, as it wouldn't have been.
                }
            });
        } else {
            for (Op op : changes(++round, new ArrayList<>(), new ArrayList<>(), new int[2])) {
                entries.put(op.name(), new VariableFile.Entry(op.name(), op.type() == null ? "" : op.type(),
                        op.bytes() == null ? new byte[0] : op.bytes()));
                Saved last = saved.get(op.name());
                comments.add("row: " + VariableFile.quote(op.name()) + ", " + rowState(last));
            }
            // What was seen this round isn't written, so the next write still finds the same changes.
        }
        Path target = unloaded ? unloadedFile : pendingFile;
        try {
            if (entries.isEmpty()) {
                // Nothing left to keep: an old file would bring back variables scripts deleted.
                Files.deleteIfExists(target);
                return true;
            }
            Files.createDirectories(target.toAbsolutePath().getParent());
            VariableFile.write(target, entries.values(), comments);
            return true;
        } catch (IOException | RuntimeException e) {
            console.error("Couldn't keep the " + entries.size() + " changes waiting for " + describe() + " in "
                    + target + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * A row's state for a pending file: its update_guid and a hash of its value, or {@code -} if there was none.
     *
     * <p>A known limit: another server that changes a row and then changes it back to exactly what this server last
     * saw, from the same start (the same update_guid), leaves it looking unchanged, so a kept change is applied over
     * it. Telling those apart would need a version column Skript's table doesn't have.
     */
    private static String rowState(Saved row) {
        if (row == null) {
            return "-";
        }
        return row.guid + " " + hash(row.type + "\u0000" + java.util.HexFormat.of().formatHex(row.bytes));
    }

    /**
     * The changes kept in {@link #pendingFile}, which win over the table's for a row unchanged since this server last
     * saw it, and at startup those in {@link #unloadedFile}, for names the table doesn't have. A late load reads
     * neither file's unloaded part: what an earlier start kept while the database was never reached went into memory
     * when this start began, and scripts may have changed or deleted it since, so memory holds the truth.
     */
    private Map<String, VariableFile.Entry> readPending(Map<String, Saved> table, boolean late) {
        Map<String, VariableFile.Entry> all = new LinkedHashMap<>();
        if (!late) {
            for (VariableFile.Entry entry : readKept(unloadedFile, true).values()) {
                String name = entry.name().toLowerCase(Locale.ROOT);
                if (!table.containsKey(name)) {
                    all.put(name, entry);
                }
            }
        }
        Map<String, String> states = rowStates(pendingFile);
        int changedSince = 0;
        for (VariableFile.Entry entry : readKept(pendingFile, true).values()) {
            String name = entry.name().toLowerCase(Locale.ROOT);
            String then = states.get(name);
            if (then == null || !then.equals(rowState(table.get(name)))) {
                // The row isn't as this server last saw it: something wrote it since.
                changedSince++;
                continue;
            }
            if (!late || routing.owns().test(name)) {
                all.put(name, entry);
            }
        }
        if (changedSince > 0) {
            console.warn(changedSince + " changes kept for " + describe() + " were left out: the database's rows for "
                    + "them changed after they were kept (another server wrote them, or they were already written), so "
                    + "the database's values are kept.");
        }
        return all;
    }

    /** The row states a pending file recorded, by name. */
    private Map<String, String> rowStates(Path file) {
        Map<String, String> states = new java.util.HashMap<>();
        if (!Files.exists(file)) {
            return states;
        }
        try (java.io.BufferedReader in = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String line = in.readLine(); line != null && (line.startsWith("#") || line.isBlank());
                    line = in.readLine()) {
                if (!line.startsWith("# row: ")) {
                    continue;
                }
                List<String> fields = VariableFile.split(line.substring("# row: ".length()));
                if (fields.size() == 2) {
                    states.put(fields.get(0).toLowerCase(Locale.ROOT), fields.get(1));
                }
            }
        } catch (IOException e) {
            // No states: nothing in it is applied, and readKept says why it can't be read.
        }
        return states;
    }

    /**
     * A kept file's entries, a deletion as an entry with an empty type. A file kept for another database (its identity
     * differs, as after the host or table changed) is moved aside with a warning, and nothing is read from it.
     */
    private Map<String, VariableFile.Entry> readKept(Path file, boolean moveAsideIfOther) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String kept = header(file, "database: ");
            if (kept != null && !kept.equals(identity)) {
                if (moveAsideIfOther) {
                    Path aside = file.resolveSibling(file.getFileName() + ".other-database-"
                            + System.currentTimeMillis());
                    Files.move(file, aside);
                    console.warn(file.getFileName() + " was kept for a different database (" + kept + "), not "
                            + describe() + ", so it isn't applied. It's now " + aside.getFileName() + ".");
                }
                return new LinkedHashMap<>();
            }
            return readWithDeletions(file);
        } catch (IOException | RuntimeException e) {
            console.error("Couldn't read " + file + ": " + e.getMessage() + ". It's left as it is.");
            return new LinkedHashMap<>();
        }
    }

    /**
     * When the database can't be reached at startup: what an earlier start kept while it was never reached goes into
     * memory now, so scripts see it, and from then on memory holds the truth for those names. The database's own
     * values replace them for names it has, when it loads.
     */
    private void loadUnloaded(VariableStore into) {
        Map<String, VariableFile.Entry> kept = readKept(unloadedFile, true);
        int count = 0;
        for (VariableFile.Entry entry : kept.values()) {
            String name = entry.name().toLowerCase(Locale.ROOT);
            if (entry.type().isEmpty() || into.get(name) != null) {
                continue;
            }
            into.set(name, codecs.decode(entry.type(), entry.bytes()));
            count++;
        }
        if (count > 0) {
            note("Loaded " + count + " variables kept for " + describe() + " while an earlier start couldn't reach it ("
                    + unloadedFile.getFileName() + "). They're written when it answers, where it doesn't have them.");
        }
    }

    /** A {@code # key: value} line from a kept file's header, or {@code null}. */
    private static String header(Path file, String key) throws IOException {
        try (java.io.BufferedReader in = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String line = in.readLine(); line != null && (line.startsWith("#") || line.isBlank());
                    line = in.readLine()) {
                String body = line.startsWith("# ") ? line.substring(2) : line.substring(Math.min(1, line.length()));
                if (body.startsWith(key)) {
                    return body.substring(key.length()).strip();
                }
            }
        }
        return null;
    }

    /** The entries of a kept file, a deletion as an entry with an empty type. */
    static Map<String, VariableFile.Entry> readWithDeletions(Path file) throws IOException {
        Map<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> fields = VariableFile.split(line);
            if (fields.size() != 3) {
                continue;
            }
            String name = fields.get(0);
            entries.remove(name);
            if (fields.get(1).isEmpty()) {
                entries.put(name, new VariableFile.Entry(name, "", new byte[0]));
            } else {
                entries.put(name, new VariableFile.Entry(name, fields.get(1),
                        java.util.HexFormat.of().parseHex(fields.get(2))));
            }
        }
        return entries;
    }

    private void applyPending(VariableStore into, Map<String, VariableFile.Entry> pending) {
        for (VariableFile.Entry entry : pending.values()) {
            into.set(entry.name(), entry.type().isEmpty() ? null : codecs.decode(entry.type(), entry.bytes()));
        }
        pendingApplied = true;
        note("Applied " + pending.size() + " changes that couldn't be written to " + describe() + " before the "
                + "server last stopped (kept in " + pendingFile.getFileName() + "); they're written now.");
    }

    // ---- The rest of VariableBackend -----------------------------------------------------------------------------

    @Override
    public boolean changed() {
        return loaded && (store.modifications() != writtenAt || pendingApplied);
    }

    /**
     * Writes what changed now, on the database's thread, and waits for it. If the database is down, this doesn't
     * wait for it: the changes are written when it's back, and this throws to say so.
     */
    @Override
    public VariableStorage.Result save() throws IOException {
        if (!loaded) {
            throw new IOException(describe() + " isn't loaded yet, so nothing is written to it");
        }
        Future<Boolean> done = thread.submit(() -> connection != null && write());
        try {
            if (!done.get(closeTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IOException(describe() + " is down (" + problem + "): " + waitingText().strip());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing to " + describe(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Couldn't write to " + describe() + ": " + message(e), e);
        }
        return lastResult;
    }

    /**
     * Copies SQLite's file to {@code backups/} with {@code VACUUM INTO}, on the database's thread, so the copy is a
     * whole, consistent database even while it's written to. It's written as the backup's name with {@code .tmp} after
     * and renamed once whole, so a crash never leaves a part of one that looks like a backup (the next start removes
     * it), and {@link #cancelBackup} interrupts it, so the server's stop doesn't wait behind a backup of a big file.
     */
    @Override
    public Path backupNow() throws IOException {
        if (settings.dialect() != Dialect.SQLITE) {
            return VariableBackend.super.backupNow();
        }
        if (backupCancelled) {
            return null;
        }
        Future<Path> done = thread.submit(() -> {
            if (connection == null) {
                throw new IOException(describe() + " is down (" + problem + ")");
            }
            if (backupCancelled) {
                return null;
            }
            return VariableStorage.backup(settings.file(), VariableStorage.SCHEDULED, this::vacuumInto);
        });
        try {
            return done.get(closeTimeoutMillis * 4, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while backing up " + describe(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Couldn't back up " + describe() + ": " + message(e), e);
        }
    }

    /** {@code VACUUM INTO} {@code target}'s temporary name, then renamed to it: on the database's thread. */
    private Path vacuumInto(Path target) throws IOException {
        if (Files.exists(target)) {
            throw new java.nio.file.FileAlreadyExistsException(target.toString());
        }
        Path temp = Durable.temporary(target);
        Files.deleteIfExists(temp);
        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try (PreparedStatement vacuum = connection.prepareStatement("VACUUM INTO ?")) {
                vacuum.setString(1, temp.toString());
                synchronized (backupLock) {
                    if (backupCancelled) {
                        throw new IOException("The backup of " + describe() + " was cut off");
                    }
                    backupStatement = vacuum;
                }
                try {
                    vacuum.execute();
                } finally {
                    synchronized (backupLock) {
                        backupStatement = null;
                    }
                }
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            if (backupCancelled) {
                throw new IOException("The backup of " + describe() + " was cut off");
            }
            Durable.force(temp);
            // Not REPLACE_EXISTING: a backup is never written over.
            Files.move(temp, target);
            Durable.syncDirectory(target.toAbsolutePath().getParent());
            return target;
        } catch (SQLException e) {
            Files.deleteIfExists(temp);
            throw new IOException(backupCancelled ? "The backup of " + describe() + " was cut off" : e.getMessage(), e);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /**
     * Interrupts a {@code VACUUM INTO} that's running and stops later ones. Only while one runs, under
     * {@link #backupLock}: SQLite's interrupt is the whole connection's, and must not reach the write after it.
     */
    @Override
    public void cancelBackup() {
        synchronized (backupLock) {
            backupCancelled = true;
            if (backupStatement != null) {
                try {
                    backupStatement.cancel();
                } catch (SQLException e) {
                    // Already done: there's nothing to cut off.
                }
            }
        }
    }

    private final Object backupLock = new Object();
    private volatile boolean backupCancelled;
    // Package-private and volatile for tests, which wait for a backup to be running.
    volatile PreparedStatement backupStatement;

    @Override
    public VariableStorage.Detached detach(ClassLoader loader) {
        return Databases.detach(store, codecs, loader, this::previous);
    }

    @Override
    public VariableFile.Entry previous(String name) {
        Saved before = saved.get(name.toLowerCase(Locale.ROOT));
        return before == null ? null : new VariableFile.Entry(name.toLowerCase(Locale.ROOT), before.type, before.bytes);
    }

    /**
     * Writes what's left and lets go of the connection. If that can't be done in time, what wasn't written is kept in
     * {@link #pendingFile} for the next start.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        // A backup running on the database's thread would hold up the last write behind it.
        cancelBackup();
        // A load still running (a late one, or one abandoned at startup) is cut off and never applied: what's in
        // memory is what's kept below.
        synchronized (this) {
            closing = true;
        }
        abort(connecting);
        Future<Boolean> last = thread.submit(() -> {
            if (!loaded) {
                return writePending(true);
            }
            if (connection == null) {
                reconnectOnce();
            }
            boolean ok = connection != null && write();
            if (!ok) {
                ok = writePending(false);
                if (ok && Files.exists(pendingFile)) {
                    console.error(describe() + " couldn't be written before the server stopped. The " + waiting
                            + " changes waiting are kept in " + pendingFile + " and written at the next start.");
                }
            }
            return ok;
        });
        boolean done;
        try {
            done = last.get(closeTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            done = false;
        } catch (ExecutionException | TimeoutException e) {
            done = false;
        }
        closed = true;
        state = State.CLOSED;
        if (!done) {
            // The thread is stuck on the database: abandon it, and keep what's waiting from this thread.
            // Not interrupted: a write it's in the middle of finishes or fails by itself, rather than failing
            // half-way with nothing to say why.
            last.cancel(false);
            abortConnection();
            if (loaded) {
                writePending(false);
                console.error(describe() + " didn't answer before the server stopped. What wasn't written is kept in "
                        + pendingFile + " and written at the next start.");
            } else {
                writePending(true);
            }
        }
        thread.shutdownNow();
        closeConnection();
    }

    /** One quick try, when the server stops while the database is down. */
    private void reconnectOnce() {
        try {
            connection = connect();
        } catch (SQLException | RuntimeException e) {
            connection = null;
        }
    }

    // ---- Connections ---------------------------------------------------------------------------------------------

    private Connection connect() throws SQLException {
        Driver driver = driver(settings.dialect());
        Properties properties = new Properties();
        String url;
        if (settings.dialect() == Dialect.SQLITE) {
            url = "jdbc:sqlite:" + settings.file().toAbsolutePath();
            // Another program holding the file waits this long before a write gives up and is retried.
            properties.setProperty("busy_timeout", "5000");
        } else {
            url = "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database();
            properties.setProperty("user", settings.user());
            properties.setProperty("password", settings.password());
            properties.setProperty("connectTimeout", "5000");
            properties.setProperty("socketTimeout", "60000");
            properties.setProperty("tcpKeepAlive", "true");
        }
        Properties extra = extraProperties;
        if (extra != null) {
            properties.putAll(extra);
        }
        Connection c = driver.connect(url, properties);
        if (c == null) {
            throw new SQLException("the " + settings.dialect() + " driver didn't take " + url);
        }
        try {
            c.setAutoCommit(false);
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    /** The JDBC driver Paper and Folia ship, used directly so no plugin like SQLibrary is needed. */
    static Driver driver(Dialect dialect) throws SQLException {
        try {
            Class<?> type = Class.forName(dialect.driver, true, SqlVariableStorage.class.getClassLoader());
            return (Driver) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new MissingDriver("this server doesn't have the " + dialect + " driver (" + dialect.driver + "), "
                    + "which Paper and Folia include");
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new SQLException("couldn't start the " + dialect + " driver: " + e, e);
        }
    }

    /** The driver isn't there: trying again won't help, so it isn't tried again. */
    static final class MissingDriver extends SQLException {
        MissingDriver(String message) {
            super(message);
        }
    }

    private void rollbackQuietly() {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            // The connection's going anyway.
        }
    }

    private void closeConnection() {
        Connection c = connection;
        connection = null;
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                // Gone already.
            }
        }
    }

    private void abortConnection() {
        abort(connection);
    }

    /** Cuts a connection off without waiting for the database, which may not answer. */
    private static void abort(Connection c) {
        if (c != null) {
            try {
                c.abort(Runnable::run);
            } catch (SQLException | RuntimeException e) {
                // Gone already.
            }
        }
    }

    /** A message for the console: the database's own words, never the password. */
    private String message(Throwable e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String text = cause.getMessage() == null ? cause.toString() : cause.getMessage().strip();
        text = text.lines().findFirst().orElse(text).strip();
        while (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        String password = settings.password();
        if (password != null && !password.isEmpty()) {
            text = text.replace(password, "****");
        }
        return text;
    }

    /** Values that can't change in place, so the same object means the same saved bytes. */
    private static boolean immutable(Object value) {
        return value instanceof String || value instanceof Long || value instanceof Double || value instanceof Boolean
                || value instanceof Integer || value instanceof Timespan || value instanceof RawValue;
    }
}
