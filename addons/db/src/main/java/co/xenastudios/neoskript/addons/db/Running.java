/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The statement a piece of work is running, so it can be stopped: when NeoSkript gives up waiting for it, and at
 * {@code query-timeout} whatever the driver does (SQLite's only stops waiting for locks then).
 */
final class Running {

    private final ScheduledExecutorService timer;
    private final long timeoutMillis;
    /** Guards the statement: it's only cancelled while it's still the one running, never once it's finished. */
    private final Object lock = new Object();
    private Statement statement;
    private ScheduledFuture<?> stop;
    private boolean cancelled;

    /**
     * @param timer where the timeout runs, or {@code null} for none
     * @param timeoutMillis how long a statement may run, or 0 for no limit
     */
    Running(ScheduledExecutorService timer, long timeoutMillis) {
        this(timer, timeoutMillis, null);
    }

    /**
     * @param timer where the timeout runs, or {@code null} for none
     * @param timeoutMillis how long a statement may run, or 0 for no limit
     * @param live where it's kept while a statement runs, for NeoSkriptDb to cancel as it stops, or {@code null}
     */
    Running(ScheduledExecutorService timer, long timeoutMillis, java.util.Set<Running> live) {
        this.timer = timer;
        this.timeoutMillis = timeoutMillis;
        this.live = live;
    }

    private final java.util.Set<Running> live;

    /** No timeout, for tests and work that isn't a statement. */
    static Running untimed() {
        return new Running(null, 0);
    }

    /** A statement started. Call {@link #finished} before it's closed. */
    void started(Statement started) {
        synchronized (lock) {
            statement = started;
            if (live != null) {
                live.add(this);
            }
            if (cancelled) {
                cancel(started);
                return;
            }
            if (timer != null && timeoutMillis > 0) {
                try {
                    // A second over, so the driver's own timeout, where it has one, comes first.
                    stop = timer.schedule(() -> cancelIfRunning(started), timeoutMillis + 1_000,
                            TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.RejectedExecutionException stopping) {
                    // NeoSkriptDb is stopping: it cancels what's running itself.
                }
            }
        }
    }

    /**
     * The statement finished, before it's closed: it's never cancelled after this, so a cancel can't reach the
     * connection once it serves another statement (SQLite cancels the connection's work, not the statement's).
     */
    void finished(Statement finished) {
        synchronized (lock) {
            if (statement == finished) {
                statement = null;
            }
            if (live != null) {
                live.remove(this);
            }
            if (stop != null) {
                stop.cancel(false);
                stop = null;
            }
        }
    }

    /** Stops the statement running now, if any, and any started after. */
    void cancel() {
        synchronized (lock) {
            cancelled = true;
            if (statement != null) {
                cancel(statement);
            }
        }
    }

    private void cancelIfRunning(Statement target) {
        synchronized (lock) {
            if (statement == target) {
                cancel(target);
            }
        }
    }

    private static void cancel(Statement statement) {
        try {
            statement.cancel();
        } catch (SQLException | RuntimeException ignored) {
            // It finishes or fails on its own, then.
        }
    }
}
