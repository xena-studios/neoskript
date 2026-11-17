/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A database, as scripts have it ({@code the database "..."}): a JDBC URL and a pool of connections to it, which
 * opens when the first statement needs a connection, never while scripts load. One per URL, shared by every
 * script. A database nothing has used for a while closes its connections, and opens them again when it's next
 * used.
 */
public final class Database {

    private static final AtomicInteger POOLS = new AtomicInteger();

    private final String url;
    private final String shown;
    private final Settings settings;
    private final Drivers drivers;
    private final ThreadFactory threads;
    private final Object lock = new Object();
    private volatile Long maxLifetimeMillis;
    private volatile String driver;
    private volatile HikariDataSource pool;
    private volatile long lastUsed = System.nanoTime();
    /** Connections handed out and not closed yet, and transactions open on them. */
    private final AtomicInteger inUse = new AtomicInteger();
    private volatile boolean closed;

    Database(String url, Long maxLifetimeMillis, String driver, Settings settings, Drivers drivers,
            ThreadFactory threads) {
        this.url = url;
        this.shown = Secrets.mask(url);
        this.maxLifetimeMillis = maxLifetimeMillis;
        this.driver = driver;
        this.settings = settings;
        this.drivers = drivers;
        this.threads = threads;
    }

    /** The JDBC URL. Not for the console: it may have a password in it. */
    public String url() {
        return url;
    }

    /** The URL with its passwords as {@code ***}: how scripts and the console show it. */
    public String shown() {
        return shown;
    }

    /** The longest a connection is kept, from the script, or {@code null} for config.yml's. */
    public Long maxLifetimeMillis() {
        return maxLifetimeMillis;
    }

    /** The driver the script named, or {@code null}. */
    public String driver() {
        return driver;
    }

    /**
     * Takes a script's options for this URL, if the pool hasn't opened yet, as skript-db does: once it's open,
     * they're fixed until it closes.
     */
    void options(Long maxLifetimeMillis, String driver) {
        if (maxLifetimeMillis == null && driver == null) {
            return;
        }
        synchronized (lock) {
            if (pool == null) {
                if (maxLifetimeMillis != null) {
                    this.maxLifetimeMillis = maxLifetimeMillis;
                }
                if (driver != null) {
                    this.driver = driver;
                }
            }
        }
    }

    /**
     * A connection from the pool, opening it first if it isn't open. Close the lease to give it back.
     *
     * @return the connection, lent
     * @throws SQLException if none can be had: the database is down, the URL is wrong, or every connection stayed
     *     busy for {@code connection-timeout}
     */
    Lease lease() throws SQLException {
        for (int attempt = 0; ; attempt++) {
            HikariDataSource current = open();
            inUse.incrementAndGet();
            lastUsed = System.nanoTime();
            try {
                Lease lease = new Lease(current.getConnection(), this, new java.util.concurrent.atomic.AtomicBoolean());
                leases.add(lease);
                return lease;
            } catch (SQLException | RuntimeException e) {
                inUse.decrementAndGet();
                // Closed for being idle just now: once more, with a new pool.
                if (attempt == 0 && current.isClosed() && !closed) {
                    continue;
                }
                if (e instanceof SQLException sql) {
                    throw sql;
                }
                throw new SQLException(e.getMessage(), e);
            }
        }
    }

    /**
     * A connection lent by a database, given back when the lease is closed.
     *
     * @param connection the connection
     * @param database its database
     */
    record Lease(Connection connection, Database database, java.util.concurrent.atomic.AtomicBoolean returned)
            implements AutoCloseable {

        @Override
        public void close() throws SQLException {
            if (!returned.compareAndSet(false, true)) {
                return;
            }
            database.leases.remove(this);
            try {
                connection.close();
            } finally {
                database.inUse.decrementAndGet();
                database.lastUsed = System.nanoTime();
            }
        }

        /** Drops the connection without waiting for it, as NeoSkriptDb stops: whatever it was doing is lost. */
        void abort() {
            try {
                connection.abort(Runnable::run);
            } catch (SQLException | RuntimeException | LinkageError ignored) {
                // Closed below either way.
            }
            try {
                close();
            } catch (SQLException | RuntimeException ignored) {
                // Aborted already.
            }
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    /** The connections lent and not given back. */
    private final java.util.Set<Lease> leases = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Drops every connection still lent, as NeoSkriptDb stops after its grace. */
    void abortLeases() {
        for (Lease lease : java.util.List.copyOf(leases)) {
            lease.abort();
        }
    }

    private HikariDataSource open() throws SQLException {
        HikariDataSource current = pool;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            if (closed) {
                throw new SQLException("NeoSkriptDb is disabled");
            }
            if (pool == null) {
                try {
                    pool = create();
                } catch (RuntimeException | LinkageError e) {
                    throw new SQLException(Secrets.hide(e.getMessage(), url), e);
                }
            }
            return pool;
        }
    }

    private HikariDataSource create() {
        String chosen = driver != null ? driver : settings.driverClassName();
        Drivers.Resolution resolution = drivers.resolve(url, chosen);
        HikariDataSource created = new HikariDataSource();
        // The password goes to the pool on its own: HikariCP logs its URL when debugging, never its password.
        Secrets.Split split = Secrets.split(resolution.url());
        created.setJdbcUrl(split.url());
        if (split.user() != null) {
            created.setUsername(split.user());
        }
        if (split.password() != null) {
            created.setPassword(split.password());
        }
        if (resolution.driver() != null) {
            created.setDriverClassName(resolution.driver());
        }
        created.setMaximumPoolSize(settings.poolSize());
        // Idle connections close after a minute, down to none: a database scripts stop using holds nothing.
        created.setMinimumIdle(0);
        created.setIdleTimeout(60_000);
        Long lifetime = maxLifetimeMillis;
        created.setMaxLifetime(lifetime != null ? lifetime : settings.maxLifetimeMillis());
        created.setConnectionTimeout(settings.connectionTimeoutMillis());
        // A connection that can't be had fails the statement, rather than the pool, and the next one tries again.
        created.setInitializationFailTimeout(-1);
        created.setPoolName("NeoSkriptDb-" + POOLS.incrementAndGet());
        created.setThreadFactory(threads);
        return created;
    }

    /** Whether the pool is open. */
    boolean isOpen() {
        HikariDataSource current = pool;
        return current != null && !current.isClosed();
    }

    /**
     * Closes the pool if nothing has used it for {@code idleNanos} and no connection is out, so it holds no
     * connections or threads. It opens again when it's next used.
     *
     * @return whether it closed
     */
    boolean closeIfIdle(long idleNanos) {
        synchronized (lock) {
            HikariDataSource current = pool;
            if (current == null || inUse.get() > 0 || System.nanoTime() - lastUsed < idleNanos) {
                return false;
            }
            pool = null;
            current.close();
            return true;
        }
    }

    /** Closes the pool for good: connections still out are closed as they come back. */
    void close() {
        synchronized (lock) {
            closed = true;
            HikariDataSource current = pool;
            pool = null;
            if (current != null) {
                current.close();
            }
        }
    }

    /** How many connections are out. */
    int inUse() {
        return inUse.get();
    }

    @Override
    public String toString() {
        return shown;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Database database && database.url.equals(url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
