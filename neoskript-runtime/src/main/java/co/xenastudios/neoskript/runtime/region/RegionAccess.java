/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.region;

/** What the server knows of regions: which values live in one, which thread owns it, and how to run code there. */
public interface RegionAccess {

    /** What lives in a region: entities (players too), blocks and locations. */
    enum Kind {
        NONE,
        ENTITY,
        BLOCK,
        LOCATION
    }

    /** What values of {@code type} are, for a placeholder of that type. */
    Kind kindOf(Class<?> type);

    /** What {@code value} is. */
    Kind kind(Object value);

    /** Whether the current thread owns the region of {@code value}; for {@code null}, whether it's the global one. */
    boolean owns(Object value);

    /** Runs {@code task} soon on the thread that owns {@code value}'s region, without waiting for it. */
    void execute(Object value, Runnable task);

    /**
     * Runs {@code task} soon on the thread that owns {@code value}'s region, in order with what {@link #execute}
     * sent there before, for a trigger to carry on there. If {@code value} is an entity that's gone by then, it
     * runs on the global thread instead.
     */
    default void resume(Object value, Runnable task) {
        execute(value, task);
    }

    /**
     * Whether {@code event} is one the server is still dispatching to plugins, so a trigger of it that waited would
     * lose what it could still do to it (cancel it, change its values). Not an event of NeoSkript's own, like a
     * test, an {@code every} period or a script command.
     */
    default boolean dispatching(Object event) {
        return false;
    }

    /** How a runtime error names {@code value}: {@code the player Notch}, {@code a zombie}. */
    String describe(Object value);
}
