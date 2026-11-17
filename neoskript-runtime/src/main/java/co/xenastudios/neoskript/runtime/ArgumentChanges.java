/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.Arrays;

/**
 * What an addon element set its variable arguments to while it ran, as a Skript addon changes its arguments
 * ({@code component replace "a" with "b" in {_text}}): NeoSkriptCompat records each change, and the compiled code
 * writes it to the variable once the element has run. Not part of the addon API.
 */
public final class ArgumentChanges {

    /** No change to the argument. */
    public static final Object NONE = new Object();

    private static final ThreadLocal<Object[]> CHANGES = new ThreadLocal<>();

    private ArgumentChanges() {}

    /** An element with {@code count} arguments starts running: none changed yet. */
    public static void begin(int count) {
        Object[] changes = new Object[count];
        Arrays.fill(changes, NONE);
        CHANGES.set(changes);
    }

    /** The element set argument {@code index} to {@code value}: a value, an array for a list, or null to delete. */
    public static void put(int index, Object value) {
        Object[] changes = CHANGES.get();
        if (changes != null && index >= 0 && index < changes.length) {
            changes[index] = value;
        }
    }

    /** What argument {@code index} was set to, or {@link #NONE}: once. */
    public static Object take(int index) {
        Object[] changes = CHANGES.get();
        if (changes == null || index < 0 || index >= changes.length) {
            return NONE;
        }
        Object value = changes[index];
        changes[index] = NONE;
        return value;
    }

    /** The changes are written: nothing's kept for the thread. */
    public static void end() {
        CHANGES.remove();
    }
}
