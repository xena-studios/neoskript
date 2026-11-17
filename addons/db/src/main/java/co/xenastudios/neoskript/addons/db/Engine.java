/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * What runs the statements: the databases by URL, the threads statements run on, the open transactions and the
 * last errors. Statements run on its own threads, and the trigger pauses until they're done; a trigger that isn't
 * on a server thread (an async event, as skript-db allows) runs them where it is, as skript-db does.
 */
public final class Engine {

    private final Settings settings;
    private final Logger logger;
    private final Drivers drivers;
    private final BooleanSupplier onServerThread;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService housekeeping;
    private final ThreadFactory poolThreads;
    private final ConcurrentHashMap<String, Database> databases = new ConcurrentHashMap<>();
    private final Statements statements;
    private final Transactions transactions;
    private final LastErrors errors = new LastErrors();
    private volatile boolean closed;

    /**
     * Starts the threads.
     *
     * @param settings config.yml's settings
     * @param logger the console
     * @param loader the class loader the drivers are found with
     * @param onServerThread whether the current thread is one of the server's (Paper's main thread, Folia's
     *     region and global threads)
     */
    public Engine(Settings settings, Logger logger, ClassLoader loader, BooleanSupplier onServerThread) {
        this.settings = settings;
        this.logger = logger;
        this.drivers = new Drivers(loader);
        this.onServerThread = onServerThread;
        this.workers = new ThreadPoolExecutor(settings.workerThreads(), settings.workerThreads(), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threads("NeoSkriptDb-worker"));
        this.workers.allowCoreThreadTimeOut(true);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, threads("NeoSkriptDb-housekeeping"));
        scheduler.setRemoveOnCancelPolicy(true);
        this.housekeeping = scheduler;
        this.poolThreads = threads("NeoSkriptDb-pool");
        this.statements = new Statements(settings);
        this.transactions = new Transactions(TimeUnit.SECONDS.toNanos(settings.transactionTimeoutSeconds()), logger);
        long every = Math.max(1, Math.min(settings.transactionTimeoutSeconds(), 30)) * 500L;
        housekeeping.scheduleWithFixedDelay(this::tidy, every, every, TimeUnit.MILLISECONDS);
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            // Never what keeps the server's process alive.
            thread.setDaemon(true);
            return thread;
        };
    }

    Settings settings() {
        return settings;
    }

    Statements statements() {
        return statements;
    }

    Transactions transactions() {
        return transactions;
    }

    LastErrors errors() {
        return errors;
    }

    Logger logger() {
        return logger;
    }

    /**
     * The database for a URL: the same one each time, with the options given the first time before it opened.
     *
     * @param url the URL as a script wrote it ({@code mysql://...}, {@code jdbc:sqlite:...})
     * @param maxLifetimeMillis how long a connection is kept, or {@code null} for config.yml's
     * @param driver a driver's class name, or {@code null}
     * @return the database
     */
    public Database database(String url, Long maxLifetimeMillis, String driver) {
        String normalised = Drivers.normalise(url);
        Database database = databases.computeIfAbsent(normalised,
                key -> new Database(key, maxLifetimeMillis, blankToNull(driver), settings, drivers, poolThreads));
        database.options(maxLifetimeMillis, blankToNull(driver));
        return database;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    /** Every database scripts have named. */
    Collection<Database> databases() {
        return List.copyOf(databases.values());
    }

    /**
     * Runs {@code work} off the server's threads, or where it is if that isn't a server thread.
     *
     * @param work the work, which may throw
     * @param running the statement it runs, which is cancelled if the stage is (NeoSkript gave up waiting)
     * @return what the trigger waits for
     */
    CompletionStage<Object> run(Work work, AtomicReference<Statement> running) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("NeoSkriptDb is disabled"));
        }
        if (!onServerThread.getAsBoolean()) {
            // An async event's trigger: skript-db runs the statement where it is, and so does this.
            try {
                return CompletableFuture.completedFuture(work.run());
            } catch (Exception | LinkageError e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<Object> stage = new CompletableFuture<>();
        stage.whenComplete((value, failure) -> {
            if (stage.isCancelled()) {
                cancel(running.get());
            }
        });
        try {
            workers.execute(() -> {
                if (stage.isDone()) {
                    return; // Given up on before it started.
                }
                try {
                    stage.complete(work.run());
                } catch (Exception | LinkageError e) {
                    stage.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException stopped) {
            stage.completeExceptionally(new IllegalStateException("NeoSkriptDb is disabled"));
        }
        return stage;
    }

    private static void cancel(Statement statement) {
        if (statement != null) {
            try {
                statement.cancel();
            } catch (SQLException | RuntimeException ignored) {
                // It finishes or fails on its own, then.
            }
        }
    }

    /** Work for {@link #run}. */
    @FunctionalInterface
    interface Work {
        Object run() throws Exception;
    }

    /** What a failure says, without passwords: its first message, or its kind. */
    static String message(Throwable failure, Database database) {
        Throwable cause = failure;
        while (cause.getMessage() == null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return database == null ? message : Secrets.hide(message, database.url());
    }

    /** Rolls back abandoned transactions, and closes databases nothing has used for {@code idle-close}. */
    private void tidy() {
        try {
            transactions.rollBackAbandoned();
            if (settings.idleCloseSeconds() > 0) {
                long idle = TimeUnit.SECONDS.toNanos(settings.idleCloseSeconds());
                for (Database database : databases.values()) {
                    database.closeIfIdle(idle);
                }
            }
        } catch (RuntimeException e) {
            logger.warning("Couldn't tidy up the databases: " + e);
        }
    }

    /** How many statements are running or waiting for a thread. */
    int busy() {
        return workers.getActiveCount() + workers.getQueue().size();
    }

    /**
     * Stops: statements already started get up to {@code grace} to finish, then the rest are interrupted, open
     * transactions are rolled back and every database's connections close. Statements started after this fail.
     *
     * @param grace how long running statements get
     */
    public void close(java.time.Duration grace) {
        if (closed) {
            return;
        }
        closed = true;
        workers.shutdown();
        try {
            if (!workers.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                for (Database database : databases.values()) {
                    if (database.inUse() > 0) {
                        logger.warning("A statement on " + database.shown() + " was still running as NeoSkriptDb"
                                + " stopped, and was stopped.");
                    }
                }
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        housekeeping.shutdownNow();
        transactions.close();
        databases.values().forEach(Database::close);
        databases.clear();
    }

    /** Whether it has stopped. */
    boolean isClosed() {
        return closed;
    }
}
