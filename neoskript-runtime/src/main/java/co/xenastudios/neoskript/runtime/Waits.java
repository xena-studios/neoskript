/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Carries on waiting triggers. The plugin installs the scheduler and where errors in resumed code are
 * reported, since nothing up the stack can catch them then. A trigger carries on on the main thread on
 * Paper; on Folia, on the thread of what its event happened to (see {@link SimpleTriggerContext#owner}).
 */
public final class Waits {

    private static volatile Scheduler scheduler;
    private static volatile Consumer<ScriptRuntimeException> errors = e -> {
        throw e;
    };

    private Waits() {}

    /** With {@link #track}, how many carry-ons each event's triggers have scheduled and not yet run. */
    private static final java.util.Map<Object, Integer> PENDING =
            Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile boolean tracking;

    /**
     * Starts keeping count of the carry-ons each event's triggers have scheduled, for {@link #pending}. Skript's
     * test runner does, to finish a test that waits before the next one starts.
     */
    public static void track() {
        tracking = true;
    }

    /** Whether a trigger of {@code event} is waiting to carry on, with {@link #track} on. */
    public static boolean pending(Object event) {
        return PENDING.containsKey(event);
    }

    private static void scheduled(Object event) {
        if (tracking && event != null) {
            PENDING.merge(event, 1, Integer::sum);
        }
    }

    private static void ran(Object event) {
        if (tracking && event != null) {
            PENDING.computeIfPresent(event, (key, count) -> count == 1 ? null : count - 1);
        }
    }

    public static void install(Scheduler scheduler, Consumer<ScriptRuntimeException> errors) {
        Waits.scheduler = scheduler;
        Waits.errors = errors;
    }

    /** How long a {@code wait} waits, in ticks. Anything that isn't a timespan waits nothing. */
    public static long ticks(Object timespan) {
        return Values.single(timespan) instanceof Timespan t ? Math.max(t.ticks(), 0) : 0;
    }

    /**
     * Called by code compiled for Folia before a line that only Folia's global thread may run (a console command, a
     * change of world time or PvP): carries on with {@code frame} on the global thread, so the line's change is
     * there for the next line, as on Paper. Not when this is the global thread already, nor in a trigger of an event
     * the server is still dispatching, which would lose what it could still do to the event: there the line sends
     * its change to the global thread and the trigger carries on
     * ({@link co.xenastudios.neoskript.runtime.region.Regions#GLOBAL_CHANGES}). Whether it was scheduled.
     */
    public static boolean hopGlobal(Resumable trigger, TriggerContext context, Frame frame, Object unused) {
        co.xenastudios.neoskript.runtime.region.RegionAccess access =
                co.xenastudios.neoskript.runtime.region.Regions.access();
        if (access == null || access.owns(null)
                || (access.dispatching(context.event()) && !delayed(context.event()))) {
            return false;
        }
        Scheduler current = scheduler;
        if (current == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        frame.owner = null;
        scheduled(context.event());
        current.runLater(null, 0, () -> {
            delay(context.event());
            try {
                trigger.resume(context, frame);
            } catch (ScriptRuntimeException e) {
                errors.accept(e);
            } finally {
                ran(context.event());
            }
        });
        return true;
    }

    /** Called by the generated code at a {@code wait}: carries on with {@code frame} after {@code ticks}. */
    public static void schedule(Resumable trigger, TriggerContext context, Frame frame, long ticks) {
        Scheduler current = scheduler;
        if (current == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        Object owner = frame.owner != null ? frame.owner : owner(context);
        scheduled(context.event());
        current.runLater(owner, ticks, () -> {
            delay(context.event());
            try {
                trigger.resume(context, frame);
            } catch (ScriptRuntimeException e) {
                errors.accept(e);
            } finally {
                ran(context.event());
            }
        });
    }

    /** What the trigger's event happened to, whose thread it runs on (Folia). */
    private static Object owner(TriggerContext context) {
        return context instanceof SimpleTriggerContext simple ? simple.owner() : null;
    }

    /**
     * Called by code compiled for Folia at the start and the end of {@code run on X:}: carries on with
     * {@code frame} on the thread that owns {@code to}, or with {@code null}, the trigger's own thread. Whether it
     * was scheduled; if not, this thread already owns it, and the caller carries straight on.
     */
    public static boolean hop(Resumable trigger, TriggerContext context, Frame frame, Object to) {
        Object target = Values.single(to);
        if (target == null) {
            target = owner(context);
        }
        co.xenastudios.neoskript.runtime.region.RegionAccess access = co.xenastudios.neoskript.runtime.region.Regions.access();
        if (access != null && access.owns(target)) {
            return false;
        }
        Object owner = target;
        Frame carried = frame;
        carried.owner = null;
        Scheduler current = scheduler;
        if (current == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        scheduled(context.event());
        current.runLater(owner, 0, () -> {
            delay(context.event());
            try {
                trigger.resume(context, carried);
            } catch (ScriptRuntimeException e) {
                errors.accept(e);
            } finally {
                ran(context.event());
            }
        });
        return true;
    }

    /** The events whose triggers have waited, as Skript's {@code Delay}: kept only while the event is. */
    private static final Set<Object> DELAYED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * Whether a trigger of {@code event} has waited, as Skript's {@code Delay.isDelayed}: from then on, what
     * reads or changes the event's own values (the tool in an item held event, a teleport in a respawn event)
     * reads or changes the world instead, as the event is over.
     */
    public static boolean delayed(Object event) {
        return event != null && DELAYED.contains(event);
    }

    /** Marks {@code event} as waited for, as Skript's {@code Delay.addDelayedEvent}. */
    public static void delay(Object event) {
        if (event != null) {
            DELAYED.add(event);
        }
    }
}
