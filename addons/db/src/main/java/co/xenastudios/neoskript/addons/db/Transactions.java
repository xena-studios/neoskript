/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * The transactions scripts have open ({@code transaction on {db}:}), by trigger and database, as skript-db keeps
 * them. A transaction ends at the end of its section (committed), when a statement in it fails (rolled back, and the
 * rest of the section skipped), or when its trigger leaves the section another way (rolled back at once:
 * {@link co.xenastudios.neoskript.api.element.PausingSection#abandoned}). A trigger waiting inside its section keeps
 * it open: unlike skript-db, there's no idle timeout that rolls back under a trigger that's still going, after which
 * the rest of its statements would commit on their own. One rolled back from outside (as NeoSkriptDb stops) is kept
 * as broken until its section ends, so the trigger's later statements fail with the reason.
 */
final class Transactions {

    private final Map<Object, Map<Database, Transaction>> scopes = new IdentityHashMap<>();
    private final long warnAfterNanos;
    private final Logger logger;

    /**
     * @param warnAfterNanos how long a transaction may stay open before the console says so, once
     * @param logger the console
     */
    Transactions(long warnAfterNanos, Logger logger) {
        this.warnAfterNanos = warnAfterNanos;
        this.logger = logger;
    }

    /** One transaction: a connection with auto-commit off, used by one trigger. */
    static final class Transaction {

        private final Database database;
        private final Database.Lease lease;
        private final Object owner;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final ReentrantLock busy = new ReentrantLock();
        private final long opened = System.nanoTime();
        private volatile String failure;
        private volatile boolean warned;

        private Transaction(Database database, Database.Lease lease, Object owner) {
            this.database = database;
            this.lease = lease;
            this.owner = owner;
        }

        Database database() {
            return database;
        }

        /** The section that opened it. */
        Object owner() {
            return owner;
        }

        boolean isOpen() {
            return open.get();
        }

        /** Why it was rolled back from outside its trigger, or {@code null}. */
        String failure() {
            return failure;
        }

        /** Runs {@code work} on the transaction's connection, one statement at a time. */
        <T> T use(Work<T> work) throws SQLException {
            busy.lock();
            try {
                if (failure != null) {
                    throw new SQLException("The transaction was rolled back: " + failure);
                }
                if (!open.get()) {
                    throw new SQLException("The transaction is no longer open");
                }
                return work.run(lease.connection());
            } finally {
                busy.unlock();
            }
        }

        void commit() throws SQLException {
            busy.lock();
            try {
                if (failure != null) {
                    throw new SQLException("The transaction was rolled back: " + failure);
                }
                if (open.compareAndSet(true, false)) {
                    try {
                        lease.connection().commit();
                    } finally {
                        release();
                    }
                }
            } finally {
                busy.unlock();
            }
        }

        /** Rolls back, if it's still open. Never throws. Waits for a statement running in it. */
        void rollback() {
            busy.lock();
            try {
                rollbackHeld(null);
            } finally {
                busy.unlock();
            }
        }

        /**
         * Rolls back from outside its trigger, waiting at most until {@code deadline} for a statement running in it;
         * past that, the connection is dropped, which rolls back on the database's side. The trigger's later
         * statements fail with {@code reason}.
         */
        void rollbackBy(long deadline, String reason) {
            boolean held;
            try {
                held = busy.tryLock(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                held = false;
            }
            if (!held) {
                failure = reason;
                if (open.compareAndSet(true, false)) {
                    lease.abort();
                }
                return;
            }
            try {
                rollbackHeld(reason);
            } finally {
                busy.unlock();
            }
        }

        private void rollbackHeld(String reason) {
            if (reason != null) {
                failure = reason;
            }
            if (open.compareAndSet(true, false)) {
                try {
                    lease.connection().rollback();
                } catch (SQLException | RuntimeException ignored) {
                    // The connection is closed below, which rolls back too.
                } finally {
                    release();
                }
            }
        }

        private void release() {
            try {
                lease.connection().setAutoCommit(true);
            } catch (SQLException | RuntimeException ignored) {
                // Hikari resets it as the connection goes back.
            }
            try {
                lease.close();
            } catch (SQLException | RuntimeException ignored) {
                // Gone already.
            }
        }
    }

    /** Work on a transaction's connection. */
    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Opens a transaction for a trigger. Takes a connection, so it runs off the server's threads.
     *
     * @throws SQLException if no connection can be had
     * @throws IllegalStateException if the trigger has one open on the database already
     */
    Transaction begin(Object scope, Database database, Object owner) throws SQLException {
        if (find(scope, database) != null) {
            throw new IllegalStateException("There is already an open transaction on " + database.shown());
        }
        Database.Lease lease = database.lease();
        try {
            lease.connection().setAutoCommit(false);
        } catch (SQLException | RuntimeException e) {
            try {
                lease.close();
            } catch (SQLException closing) {
                e.addSuppressed(closing);
            }
            throw e;
        }
        Transaction transaction = new Transaction(database, lease, owner);
        synchronized (scopes) {
            Transaction existing = scopes.computeIfAbsent(scope, ignored -> new IdentityHashMap<>()).get(database);
            if (existing != null) {
                transaction.rollback();
                throw new IllegalStateException("There is already an open transaction on " + database.shown());
            }
            scopes.get(scope).put(database, transaction);
        }
        return transaction;
    }

    /** The trigger's transaction on the database, open or broken, or {@code null}. */
    Transaction find(Object scope, Database database) {
        synchronized (scopes) {
            Map<Database, Transaction> open = scopes.get(scope);
            return open == null ? null : open.get(database);
        }
    }

    /** The trigger's transaction that section {@code owner} opened, open or broken, or {@code null}. */
    Transaction findOwned(Object scope, Object owner) {
        synchronized (scopes) {
            Map<Database, Transaction> open = scopes.get(scope);
            if (open != null) {
                for (Transaction transaction : open.values()) {
                    if (transaction.owner() == owner) {
                        return transaction;
                    }
                }
            }
            return null;
        }
    }

    /** Forgets a transaction that's over. */
    void end(Object scope, Transaction transaction) {
        synchronized (scopes) {
            Map<Database, Transaction> open = scopes.get(scope);
            if (open != null) {
                open.remove(transaction.database(), transaction);
                if (open.isEmpty()) {
                    scopes.remove(scope);
                }
            }
        }
    }

    /** How many transactions are open or broken. */
    int open() {
        synchronized (scopes) {
            return scopes.values().stream().mapToInt(Map::size).sum();
        }
    }

    /** Says in the console, once each, which transactions have been open a long time: a trigger waiting in one. */
    void warnLongOpen() {
        List<Transaction> old = new ArrayList<>();
        synchronized (scopes) {
            for (Map<Database, Transaction> open : scopes.values()) {
                for (Transaction transaction : open.values()) {
                    if (transaction.isOpen() && !transaction.warned
                            && System.nanoTime() - transaction.opened > warnAfterNanos) {
                        transaction.warned = true;
                        old.add(transaction);
                    }
                }
            }
        }
        for (Transaction transaction : old) {
            logger.warning("A transaction on " + transaction.database().shown() + " has been open for over "
                    + TimeUnit.NANOSECONDS.toSeconds(warnAfterNanos) + " seconds, holding a connection: its"
                    + " trigger is still in its section, waiting.");
        }
    }

    /**
     * Rolls back every open transaction, as NeoSkriptDb stops: each waits at most until {@code deadline} for a
     * statement running in it (which should have been cancelled), then its connection is dropped.
     */
    void close(long deadline) {
        List<Transaction> open = new ArrayList<>();
        synchronized (scopes) {
            scopes.values().forEach(transactions -> open.addAll(transactions.values()));
            scopes.clear();
        }
        for (Transaction transaction : open) {
            transaction.rollbackBy(deadline, "NeoSkriptDb stopped");
        }
    }
}
