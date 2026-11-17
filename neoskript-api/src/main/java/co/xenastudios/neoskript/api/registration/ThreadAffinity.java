/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

/**
 * Which thread an element has to run on, on Folia, for {@link Registrar#affinity}. On Paper, everything runs on the
 * main thread and this changes nothing.
 */
public enum ThreadAffinity {
    /**
     * The default: the element touches the entities, blocks and locations it's given. An expression or condition
     * given one another thread owns is a runtime error naming the line, and gives nothing (a condition fails); an
     * effect runs on the owner's thread, soon, while the trigger carries on.
     */
    OWNER,
    /**
     * The element only reads what's safe from any thread (names, UUIDs, permissions) or only sends something to a
     * player (a message, a title): it runs where it is, unchecked.
     */
    ANY,
    /**
     * The element changes what only the global thread may (world time, weather, gamerules): from another thread, it
     * runs on the global one, soon, while the trigger carries on.
     */
    GLOBAL
}
