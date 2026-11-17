/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.runtime.errors.ErrorSites;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Triggers paused by an addon's pausing effect or expression, until its {@link CompletionStage} completes: the
 * same frames as {@code wait}'s ({@link Waits}), carried on through the same scheduler, on the trigger's thread.
 *
 * <p>Each pause has a timeout, after which the trigger carries on without the result. A stage that completes
 * after that, or after the server has stopped ({@link #close}), is ignored, and a pause keeps nothing of the
 * trigger once it's over, so a stage an addon holds on to doesn't keep old scripts' classes alive.
 */
public final class Pauses {

    /** {@link Frame#result} of a pause that failed or timed out, whose error has been reported. */
    public static final Object FAILED = new Object() {
        @Override
        public String toString() {
            return "<failed>";
        }
    };

    /** The default timeout, a minute. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000;

    /**
     * Where a pause is in compiled code: the element's call, for its errors, and how long it may take.
     *
     * @param site the element's call
     * @param timeoutMillis how long the trigger waits, in milliseconds
     */
    public record Point(ErrorSites.Site site, long timeoutMillis) {}

    /**
     * The most triggers paused at once. Past it, a pausing element fails at once with a runtime error, so a script
     * looping over a slow service can't pile up frames without end.
     */
    public static final int MAX_PENDING = 10_000;

    private static final Set<Pending> PENDING = ConcurrentHashMap.newKeySet();
    /** Whether the server is stopping: pauses started from now on stop their triggers, as a wait's would. */
    private static volatile boolean closed;
    private static ScheduledThreadPoolExecutor timer;

    private Pauses() {}

    /** Whether the server has started stopping: pauses started from now on get {@link #close}'s grace. */
    private static volatile boolean stopping;

    /** Lets triggers pause, as when the plugin enables. */
    static synchronized void open() {
        closed = false;
        stopping = false;
    }

    /**
     * Says the server is stopping, before its {@code on unload} triggers run: what they pause for gets
     * {@link #close(java.time.Duration)}'s grace. Pauses from before don't.
     */
    public static void stopping() {
        stopping = true;
    }

    /**
     * Stops every pause, as when the server stops. First it gives the stages paused for since {@link #stopping}
     * up to {@code grace} to complete, so work an {@code on unload} trigger started (a last database write) can
     * finish before the server exits; then their triggers are dropped without carrying on, as after a {@code wait}, and the timer's thread
     * ends. Stages aren't cancelled: the work they stand for may still be running, and it's the addon's to finish
     * or stop. A stage completing after this is ignored. It never waits longer than {@code grace}.
     */
    public static void close(java.time.Duration grace) {
        long deadline = System.nanoTime() + Math.max(grace.toNanos(), 0);
        synchronized (PENDING) {
            while (PENDING.stream().anyMatch(pending -> pending.late)) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(PENDING, Math.min(left, TimeUnit.MILLISECONDS.toNanos(50)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        ScheduledThreadPoolExecutor stopped;
        synchronized (Pauses.class) {
            closed = true;
            stopped = timer;
            timer = null;
        }
        for (Pending pending : List.copyOf(PENDING)) {
            pending.abandon();
        }
        PENDING.clear();
        if (stopped != null) {
            stopped.shutdownNow();
        }
    }

    /** {@link #close(java.time.Duration)} with no grace. */
    public static void close() {
        close(java.time.Duration.ZERO);
    }

    /** How many triggers are paused now. */
    public static int pending() {
        return PENDING.size();
    }

    private static void removed(Pending pending) {
        PENDING.remove(pending);
        synchronized (PENDING) {
            PENDING.notifyAll();
        }
    }

    /** The timer for timeouts, or {@code null} once the server is stopping, so none is made then. */
    private static synchronized ScheduledThreadPoolExecutor timer() {
        if (closed) {
            return null;
        }
        if (timer == null) {
            ScheduledThreadPoolExecutor made = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "NeoSkript pause timeouts");
                thread.setDaemon(true);
                return thread;
            });
            made.setRemoveOnCancelPolicy(true);
            made.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            timer = made;
        }
        return timer;
    }

    /**
     * Called by the generated code at a pausing element: carries on with {@code frame} once {@code stage}
     * completes, with what it completed with in {@link Frame#result}. Whether the trigger paused; if not, the
     * stage had completed already (or there was none, or it couldn't be waited for), the result is in the frame,
     * and the caller carries straight on. Either way the event counts as waited for, as after a {@code wait}.
     */
    public static boolean pause(Resumable trigger, TriggerContext context, Frame frame, CompletionStage<?> stage,
            Point point) {
        Object event = context.event();
        Waits.delay(event);
        CompletableFuture<?> future = null;
        if (stage != null) {
            try {
                future = stage.toCompletableFuture();
            } catch (UnsupportedOperationException notOne) {
                // A stage that can't be one: waited for through whenComplete alone.
            }
        }
        if (stage == null || future != null && future.isDone()) {
            frame.result = stage == null ? null : now(future, point);
            return false;
        }
        if (PENDING.size() >= MAX_PENDING) {
            frame.result = failed(point, new IllegalStateException("Too many triggers are waiting (" + MAX_PENDING
                    + "), so this one didn't: is a script starting slow work in a loop?"));
            return false;
        }
        Scheduler scheduler = Waits.scheduler();
        if (scheduler == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        if (closed) {
            // The server has stopped: the trigger stops here, as a wait's would.
            return true;
        }
        Pending pending = new Pending(trigger, context, frame, point, Waits.ownerOf(context, frame), scheduler, future);
        PENDING.add(pending);
        Waits.scheduled(event);
        pending.counted = true;
        ScheduledThreadPoolExecutor timeouts = timer();
        if (timeouts == null || closed) {
            // The server stopped meanwhile: close() may not have seen this one.
            pending.abandon();
            return true;
        }
        try {
            pending.timeout = timeouts.schedule(() -> pending.finish(null, new Timeout(point.timeoutMillis())),
                    Math.max(point.timeoutMillis(), 1), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            pending.abandon();
            return true;
        }
        try {
            stage.whenComplete(pending::finish);
        } catch (RuntimeException | LinkageError broken) {
            // A stage that can't be waited for is a failed one: the trigger carries on now, never later.
            pending.abandon();
            frame.result = failed(point, broken);
            return false;
        }
        return true;
    }

    /** Reports {@code failure} on the trigger's thread and gives the result of a failed pause. */
    private static Object failed(Point point, Throwable failure) {
        report(point, failure);
        return FAILED;
    }

    /**
     * {@code element}, called as {@code type} ({@code PausingEffect} or {@code PausingExpression}), so that its
     * {@code start} throwing is a failed stage, as a stage completing exceptionally is: a runtime error, after which
     * the trigger carries on.
     */
    public static Object guard(Object element, Class<?> type) {
        if (type == co.xenastudios.neoskript.api.element.PausingEffect.class) {
            return new GuardedEffect((co.xenastudios.neoskript.api.element.PausingEffect) element);
        }
        if (type == co.xenastudios.neoskript.api.element.PausingExpression.class) {
            return new GuardedExpression((co.xenastudios.neoskript.api.element.PausingExpression) element);
        }
        return element;
    }

    private static CompletionStage<?> started(java.util.function.Supplier<CompletionStage<?>> start) {
        try {
            return start.get();
        } catch (RuntimeException | LinkageError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private record GuardedEffect(co.xenastudios.neoskript.api.element.PausingEffect element)
            implements co.xenastudios.neoskript.api.element.PausingEffect, ErrorSites.Transparent {

        @Override
        public CompletionStage<?> start(TriggerContext context, Object[] arguments) {
            return started(() -> element.start(context, arguments));
        }

        @Override
        public int target() {
            return element.target();
        }
    }

    private record GuardedExpression(co.xenastudios.neoskript.api.element.PausingExpression element)
            implements co.xenastudios.neoskript.api.element.PausingExpression, ErrorSites.Transparent {

        @Override
        public CompletionStage<?> start(TriggerContext context, Object[] arguments) {
            return started(() -> element.start(context, arguments));
        }
    }

    /** The outcome of a stage that's done, on the trigger's thread: an error is reported now. */
    private static Object now(CompletableFuture<?> future, Point point) {
        Object value;
        try {
            value = converted(future.join());
        } catch (RuntimeException e) {
            report(point, e);
            return FAILED;
        }
        return value;
    }

    /** A pausing expression's value: what its stage completed with, or nothing if it failed. */
    public static Object value(Frame frame) {
        Object result = frame.result;
        frame.result = null;
        return result == FAILED ? null : KeyedValues.plain(result);
    }

    /** A pausing effect's value for its target, with the indices a map gave it ({@link KeyedValues}). */
    public static Object keyed(Frame frame) {
        Object result = frame.result;
        frame.result = null;
        return result == FAILED ? null : result;
    }

    /** Whether the pause failed or timed out, so a pausing effect stores nothing. */
    public static boolean failed(Frame frame) {
        return frame.result == FAILED;
    }

    /**
     * A stage's value as scripts have values: a map is keyed values (a nested map or list makes a list under its
     * key), a collection or an array is several values, and anything else is itself.
     */
    static Object converted(Object value) {
        return KeyedValues.result(value);
    }

    /** Reports a stage's failure on the pausing element's line. */
    private static void report(Point point, Throwable failure) {
        RuntimeErrors.report(point.site().source(), message(failure));
    }

    /** What a failure says: the exception's message, or its kind. */
    static String message(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof Timeout timeout) {
            return timeout.getMessage();
        }
        if (cause instanceof CancellationException) {
            return "It was cancelled before it finished";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /** A pause that took too long. */
    private static final class Timeout extends RuntimeException {

        Timeout(long millis) {
            super("Gave up waiting for it after " + new Timespan(millis), null, false, false);
        }
    }

    /** A paused trigger, until its stage completes, times out or the server stops. */
    private static final class Pending {

        private final AtomicBoolean over = new AtomicBoolean();
        // All let go of once it's over, so a stage kept after that keeps nothing of the trigger or its event.
        private Object event;
        private Point point;
        private Scheduler scheduler;
        private Resumable trigger;
        private TriggerContext context;
        private Frame frame;
        private Object owner;
        private CompletableFuture<?> future;
        volatile ScheduledFuture<?> timeout;
        /** Whether it was counted in {@link Waits#scheduled}, so it's uncounted once. */
        volatile boolean counted;
        /** Whether it was started while the server was stopping, so the stop waits a little for it. */
        final boolean late = stopping;

        Pending(Resumable trigger, TriggerContext context, Frame frame, Point point, Object owner, Scheduler scheduler,
                CompletableFuture<?> future) {
            this.trigger = trigger;
            this.context = context;
            this.frame = frame;
            this.point = point;
            this.owner = owner;
            this.scheduler = scheduler;
            this.future = future;
            this.event = context.event();
        }

        /** Its stage completed, or its timeout came: carries the trigger on, on its thread, unless it's over. */
        void finish(Object value, Throwable failure) {
            if (!over.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> timer = timeout;
            if (timer != null) {
                timer.cancel(false);
            }
            if (failure instanceof Timeout && future != null) {
                try {
                    future.cancel(false);
                } catch (RuntimeException ignored) {
                    // Cancelling is only a courtesy to the addon.
                }
            }
            Resumable resumed = trigger;
            TriggerContext in = context;
            Frame carried = frame;
            Object on = owner;
            Object of = event;
            Point at = point;
            Scheduler with = scheduler;
            boolean wasCounted = counted;
            release();
            removed(this);
            if (closed || stopping) {
                // The server is stopping: the work is done, but the trigger doesn't carry on, as after a wait.
                uncount(of, wasCounted);
                return;
            }
            Object result;
            Throwable problem = failure;
            if (problem == null) {
                try {
                    result = converted(value);
                } catch (RuntimeException e) {
                    problem = e;
                    result = FAILED;
                }
            } else {
                result = FAILED;
            }
            Throwable reported = problem;
            carried.result = result;
            try {
                with.runLater(on, 0, () -> {
                    Waits.delay(of);
                    try {
                        if (reported != null) {
                            report(at, reported);
                        }
                        resumed.resume(in, carried);
                    } catch (ScriptRuntimeException e) {
                        Waits.errors().accept(e);
                    } finally {
                        uncount(of, wasCounted);
                    }
                });
            } catch (RuntimeException stopping) {
                // The plugin is disabling (Bukkit refuses its tasks): the trigger stops here.
                uncount(of, wasCounted);
            }
        }

        /**
         * Ends it without carrying on: the server stopped, or its stage can't be waited for. Its stage isn't
         * cancelled: the work may still be running, and it's the addon's.
         */
        void abandon() {
            if (!over.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> timer = timeout;
            if (timer != null) {
                timer.cancel(false);
            }
            Object of = event;
            boolean wasCounted = counted;
            release();
            removed(this);
            uncount(of, wasCounted);
        }

        private static void uncount(Object event, boolean counted) {
            if (counted) {
                Waits.ran(event);
            }
        }

        private void release() {
            trigger = null;
            context = null;
            frame = null;
            owner = null;
            future = null;
            event = null;
            point = null;
            scheduler = null;
        }
    }
}
