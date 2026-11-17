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
    private volatile Statement statement;
    private volatile ScheduledFuture<?> stop;
    private volatile boolean cancelled;

    /**
     * @param timer where the timeout runs, or {@code null} for none
     * @param timeoutMillis how long a statement may run, or 0 for no limit
     */
    Running(ScheduledExecutorService timer, long timeoutMillis) {
        this.timer = timer;
        this.timeoutMillis = timeoutMillis;
    }

    /** No timeout, for tests and work that isn't a statement. */
    static Running untimed() {
        return new Running(null, 0);
    }

    /** A statement started. */
    void started(Statement started) {
        statement = started;
        if (cancelled) {
            cancel(started);
            return;
        }
        if (timer != null && timeoutMillis > 0) {
            try {
                // A second over, so the driver's own timeout, where it has one, comes first.
                stop = timer.schedule(() -> cancel(started), timeoutMillis + 1_000, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException stopping) {
                // NeoSkriptDb is stopping: the statement gets the grace instead.
            }
        }
    }

    /** The statement finished. */
    void finished() {
        statement = null;
        ScheduledFuture<?> pending = stop;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    /** Stops the statement running now, if any, and any started after. */
    void cancel() {
        cancelled = true;
        Statement current = statement;
        if (current != null) {
            cancel(current);
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
