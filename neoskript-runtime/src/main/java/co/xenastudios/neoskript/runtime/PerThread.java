/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A value for each thread, like a {@link ThreadLocal}, but kept here rather than in the thread. A thread-local value
 * of one of NeoSkript's classes stays in the server's threads after the plugin is disabled, and keeps its class
 * loader, and with it all of the old plugin, in memory for good. Threads that end are let go of.
 */
public final class PerThread<T> {

    private final Map<Thread, T> values = Collections.synchronizedMap(new WeakHashMap<>());

    public T get() {
        return values.get(Thread.currentThread());
    }

    public void set(T value) {
        if (value == null) {
            values.remove(Thread.currentThread());
        } else {
            values.put(Thread.currentThread(), value);
        }
    }
}
