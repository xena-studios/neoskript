/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * One database of the {@code databases} section of NeoSkript's config.yml (Skript's {@code databases:}), where the
 * global variables its {@code pattern} matches are saved. An SQL database with {@code monitor-changes: true} is
 * shared: every server that uses it reads the others' changes every {@code monitor-interval}, and an addon can make
 * that quicker by telling the other servers when this one has written ({@link #whenWritten}), so they read at once
 * ({@link #checkForChanges}), as neoskript-proxy does for network variables.
 *
 * <p>Addons call this; they don't implement it.
 */
@Experimental
public interface VariableDatabase {

    /**
     * Its name in config.yml.
     *
     * @return the name
     */
    String name();

    /**
     * Its {@code pattern}: a regular expression a variable's whole name must match to be saved here, unless a
     * database above it matches first.
     *
     * @return the pattern as written
     */
    String pattern();

    /**
     * Whether it's an SQL database with {@code monitor-changes: true}, so other servers can share its variables.
     *
     * @return whether it's shared
     */
    boolean shared();

    /**
     * Whether its variables are loaded and writes to it go through now: {@code false} while it's down, or before it
     * has been reached.
     *
     * @return whether it's available
     */
    boolean available();

    /**
     * Where it stands, as {@code /sk info} says it: its type, and whether it's connected, down or not reached, and why.
     *
     * @return its state, for people
     */
    String state();

    /**
     * Which database it is, the same on every server that uses it and different for any other: from its type, where
     * it is and its table, never the user or password.
     *
     * @return its id, or {@code null} if it isn't {@linkplain #shared() shared}
     */
    String id();

    /**
     * Runs {@code listener} each time this server has written changes to it, on the database's own thread, never the
     * server's. It must be quick: hand anything slow to another thread.
     *
     * @param listener what to run
     * @return runs to stop it
     */
    Runnable whenWritten(Runnable listener);

    /**
     * Reads the changes other servers have written as soon as it can, rather than at the next
     * {@code monitor-interval}. Many calls close together are one read. Does nothing if it isn't
     * {@linkplain #shared() shared}, or while it's down: it reads what it missed once it's back.
     */
    void checkForChanges();
}
