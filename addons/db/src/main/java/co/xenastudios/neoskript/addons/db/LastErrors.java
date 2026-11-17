/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code the last sql error}, as skript-db keeps it: the error of the last statement or transaction a trigger
 * ran, or nothing if it succeeded; a trigger that hasn't run one sees the last error of any. A trigger is known by
 * its event, held weakly, so this keeps nothing of a trigger once it's over.
 */
final class LastErrors {

    private final ConcurrentHashMap<Key, Optional<String>> byScope = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> gone = new ReferenceQueue<>();
    private volatile String last;

    /**
     * Records how a statement ended.
     *
     * @param scope the trigger's event
     * @param error the error, or {@code null} if it succeeded
     */
    void record(Object scope, String error) {
        purge();
        if (scope != null) {
            byScope.put(new Key(scope, gone), Optional.ofNullable(error));
        }
        last = error;
    }

    /**
     * The error a trigger sees.
     *
     * @param scope the trigger's event
     * @return the error, or {@code null}
     */
    String get(Object scope) {
        purge();
        Optional<String> own = scope == null ? null : byScope.get(new Key(scope, null));
        return own != null ? own.orElse(null) : last;
    }

    /** How many triggers' errors are kept. */
    int size() {
        purge();
        return byScope.size();
    }

    private void purge() {
        java.lang.ref.Reference<?> reference;
        while ((reference = gone.poll()) != null) {
            byScope.remove(reference);
        }
    }

    /** An object by its identity, held weakly. */
    private static final class Key extends WeakReference<Object> {

        private final int hash;

        Key(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
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
            return other instanceof Key key && key.hash == hash && key.get() != null && key.get() == get();
        }
    }
}
