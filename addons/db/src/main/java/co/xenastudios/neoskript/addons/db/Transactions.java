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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * The transactions scripts have open ({@code transaction on {db}:}), by trigger and database, as skript-db keeps
 * them. A transaction its section left without finishing ({@code stop}, {@code return}) is rolled back once it
 * has been idle for {@code transaction-timeout}, and every one still open is rolled back when NeoSkriptDb stops.
 */
final class Transactions {

    private final Map<Object, Map<Database, Transaction>> scopes = new IdentityHashMap<>();
    private final long timeoutNanos;
    private final Logger logger;

    Transactions(long timeoutNanos, Logger logger) {
        this.timeoutNanos = timeoutNanos;
        this.logger = logger;
    }

    /** One transaction: a connection with auto-commit off, used by one trigger. */
    static final class Transaction {

        private final Database database;
        private final Database.Lease lease;
        private final Object owner;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final ReentrantLock busy = new ReentrantLock();
        private volatile long lastUsed = System.nanoTime();

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

        /** Runs {@code work} on the transaction's connection, one statement at a time. */
        <T> T use(Work<T> work) throws SQLException {
            busy.lock();
            try {
                if (!open.get()) {
                    throw new SQLException("The transaction is no longer open");
                }
                return work.run(lease.connection());
            } finally {
                lastUsed = System.nanoTime();
                busy.unlock();
            }
        }

        void commit() throws SQLException {
            busy.lock();
            try {
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

        /** Rolls back, if it's still open. Never throws. */
        void rollback() {
            // A statement that's running is left to finish or time out first.
            busy.lock();
            try {
                if (open.compareAndSet(true, false)) {
                    try {
                        lease.connection().rollback();
                    } catch (SQLException | RuntimeException ignored) {
                        // The connection is closed below, which rolls back too.
                    } finally {
                        release();
                    }
                }
            } finally {
                busy.unlock();
            }
        }

        private boolean idleLongerThan(long nanos) {
            return !busy.isLocked() && System.nanoTime() - lastUsed > nanos;
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
            if (existing != null && existing.isOpen()) {
                transaction.rollback();
                throw new IllegalStateException("There is already an open transaction on " + database.shown());
            }
            scopes.get(scope).put(database, transaction);
        }
        return transaction;
    }

    /** The trigger's open transaction on the database, or {@code null}. */
    Transaction find(Object scope, Database database) {
        synchronized (scopes) {
            Map<Database, Transaction> open = scopes.get(scope);
            Transaction transaction = open == null ? null : open.get(database);
            return transaction != null && transaction.isOpen() ? transaction : null;
        }
    }

    /** The trigger's open transaction that section {@code owner} opened, or {@code null}. */
    Transaction findOwned(Object scope, Object owner) {
        synchronized (scopes) {
            Map<Database, Transaction> open = scopes.get(scope);
            if (open != null) {
                for (Transaction transaction : open.values()) {
                    if (transaction.isOpen() && transaction.owner() == owner) {
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

    /** How many transactions are open. */
    int open() {
        synchronized (scopes) {
            return scopes.values().stream().mapToInt(Map::size).sum();
        }
    }

    /** Rolls back the transactions idle for longer than {@code transaction-timeout}. */
    void rollBackAbandoned() {
        List<Transaction> abandoned = new ArrayList<>();
        synchronized (scopes) {
            for (Map<Database, Transaction> open : scopes.values()) {
                open.values().removeIf(transaction -> {
                    if (!transaction.isOpen()) {
                        return true;
                    }
                    if (transaction.idleLongerThan(timeoutNanos)) {
                        abandoned.add(transaction);
                        return true;
                    }
                    return false;
                });
            }
            scopes.values().removeIf(Map::isEmpty);
        }
        for (Transaction transaction : abandoned) {
            transaction.rollback();
            logger.warning("Rolled back an abandoned transaction on " + transaction.database().shown()
                    + ". Don't leave a transaction section with stop, exit or return.");
        }
    }

    /** Rolls back every open transaction, as NeoSkriptDb stops. */
    void close() {
        List<Transaction> open = new ArrayList<>();
        synchronized (scopes) {
            scopes.values().forEach(transactions -> open.addAll(transactions.values()));
            scopes.clear();
        }
        open.forEach(Transaction::rollback);
    }
}
