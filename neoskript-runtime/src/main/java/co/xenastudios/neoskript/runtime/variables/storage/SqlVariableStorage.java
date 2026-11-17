/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.Arithmetic;
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
 *
 * <p>Also with {@code monitor changes}, a write checks each row against what this server last saw of it, in the same
 * transaction (locking the rows, on MySQL). If another server wrote it since, the two changes are merged where they
 * can be, so neither is lost: numbers that were only added to or taken from here have this server's additions added
 * to the other server's value, and a value added to a list at an index another server filled first moves to the
 * list's next free index. Anything else is the last write's, as in Skript. An addon can hear of each write
 * ({@link #whenWritten}) and ask for other servers' changes at once ({@link #checkForChanges}), as neoskript-proxy
 * does to share variables across a network faster than {@code monitor interval}.
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

    /** The least time between two checks for other servers' changes that {@link #checkForChanges} asks for. */
    static volatile long checkGapMillis = 200;

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
     * @param merge with {@code monitor}, whether two servers' changes to one variable are merged where they can be
     *     ({@code merge-changes}, on by default); off, the last write wins, exactly as in Skript
     */
    public record Settings(String name, Dialect dialect, Path file, String host, int port, String database,
            String user, String password, String table, boolean monitor, long monitorMillis, boolean merge) {

        public Settings {
            if (table == null || !TABLE_NAME.matcher(table).matches()) {
                throw new IllegalArgumentException("table '" + table + "' isn't a table name (letters, digits and _)");
            }
        }

        public static Settings sqlite(String name, Path file, String table, boolean monitor, long monitorMillis) {
            return new Settings(name, Dialect.SQLITE, file, null, 0, null, null, null, table, monitor, monitorMillis,
                    true);
        }

        public static Settings mysql(String name, String host, int port, String database, String user, String password,
                String table, boolean monitor, long monitorMillis) {
            return new Settings(name, Dialect.MYSQL, null, host, port, database, user, password, table, monitor,
                    monitorMillis, true);
        }

        /** These settings with {@code merge-changes} as given. */
        public Settings withMerge(boolean merge) {
            return new Settings(name, dialect, file, host, port, database, user, password, table, monitor,
                    monitorMillis, merge);
        }

        /** Whether two servers' changes are merged: monitored, and {@code merge-changes} on. */
        public boolean merging() {
            return monitor && merge;
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
        /**
         * With merging: the change mark ({@link #sequence}) as of when this server's value was last this row's: a set
         * marked after it was made after, so the variable has changed in more than additions since.
         */
        volatile long since;

        Saved(Object value, String type, byte[] bytes, String guid) {
            this.value = value;
            this.type = type;
            this.bytes = bytes;
            this.guid = guid;
        }
    }

    /**
     * One change to write: a value, or a deletion ({@code type} {@code null}).
     *
     * @param base what this server last saw of the row, which the change was made from, or {@code null}
     * @param value the value written, as it was in memory
     * @param added with monitor changes, what scripts had added to the number since {@code base} as this round
     *     found it, or {@code null}
     * @param additive whether the change was only {@code added}, so it can be merged with another server's
     * @param appended with monitor changes, the mark of the variable having been made by adding to its list, or
     *     {@code null}
     * @param readAt the change mark ({@link #sequence}) as the value was read: changes marked after it aren't in it
     * @param shift for a merged write, what it adds to the value in memory once it has committed, or {@code null}
     */
    private record Op(String name, String type, byte[] bytes, Saved saved, Saved base, Object value, Object added,
            boolean additive, Long appended, long readAt, Object shift) {

        static Op deletion(String name, Saved base, Object added, Long appended, long readAt) {
            return new Op(name, null, null, null, base, null, added, false, appended, readAt, null);
        }

        Op merged(String type, byte[] bytes, Saved saved, Object value, Object shift) {
            return new Op(name, type, bytes, saved, base, value, added, additive, appended, readAt, shift);
        }

        /** Whether it would write what {@code row} ({@link #currentRows}) holds. */
        boolean wrote(Object[] row) {
            if (row == null || row[0] == null) {
                return type == null;
            }
            return type != null && type.equals(row[0]) && Arrays.equals(bytes, (byte[]) row[1]);
        }
    }

    /**
     * What a write's transaction does, worked out in it ({@link #resolve}): what to write; what to change in memory
     * once it has committed; the variables left for the next round (their base moved, as when an earlier write whose
     * answer was lost turned out to have committed); and how many rows met another server's write, and merged.
     */
    private static final class Resolution {
        final List<Op> write = new ArrayList<>();
        final List<Runnable> after = new ArrayList<>();
        final java.util.Set<String> skipped = new java.util.HashSet<>();
        int conflicts;
        int merges;

        static Resolution plain(List<Op> ops) {
            Resolution resolution = new Resolution();
            resolution.write.addAll(ops);
            return resolution;
        }

        void add(Resolution other) {
            write.addAll(other.write);
            after.addAll(other.after);
            skipped.addAll(other.skipped);
            conflicts += other.conflicts;
            merges += other.merges;
        }
    }

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
    /**
     * With monitor changes: what scripts added to each number since it was last written, by name, so a write can
     * merge it with another server's change. As many names at most as changed since the last write.
     */
    private final Map<String, Object> added = new ConcurrentHashMap<>();
    /**
     * With merging: changes under way, by name (or {@code list::*} for a whole list), so a write never reads one half
     * done, and other servers' values are never put over one.
     */
    private final Map<String, Integer> inFlight = new ConcurrentHashMap<>();
    /**
     * With merging: the last change other than an addition, by name (or {@code list::*}), as a mark from
     * {@link #sequence}, made before the change itself. A change marked after its base ({@link Saved#since}) means
     * the variable changed in more than additions, so it isn't merged. Let go of once written.
     */
    private final Map<String, Long> replaced = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    /** Set while this database's own thread changes the store (another server's value, a merge): not marked. */
    private final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);
    /**
     * Writes sent but not known to have committed, by name: if the database's answer was lost, a later write finds
     * one of these in the row, with this server's update_guid, and takes it as written rather than as another
     * server's. Only touched on the database's thread.
     */
    private final Map<String, List<Op>> attempts = new java.util.HashMap<>();
    /** For tests: the next commit's answer is lost, as if the connection dropped just after the database committed. */
    volatile boolean loseNextReply;
    /** For tests: runs just after a write commits, before memory is changed to match. */
    volatile Runnable afterCommit;
    /** For tests: runs as monitoring is about to put another server's value in memory, with the variable's name. */
    volatile java.util.function.Consumer<String> beforeApplying;
    /**
     * With monitor changes: the variables made by adding to their list since they were last written, with when, so
     * a mark whose variable is gone again can be let go of.
     */
    private final Map<String, Long> appended = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong marks = new java.util.concurrent.atomic.AtomicLong();
    private final Runnable unwatch;
    private final List<Runnable> writeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean checkQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long lastCheckAt;
    /** Write rounds' rows that met another server's change since this start, merged or not: for /sk info and tests. */
    private final java.util.concurrent.atomic.AtomicLong conflicts = new java.util.concurrent.atomic.AtomicLong();
    /** Changes merged with another server's since this start. */
    private final java.util.concurrent.atomic.AtomicLong merges = new java.util.concurrent.atomic.AtomicLong();

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
        this.unwatch = settings.merging() ? store.watch(new VariableStore.Changes() {
            @Override
            public void changing(String name, boolean adding) {
                if (applying.get()) {
                    return;
                }
                String key = key(name);
                if (key == null) {
                    return;
                }
                inFlight.merge(key, 1, Integer::sum);
                if (!adding) {
                    // Marked before the change: whoever sees the new value sees the mark too.
                    replaced.put(key, sequence.incrementAndGet());
                }
            }

            @Override
            public void changed(String name, boolean adding, Number amount) {
                if (applying.get()) {
                    return;
                }
                String key = key(name);
                if (key == null) {
                    return;
                }
                if (adding) {
                    if (amount != null) {
                        added.merge(key, amount, Arithmetic::add);
                    } else {
                        // add or remove that turned out not to be numbers: a change like a set.
                        replaced.put(key, sequence.incrementAndGet());
                    }
                }
                inFlight.computeIfPresent(key, (k, count) -> count <= 1 ? null : count - 1);
            }

            @Override
            public void appending(String name) {
                String key = key(name);
                if (key != null) {
                    // A new mark each time, so letting go of an older one never takes a newer one with it.
                    appended.put(key, (System.currentTimeMillis() << 20) | (marks.incrementAndGet() & 0xFFFFF));
                }
            }

            /** The name as kept, or {@code null} if this database doesn't save it. */
            private String key(String name) {
                String key = name.toLowerCase(Locale.ROOT);
                if (key.startsWith("-")) {
                    return null;
                }
                // A whole list's change matters to whichever of its variables this database saves.
                return key.endsWith("::*") || routing.owns().test(key) ? key : null;
            }
        }) : () -> {};
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

    /**
     * Which database this is, the same on every server that uses it: a hash of its type, where it is and its table,
     * never the user or password. Servers sharing variables tell each other about writes by it.
     */
    public String id() {
        return hash(identity);
    }

    /** How many of this start's writes met a row another server had written since, and how many of those merged. */
    public long[] conflicts() {
        return new long[] {conflicts.get(), merges.get()};
    }

    /**
     * Runs {@code listener} after each write that changed rows, on the database's thread: it must be quick.
     *
     * @return what stops it
     */
    public Runnable whenWritten(Runnable listener) {
        writeListeners.add(listener);
        return () -> writeListeners.remove(listener);
    }

    /**
     * With monitor changes, reads other servers' changes as soon as it can, instead of at the next
     * {@code monitor interval}: when another server says it wrote. Calls close together are one check, and checks are
     * at least {@link #checkGapMillis} apart. Does nothing without monitor changes, or while the database is down: it's
     * read when it's back.
     */
    public void checkForChanges() {
        if (!settings.monitor() || closed || !checkQueued.compareAndSet(false, true)) {
            return;
        }
        long wait = Math.max(0, lastCheckAt + checkGapMillis - System.currentTimeMillis());
        try {
            thread.schedule(() -> {
                checkQueued.set(false);
                if (closed || !loaded || connection == null) {
                    return;
                }
                try {
                    monitor();
                    lastCheckAt = System.currentTimeMillis();
                    nextMonitorAt = lastCheckAt + settings.monitorMillis();
                } catch (RuntimeException | LinkageError e) {
                    console.error("Unexpected problem with " + describe() + ": " + e);
                }
            }, wait, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Closed meanwhile.
            checkQueued.set(false);
        }
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
                lastCheckAt = System.currentTimeMillis();
                nextMonitorAt = lastCheckAt + settings.monitorMillis();
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
            // What other servers wrote meanwhile is read at once: their notices (checkForChanges) went unheard.
            nextMonitorAt = 0;
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
        return changes(current, kept, notSaved, counts, new Object[3]);
    }

    /**
     * {@link #changes}, and in {@code marks} what a write needs to let go of the additions and appends it's done with
     * ({@link #letGo}).
     */
    private List<Op> changes(long current, List<String> kept, List<String> notSaved, int[] counts, Object[] marks) {
        List<Op> ops = new ArrayList<>();
        java.util.Set<String> upserted = new java.util.HashSet<>();
        boolean merging = settings.merging();
        // Names whose changes this round read whole but had nothing to write for, with what they'd added; names left
        // for the next round because a change was under way.
        Map<String, Object> unwritten = new java.util.HashMap<>();
        java.util.Set<String> deferred = new java.util.HashSet<>();
        java.util.Set<String> visited = merging ? new java.util.HashSet<>() : null;
        marks[0] = unwritten;
        marks[1] = deferred;
        marks[2] = visited;
        store.forEach((variable, read) -> {
            if (variable.startsWith("-")) {
                return;
            }
            String name = variable.toLowerCase(Locale.ROOT);
            Saved last = saved.get(name);
            Object value = read;
            Object sum = null;
            long readAt = 0;
            long mark = 0;
            if (merging) {
                visited.add(name);
                // The value, what was added to it and its marks, read so they agree: taken before the value, the mark
                // counter makes every change marked up to it either done (no change under way) or later; and nothing
                // changed between the reads either side of the value. Otherwise it waits for the next round.
                readAt = sequence.get();
                if (inFlight(name)) {
                    defer(name, last, current, deferred);
                    return;
                }
                sum = added.get(name);
                mark = markOf(name);
                value = store.get(variable);
                if (value == null || inFlight(name) || !java.util.Objects.equals(sum, added.get(name))
                        || mark != markOf(name)) {
                    defer(name, last, current, deferred);
                    return;
                }
                if (sum != null) {
                    unwritten.put(name, sum);
                }
            }
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
                reaffirm(name, last, readAt, merging);
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
                reaffirm(name, last, readAt, merging);
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
            next.since = readAt;
            upserted.add(name);
            unwritten.remove(name);
            // Changed only by additions since the base: no change marked since it, and the numbers agree.
            boolean onlyAdditions = merging && mark <= (last == null ? 0 : last.since);
            // Read after the value: an append is marked before its value is there.
            Long appendMark = merging && onlyAdditions ? appended.get(name) : null;
            ops.add(new Op(name, encoded.type(), encoded.bytes(), next, last, value, sum,
                    onlyAdditions && sum != null && onlyAdded(last, value, sum), appendMark, readAt, null));
        });
        for (Map.Entry<String, Saved> entry : saved.entrySet()) {
            if (entry.getValue().seen != current && !upserted.contains(entry.getKey())) {
                String name = entry.getKey();
                Object sum = merging ? added.get(name) : null;
                ops.add(Op.deletion(name, entry.getValue(), sum, merging ? appended.get(name) : null,
                        sequence.get()));
                unwritten.remove(name);
            }
        }
        return ops;
    }

    /** Leaves {@code name} for the next round: unchanged as far as this one goes, so not deleted either. */
    private static void defer(String name, Saved last, long current, java.util.Set<String> deferred) {
        if (last != null) {
            last.seen = current;
        }
        deferred.add(name);
    }

    /**
     * A variable whose value is still its base's, read whole at {@code readAt}: what it went through since is in that
     * value, so its base holds as of then, and a set marked before then no longer matters.
     */
    private void reaffirm(String name, Saved last, long readAt, boolean merging) {
        if (!merging) {
            return;
        }
        last.since = Math.max(last.since, readAt);
        replaced.computeIfPresent(name, (k, mark) -> mark <= readAt ? null : mark);
    }

    /**
     * The keys a change to {@code name} is told under: the name, and each list it's in, as {@code list::*}.
     */
    private static List<String> keysOf(String name) {
        List<String> keys = new ArrayList<>(4);
        keys.add(name);
        for (int at = name.indexOf("::"); at >= 0; at = name.indexOf("::", at + 2)) {
            keys.add(name.substring(0, at) + "::*");
        }
        return keys;
    }

    /** Whether a change to {@code name}, or to a list it's in, is under way. */
    private boolean inFlight(String name) {
        if (inFlight.isEmpty()) {
            return false;
        }
        for (String key : keysOf(name)) {
            if (inFlight.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** The latest mark of a change other than an addition to {@code name}, or to a list it's in; 0 if none. */
    private long markOf(String name) {
        if (replaced.isEmpty()) {
            return 0;
        }
        long mark = 0;
        for (String key : keysOf(name)) {
            Long found = replaced.get(key);
            if (found != null && found > mark) {
                mark = found;
            }
        }
        return mark;
    }

    /**
     * Whether {@code value} is {@code base}'s value with {@code sum} added: a check, on top of the marks, that the
     * numbers agree.
     */
    private static boolean onlyAdded(Saved base, Object value, Object sum) {
        if (!plainNumber(value) || !plainNumber(sum)) {
            return false;
        }
        Object difference;
        if (base == null) {
            difference = value;
        } else if (plainNumber(base.value)) {
            difference = Arithmetic.subtract(value, base.value);
        } else {
            return false;
        }
        return difference != null && sameNumber(difference, sum);
    }

    static boolean plainNumber(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    /** Equal numbers: exactly, for whole ones, or to within a rounding error for decimals. */
    static boolean sameNumber(Object a, Object b) {
        if (a instanceof Long x && b instanceof Long y) {
            return x.longValue() == y.longValue();
        }
        double x = ((Number) a).doubleValue();
        double y = ((Number) b).doubleValue();
        return Math.abs(x - y) <= 1e-9 * Math.max(1, Math.max(Math.abs(x), Math.abs(y)));
    }

    private void refuse(String name, byte[] bytes, String why) {
        refused.put(name, bytes);
        console.error("Couldn't save {" + name + "} to " + describe() + ": " + why + ". It keeps its value until the "
                + "server stops, but isn't saved.");
    }

    /** How many times a write that deadlocks with another server's is tried at once before waiting for later. */
    private static final int BUSY_TRIES = 3;

    /**
     * Writes what changed in one transaction. Returns whether it went through; if not, the database is marked down
     * and the same changes are worked out again next time. With merging, rows another server wrote since this one
     * last saw them are merged where they can be ({@link #resolve}); a write that deadlocks with another server's is
     * tried again at once, a few times, and then a little later, without counting as the database being down.
     */
    private boolean write() {
        if (!loaded || connection == null) {
            return false;
        }
        long start = System.nanoTime();
        long modifications = store.modifications();
        long current = ++round;
        long roundStart = sequence.get();
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        int[] counts = new int[2];
        Object[] found = new Object[3];
        List<Op> ops = changes(current, kept, notSaved, counts, found);
        lastResult = new VariableStorage.Result(counts[0], 0, counts[1], List.of(), null, 0, List.copyOf(kept),
                List.copyOf(notSaved));
        if (ops.isEmpty()) {
            letGo(List.of(), java.util.Set.of(), found, roundStart, true);
            finishWrite(modifications, start);
            return true;
        }
        waiting = ops.size();
        Resolution done = null;
        // Of ops, those whose round is over: written, or refused by the database.
        List<Op> finished = ops;
        boolean whole = true;
        try {
            for (int attempt = 1; done == null; attempt++) {
                try {
                    Resolution resolution = resolution(ops);
                    commit(resolution.write);
                    done = resolution;
                } catch (SQLException e) {
                    rollbackQuietly();
                    if (settings.merging() && busy(e)) {
                        if (attempt < BUSY_TRIES) {
                            continue;
                        }
                        laterBecauseBusy();
                        return false;
                    }
                    if (!isBadRow(e)) {
                        throw e;
                    }
                    // One of the rows: write them one by one, and leave out the ones the database refuses.
                    done = new Resolution();
                    finished = new ArrayList<>();
                    for (Op op : ops) {
                        Boolean wrote = writeAlone(op, done);
                        if (wrote == null) {
                            // Held by another server's write past the tries: the rest waits for the next round.
                            whole = false;
                            break;
                        }
                        finished.add(op);
                    }
                }
            }
        } catch (SQLException e) {
            closeConnection();
            down(e, false);
            return false;
        }
        for (Op op : done.write) {
            record(op);
        }
        applying.set(true);
        try {
            for (Runnable change : done.after) {
                change.run();
            }
        } finally {
            applying.set(false);
        }
        conflicts.addAndGet(done.conflicts);
        merges.addAndGet(done.merges);
        List<Op> letGoOf = new ArrayList<>(finished.size());
        for (Op op : finished) {
            if (!done.skipped.contains(op.name())) {
                letGoOf.add(op);
            }
        }
        letGo(letGoOf, done.skipped, found, roundStart, whole);
        if (!whole) {
            laterBecauseBusy();
            return false;
        }
        finishWrite(modifications, start);
        for (Runnable listener : writeListeners) {
            try {
                listener.run();
            } catch (RuntimeException | LinkageError e) {
                console.error("A listener for writes to " + describe() + " failed: " + e);
            }
        }
        return true;
    }

    /** What to write for {@code ops}: with merging, worked out against the rows ({@link #resolve}). */
    private Resolution resolution(List<Op> ops) throws SQLException {
        return settings.merging() ? resolve(ops) : Resolution.plain(ops);
    }

    /**
     * Writes and commits, remembering what was sent until the commit is known, so a lost answer can be told from
     * another server's write ({@link #attempts}).
     */
    private void commit(List<Op> write) throws SQLException {
        if (settings.merging()) {
            for (Op op : write) {
                List<Op> sent = attempts.computeIfAbsent(op.name(), k -> new ArrayList<>(1));
                if (sent.size() >= 8) {
                    sent.removeFirst();
                }
                sent.add(op);
            }
        }
        apply(write);
        connection.commit();
        Runnable hook = afterCommit;
        if (hook != null) {
            hook.run();
        }
        if (loseNextReply) {
            loseNextReply = false;
            throw new SQLException("the connection dropped before the database answered", "08S01");
        }
        for (Op op : write) {
            attempts.remove(op.name());
        }
    }

    /**
     * Writes one op on its own, after a batch hit a row the database refused, into {@code into}. Returns whether it was
     * written ({@code false} if refused), or {@code null} if another server's write held it past the tries.
     */
    private Boolean writeAlone(Op op, Resolution into) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            try {
                Resolution one = resolution(List.of(op));
                commit(one.write);
                into.add(one);
                return true;
            } catch (SQLException failed) {
                rollbackQuietly();
                if (settings.merging() && busy(failed)) {
                    if (attempt < BUSY_TRIES) {
                        continue;
                    }
                    return null;
                }
                if (!isBadRow(failed)) {
                    throw failed;
                }
                refuse(op.name(), op.bytes() == null ? new byte[0] : op.bytes(), message(failed));
                return false;
            }
        }
    }

    /** Another server's write holds the rows: next time, a little later. */
    private void laterBecauseBusy() {
        nextWriteAt = System.currentTimeMillis() + 100 + java.util.concurrent.ThreadLocalRandom.current().nextLong(400);
    }

    /**
     * Whether the database turned a write away because another server's held the same rows (a deadlock, or a lock
     * that wasn't let go of in time), so it's worth trying again at once rather than counting as down. On SQLite, a
     * write reads its rows first ({@link #resolve}), and two connections that each read and then want to write stop
     * each other: one is told the file is busy straight away.
     */
    static boolean busy(SQLException e) {
        if ("40001".equals(e.getSQLState())) {
            return true;
        }
        String driver = e.getClass().getName();
        if (driver.startsWith("com.mysql")) {
            return e.getErrorCode() == 1213 || e.getErrorCode() == 1205;
        }
        return driver.startsWith("org.sqlite") && (e.getErrorCode() & 0xFF) == 5;
    }

    /**
     * With merging, in the write's transaction: each row as it is now (locked, on MySQL, until the write commits),
     * against what this server last saw of it.
     *
     * <ul>
     *   <li>A row holding this server's own write that it never heard had committed (the answer was lost) is taken
     *       as written: memory catches up with it, and the variable waits for the next round.
     *   <li>A row another server wrote since is merged with if it can be: a number only added to here gets this
     *       server's additions added to the other's value, and a value added to a list at an index the other server
     *       filled first leaves it theirs and is added to the list again, at the next free index, by the next write.
     *   <li>Otherwise this server's change is written over it, as in Skript.
     * </ul>
     */
    private Resolution resolve(List<Op> ops) throws SQLException {
        Map<String, Object[]> rows = currentRows(ops);
        Resolution resolution = new Resolution();
        for (Op op : ops) {
            String name = op.name();
            if (saved.get(name) != op.base()) {
                // Its base moved since the round read it (a lost answer's write, caught up with below in an earlier
                // try): worked out again next round.
                resolution.skipped.add(name);
                continue;
            }
            Object[] row = rows.get(name);
            if (sameRow(op.base(), row)) {
                resolution.write.add(op);
                continue;
            }
            Op landed = landed(name, row);
            if (landed != null) {
                // Committed after all. Memory catches up now, whatever this transaction does: it's in the database.
                landed(landed);
                resolution.skipped.add(name);
                continue;
            }
            resolution.conflicts++;
            Object theirs = row == null || row[0] == null ? null : codecs.decode((String) row[0], (byte[]) row[1]);
            if (op.additive() && (theirs == null || plainNumber(theirs))) {
                Object merged = theirs == null ? op.added() : Arithmetic.add(theirs, op.added());
                VariableCodecs.Encoded encoded = encodeQuietly(merged);
                Object shift = merged == null ? null : Arithmetic.subtract(merged, op.value());
                if (encoded != null && shift != null) {
                    Saved next = new Saved(merged, encoded.type(), encoded.bytes(), guid);
                    next.seen = op.saved().seen;
                    next.since = op.readAt();
                    Op write = op.merged(encoded.type(), encoded.bytes(), next, merged, shift);
                    resolution.write.add(write);
                    resolution.after.add(() -> shift(write));
                    resolution.merges++;
                    continue;
                }
            }
            if (op.appended() != null && op.base() == null && op.type() != null && theirs != null
                    && name.contains("::")) {
                Saved kept = new Saved(theirs, (String) row[0], (byte[]) row[1], (String) row[2]);
                kept.seen = op.saved().seen;
                kept.since = op.readAt();
                Object mine = op.value();
                resolution.after.add(() -> {
                    saved.put(name, kept);
                    boolean[] moved = {false};
                    store.update(name, now -> {
                        moved[0] = now == mine;
                        return moved[0] ? theirs : now;
                    });
                    if (moved[0]) {
                        // Marked as an append, so the next write moves it again if it meets another server's.
                        store.addToList(name.substring(0, name.lastIndexOf("::")), new Object[] {mine});
                    }
                });
                resolution.merges++;
                continue;
            }
            resolution.write.add(op);
        }
        return resolution;
    }

    /**
     * The write of this server's that {@code row} holds, if it's one whose commit this server never heard of: the row
     * has this server's update_guid and the bytes of a write it sent.
     */
    private Op landed(String name, Object[] row) {
        if (row == null || !guid.equals(row[2])) {
            return null;
        }
        List<Op> sent = attempts.get(name);
        if (sent == null) {
            return null;
        }
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).wrote(row)) {
                return sent.get(i);
            }
        }
        return null;
    }

    /** A write found committed after its answer was lost: memory catches up with it, as if it had been answered. */
    private void landed(Op op) {
        attempts.remove(op.name());
        record(op);
        if (op.shift() != null) {
            applying.set(true);
            try {
                shift(op);
            } finally {
                applying.set(false);
            }
        }
        letGoOf(op);
    }

    /**
     * After a merged write commits: what it added to the value in memory, so the value is the merged one plus whatever
     * scripts added since the round read it. Not if anything else changed it since (a set, marked before it lands):
     * that's newer, and the next write's, as the last write.
     */
    private void shift(Op op) {
        String name = op.name();
        store.update(name, now -> plainNumber(now) && markOf(name) <= op.readAt() ? Arithmetic.add(now, op.shift())
                : now);
    }

    private VariableCodecs.Encoded encodeQuietly(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return codecs.encode(value);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Whether a row, as {@link #currentRows} gives it, is still what {@code base} says this server last saw. */
    private static boolean sameRow(Saved base, Object[] row) {
        if (row == null || row[0] == null) {
            return base == null;
        }
        return base != null && base.type.equals(row[0]) && Arrays.equals(base.bytes, (byte[]) row[1]);
    }

    /**
     * The rows of {@code ops}' variables as they are now, by name in lower case: {type, bytes, update_guid}, a
     * deletion's with no type or bytes, and no entry for a name without a row. On MySQL they're locked until the
     * transaction ends, a name without a row included (REPEATABLE READ's gap locks), so two servers can't both write a
     * variable each thinks is new.
     */
    private Map<String, Object[]> currentRows(List<Op> ops) throws SQLException {
        Map<String, Object[]> rows = new java.util.HashMap<>();
        for (int from = 0; from < ops.size(); from += BATCH) {
            List<Op> part = ops.subList(from, Math.min(ops.size(), from + BATCH));
            String query = "SELECT name, type, value, update_guid FROM " + settings.table() + " WHERE name IN ("
                    + String.join(", ", java.util.Collections.nCopies(part.size(), "?")) + ")"
                    + (settings.dialect() == Dialect.MYSQL ? " FOR UPDATE" : "");
            try (PreparedStatement select = connection.prepareStatement(query)) {
                for (int i = 0; i < part.size(); i++) {
                    select.setString(i + 1, part.get(i).name());
                }
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        String name = result.getString(1);
                        String type = result.getString(2);
                        byte[] bytes = result.getBytes(3);
                        if (name != null) {
                            rows.put(name.toLowerCase(Locale.ROOT), type == null || bytes == null
                                    ? new Object[] {null, null, result.getString(4)}
                                    : new Object[] {type, bytes, result.getString(4)});
                        }
                    }
                }
            }
        }
        return rows;
    }

    /** Lets go of what one written op took in: its additions, its append mark, and the sets marked before it. */
    private void letGoOf(Op op) {
        if (op.added() != null) {
            Object taken = op.added();
            added.computeIfPresent(op.name(), (name, now) -> {
                Object rest = Arithmetic.subtract(now, taken);
                return rest == null || sameNumber(rest, 0L) ? null : rest;
            });
        }
        if (op.appended() != null) {
            appended.remove(op.name(), op.appended());
        }
        long readAt = op.readAt();
        replaced.computeIfPresent(op.name(), (name, mark) -> mark <= readAt ? null : mark);
    }

    /**
     * After a write: lets go of what the ops it finished took in ({@link #letGoOf}). If the whole round went through,
     * also of additions it read whole for a variable that had nothing to write, unless more came since; of additions
     * and marks for variables that are gone (append marks a while after: an append is marked just before its value is
     * there); and of marks of changes to whole lists made before the round, unless a variable in the list was left for
     * the next round.
     */
    @SuppressWarnings("unchecked")
    private void letGo(List<Op> ops, java.util.Set<String> skipped, Object[] found, long roundStart, boolean whole) {
        if (!settings.merging() || found[0] == null) {
            return;
        }
        java.util.Set<String> written = new java.util.HashSet<>(skipped);
        for (Op op : ops) {
            written.add(op.name());
            letGoOf(op);
        }
        if (!whole) {
            return;
        }
        Map<String, Object> unwritten = (Map<String, Object>) found[0];
        java.util.Set<String> deferred = (java.util.Set<String>) found[1];
        java.util.Set<String> visited = (java.util.Set<String>) found[2];
        unwritten.forEach((name, sum) -> {
            if (!skipped.contains(name)) {
                added.remove(name, sum);
            }
        });
        for (String name : List.copyOf(added.keySet())) {
            if (!visited.contains(name) && !written.contains(name) && !deferred.contains(name)
                    && !inFlight(name) && store.get(name) == null) {
                Object sum = added.get(name);
                if (sum != null && !inFlight(name)) {
                    added.remove(name, sum);
                }
            }
        }
        for (Map.Entry<String, Long> entry : List.copyOf(replaced.entrySet())) {
            String key = entry.getKey();
            if (entry.getValue() >= roundStart || inFlight.containsKey(key)) {
                continue;
            }
            if (key.endsWith("::*")) {
                String under = key.substring(0, key.length() - 1);
                boolean waiting = false;
                for (String name : deferred) {
                    waiting |= name.startsWith(under);
                }
                for (String name : skipped) {
                    waiting |= name.startsWith(under);
                }
                if (!waiting) {
                    replaced.remove(key, entry.getValue());
                }
            } else if (!visited.contains(key) && !written.contains(key) && !deferred.contains(key)
                    && store.get(key) == null && !saved.containsKey(key)) {
                replaced.remove(key, entry.getValue());
            }
        }
        long old = (System.currentTimeMillis() - 60_000) << 20;
        appended.entrySet().removeIf(entry -> entry.getValue() < old && !written.contains(entry.getKey())
                && store.get(entry.getKey()) == null);
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
        // Rows left alone for a change here not written yet are read again next time, in case that change turns out
        // to need no write (additions that came to nothing): then the row is put in memory after all.
        long readAgainFrom = Long.MAX_VALUE;
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
                        Object local = store.get(name);
                        if (dirty(key, name, local) || pendingHere(key)) {
                            // Changed here since the last write, which hasn't happened yet: the local change is newer,
                            // and is written over this row (or merged with it), as Skript would have written it at once.
                            seen.remove(row);
                            readAgainFrom = Math.min(readAgainFrom, row - 1);
                            continue;
                        }
                        Object value = bytes == null || type == null ? null : codecs.decode(type, bytes);
                        java.util.function.Consumer<String> hook = beforeApplying;
                        if (hook != null) {
                            hook.accept(name);
                        }
                        // Only over the value just checked, and with no change under way: a change a script makes
                        // in between is newer, stays, and is written (or merged) next.
                        long since = sequence.get();
                        boolean[] put = {false};
                        applying.set(true);
                        try {
                            store.update(name, now -> {
                                put[0] = now == local && !inFlight(key);
                                return put[0] ? value : now;
                            });
                        } finally {
                            applying.set(false);
                        }
                        if (!put[0]) {
                            seen.remove(row);
                            readAgainFrom = Math.min(readAgainFrom, row - 1);
                            continue;
                        }
                        if (value == null) {
                            saved.remove(key);
                        } else {
                            Saved theirs = new Saved(value, type, bytes, rows.getString(5));
                            theirs.since = since;
                            saved.put(key, theirs);
                        }
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
        recheckFrom = Math.min(lastRowId, readAgainFrom);
        lastRowId = max;
        // Deletions other servers have seen by now are tidied away, as Skript does: twice the interval, plus five
        // seconds, after the check.
        cleanups.add(new long[] {System.currentTimeMillis() + 2 * settings.monitorMillis() + 5_000, from});
    }

    /**
     * With merging: whether a change here isn't written yet though the value may look unchanged (additions that came
     * to nothing, a set back to the same value), or one is under way.
     */
    private boolean pendingHere(String key) {
        if (!settings.merging()) {
            return false;
        }
        Saved last = saved.get(key);
        Long mark = replaced.get(key);
        return added.containsKey(key) || inFlight(key) || (mark != null && mark > (last == null ? 0 : last.since));
    }

    /** Whether {@code current}, {@code name}'s value in memory, differs from what this database last wrote or read. */
    private boolean dirty(String key, String name, Object current) {
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
        unwatch.run();
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
            if (settings.dialect() == Dialect.MYSQL) {
                // A write locks the rows it reads (resolve), names without a row included: only REPEATABLE READ's
                // gap locks do that, so two servers can't both write a name each thinks is new. It's InnoDB's default,
                // but a server can be set otherwise.
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try (Statement session = c.createStatement()) {
                    // A write held by another server's gives up after this many seconds rather than InnoDB's 50, and is
                    // tried again (busy), so the database's thread is never tied up for long.
                    session.execute("SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_SECONDS);
                } catch (SQLException notInnoDb) {
                    // Not InnoDB, or not allowed: the server's own wait stands.
                }
                c.commit();
            }
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    /** How long a MySQL write waits for another server's locks before it's tried again. */
    static final int LOCK_WAIT_SECONDS = 5;

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
