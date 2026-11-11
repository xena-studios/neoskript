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

    public static void install(Scheduler scheduler, Consumer<ScriptRuntimeException> errors) {
        Waits.scheduler = scheduler;
        Waits.errors = errors;
    }

    /** How long a {@code wait} waits, in ticks. Anything that isn't a timespan waits nothing. */
    public static long ticks(Object timespan) {
        return Values.single(timespan) instanceof Timespan t ? Math.max(t.ticks(), 0) : 0;
    }

    /** Called by the generated code at a {@code wait}: carries on with {@code frame} after {@code ticks}. */
    public static void schedule(Resumable trigger, TriggerContext context, Frame frame, long ticks) {
        Scheduler current = scheduler;
        if (current == null) {
            throw new IllegalStateException("Waiting isn't available: no scheduler is installed");
        }
        Object owner = frame.owner != null ? frame.owner : owner(context);
        current.runLater(owner, ticks, () -> {
            delay(context.event());
            try {
                trigger.resume(context, frame);
            } catch (ScriptRuntimeException e) {
                errors.accept(e);
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
        current.runLater(owner, 0, () -> {
            delay(context.event());
            try {
                trigger.resume(context, carried);
            } catch (ScriptRuntimeException e) {
                errors.accept(e);
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
