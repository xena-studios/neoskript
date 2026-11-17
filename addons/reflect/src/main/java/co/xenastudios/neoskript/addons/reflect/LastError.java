/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.element.TriggerContext;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * skript-reflect's {@code [the] [last] [java] (throwable|exception|error)}: what the last Java call in this run of the
 * trigger threw, or {@code null} once one succeeds. skript-reflect keeps one for the whole server. Here each run has
 * its own, found by the run's context ({@link TriggerContext#outermostRun}, so a function's error is its caller's, as on
 * skript-reflect): on Folia, where regions run at once, one region's call can't hide another's error, and a run that
 * waits and carries on on another thread still reads its own.
 *
 * <p>Held only as long as the run is: the context is held weakly, so when the trigger ends, its error, and the classes
 * it names, are let go, with no thread holding on to them.
 */
final class LastError {

    private static final Map<Key, Throwable> LAST = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Object> GONE = new ReferenceQueue<>();

    private LastError() {}

    static Throwable get(TriggerContext context) {
        return context == null ? null : LAST.get(new Key(context.outermostRun(), null));
    }

    static void set(TriggerContext context, Throwable error) {
        if (context == null) {
            return;
        }
        expunge();
        LAST.put(new Key(context.outermostRun(), GONE), error);
    }

    static void clear(TriggerContext context) {
        if (context != null && !LAST.isEmpty()) {
            LAST.remove(new Key(context.outermostRun(), null));
        }
    }

    /** How many runs have an error kept. For tests. */
    static int size() {
        expunge();
        return LAST.size();
    }

    private static void expunge() {
        for (java.lang.ref.Reference<?> gone; (gone = GONE.poll()) != null; ) {
            LAST.remove(gone);
        }
    }

    /** A run's context, by identity, held weakly. */
    private static final class Key extends WeakReference<Object> {

        private final int hash;

        Key(Object run, ReferenceQueue<Object> queue) {
            super(run, queue);
            hash = System.identityHashCode(run);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == key.get();
        }
    }
}
