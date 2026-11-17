/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

/**
 * Where code runs. On Paper everything runs on the main thread. On Folia, an entity, a block and a location each
 * belong to a region, which only that region's thread may read or change, and world-wide state belongs to the global
 * thread. Script code runs on the thread that owns what started it: an entity's event on the entity's thread, a script
 * load or an {@code every} period on the global one.
 *
 * <p>An owner is an entity (players too), a block or a location; anything else, or {@code null}, belongs to the
 * global thread. Tasks are run by NeoSkript's plugin: they're cancelled when NeoSkript disables, not the addon.
 *
 * <p>Addons call this; they don't implement it.
 */
public interface Threads {

    /**
     * Whether the server is Folia.
     *
     * @return whether it's Folia
     */
    boolean isFolia();

    /**
     * Whether the current thread may touch {@code owner}: the thread of its region on Folia, the main thread on
     * Paper.
     *
     * @param owner an entity, block or location; anything else, or {@code null}, for the global thread
     * @return whether this thread owns it
     */
    boolean owns(Object owner);

    /**
     * Runs a task on the global thread (the main thread on Paper), soon: on the next tick, never now.
     *
     * @param task what to run
     */
    void runGlobal(Runnable task);

    /**
     * Runs a task on the global thread after {@code ticks} server ticks.
     *
     * @param ticks how many ticks to wait first; 0 waits until the next
     * @param task what to run
     */
    void runGlobalLater(long ticks, Runnable task);

    /**
     * Runs a task on the global thread every {@code period} ticks, first after {@code delay}.
     *
     * @param delay how many ticks to wait first
     * @param period how many ticks between runs, at least 1
     * @param task what to run
     * @return what stops it
     */
    Runnable runGlobalRepeating(long delay, long period, Runnable task);

    /**
     * Runs a task on the thread that owns {@code owner}, soon, without waiting for it. On Paper that's the main
     * thread, on the next tick. A task for an entity that's gone by then (a player who left) doesn't run.
     *
     * @param owner an entity, block or location; anything else, or {@code null}, for the global thread
     * @param task what to run
     */
    void run(Object owner, Runnable task);

    /**
     * Runs a task on the thread that owns {@code owner} after {@code ticks} server ticks. On Folia, a task for an
     * entity that's gone by then runs on the global thread instead.
     *
     * @param owner an entity, block or location; anything else, or {@code null}, for the global thread
     * @param ticks how many ticks to wait first
     * @param task what to run
     */
    void runLater(Object owner, long ticks, Runnable task);

    /**
     * Runs a task off the server's threads, soon: for slow work that touches no entity, block or world, like a
     * file or a web request. Go back with {@link #run} to use what it found.
     *
     * @param task what to run
     */
    void runAsync(Runnable task);
}
