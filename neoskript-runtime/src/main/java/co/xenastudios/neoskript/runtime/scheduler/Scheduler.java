/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.scheduler;

/**
 * Where triggers run. On Paper everything runs on the main thread; on Folia, work that isn't tied to an
 * entity or location (script loads, periodic events, console commands) runs on the global region.
 */
public interface Scheduler {

    /** Runs {@code task} on the global thread: the main thread on Paper, the global region on Folia. */
    void runGlobal(Runnable task);

    /** Runs {@code task} on the global thread after {@code ticks} server ticks. */
    void runGlobalLater(long ticks, Runnable task);
}
