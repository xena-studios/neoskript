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

    private static final Set<Pending> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile boolean closed;
    private static ScheduledThreadPoolExecutor timer;

    private Pauses() {}

    /** Lets triggers pause, as when the plugin enables. */
    static synchronized void open() {
        closed = false;
    }

    /**
     * Stops every pause, as when the server stops: their triggers don't carry on, their stages are cancelled
     * where they can be, and the timer's thread ends. Stages that complete later are ignored.
     */
    public static void close() {
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

    /** How many triggers are paused now. */
    public static int pending() {
        return PENDING.size();
    }

    private static synchronized ScheduledThreadPoolExecutor timer() {
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
     * stage had completed already (or there was none), the result is in the frame, and the caller carries
     * straight on. Either way the event counts as waited for, as after a {@code wait}.
     */
    public static boolean pause(Resumable trigger, TriggerContext context, Frame frame, CompletionStage<?> stage,
            Point point) {
        Object event = context.event();
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
            Waits.delay(event);
            return false;
        }
        Scheduler scheduler = Waits.scheduler();
        if (scheduler == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        Pending pending = new Pending(trigger, context, frame, point, Waits.ownerOf(context, frame), scheduler, future);
        if (closed) {
            // The server is stopping: the trigger stops here, as a wait's would.
            pending.abandon();
            return true;
        }
        PENDING.add(pending);
        Waits.scheduled(event);
        try {
            pending.timeout = timer().schedule(() -> pending.finish(null, new Timeout(point.timeoutMillis())),
                    Math.max(point.timeoutMillis(), 1), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            pending.abandon();
            return true;
        }
        stage.whenComplete(pending::finish);
        return true;
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
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> flat = new LinkedHashMap<>();
            flatten("", map, flat, 0);
            return new KeyedValues(flat.values().toArray(), flat.keySet().toArray(String[]::new));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(java.util.Objects::nonNull).toArray();
        }
        if (value instanceof Object[] array) {
            return java.util.Arrays.stream(array).filter(java.util.Objects::nonNull).toArray();
        }
        return value;
    }

    /** How deep maps and lists in a stage's value are followed, against one that contains itself. */
    private static final int MAX_DEPTH = 64;

    private static void flatten(String prefix, Object value, Map<String, Object> into, int depth) {
        if (value == null) {
            return;
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("The result is nested more than " + MAX_DEPTH + " deep");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    flatten(child(prefix, String.valueOf(entry.getKey())), entry.getValue(), into, depth + 1);
                }
            }
        } else if (value instanceof Collection<?> || value instanceof Object[]) {
            List<Object> items = value instanceof Collection<?> collection ? new ArrayList<>(collection)
                    : java.util.Arrays.asList((Object[]) value);
            for (int i = 0; i < items.size(); i++) {
                flatten(child(prefix, Integer.toString(i + 1)), items.get(i), into, depth + 1);
            }
        } else if (!prefix.isEmpty()) {
            into.put(prefix, value);
        }
    }

    /** The index of {@code key} in the list at {@code prefix}: {@code rows::1}. */
    private static String child(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "::" + key;
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
        private final Object event;
        private final Point point;
        private final Scheduler scheduler;
        // Let go of once it's over, so a stage kept after that keeps nothing of the trigger.
        private Resumable trigger;
        private TriggerContext context;
        private Frame frame;
        private Object owner;
        private CompletableFuture<?> future;
        volatile ScheduledFuture<?> timeout;

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
            PENDING.remove(this);
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
            release();
            if (closed) {
                Waits.ran(event);
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
                scheduler.runLater(on, 0, () -> {
                    Waits.delay(event);
                    try {
                        if (reported != null) {
                            report(point, reported);
                        }
                        resumed.resume(in, carried);
                    } catch (ScriptRuntimeException e) {
                        Waits.errors().accept(e);
                    } finally {
                        Waits.ran(event);
                    }
                });
            } catch (RuntimeException stopping) {
                // The plugin is disabling (Bukkit refuses its tasks): the trigger stops here.
                Waits.ran(event);
            }
        }

        /** Ends it without carrying on: the server is stopping. */
        void abandon() {
            if (!over.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> timer = timeout;
            if (timer != null) {
                timer.cancel(false);
            }
            CompletableFuture<?> stage = future;
            release();
            Waits.ran(event);
            if (stage != null) {
                try {
                    stage.cancel(false);
                } catch (RuntimeException ignored) {
                    // Only a courtesy to the addon.
                }
            }
        }

        private void release() {
            trigger = null;
            context = null;
            frame = null;
            owner = null;
            future = null;
        }
    }
}
