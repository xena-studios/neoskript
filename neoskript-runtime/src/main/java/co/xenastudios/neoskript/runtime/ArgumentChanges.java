/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.TriggerContext;
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

    /**
     * Runs {@code effect} with {@code count} arguments on this thread, recording what it sets them to: by index, then
     * at {@code count} what it threw, if anything ({@link #rethrow}). The changes of an effect that runs inside it (a
     * trigger its event fires) are their own, and what was recorded around it is back once it's run.
     */
    public static Object[] execute(Effect effect, TriggerContext context, Object[] arguments, int count) {
        Object[] previous = CHANGES.get();
        Object[] changes = new Object[count + 1];
        Arrays.fill(changes, 0, count, NONE);
        CHANGES.set(changes);
        try {
            effect.execute(context, arguments);
        } catch (Throwable thrown) {
            // Written first, as Skript's addons change their arguments as they go.
            changes[count] = thrown;
        } finally {
            if (previous == null) {
                CHANGES.remove();
            } else {
                CHANGES.set(previous);
            }
        }
        return changes;
    }

    /**
     * The element set argument {@code index} to {@code value}: a value, an array for a list, or null to delete. Kept
     * only while the effect runs on this thread.
     */
    public static void put(int index, Object value) {
        Object[] changes = CHANGES.get();
        if (changes != null && index >= 0 && index < changes.length - 1) {
            changes[index] = value;
        }
    }

    /** Throws what the effect threw, once its changes are written. */
    public static void rethrow(Object[] changes) {
        if (changes[changes.length - 1] instanceof Throwable thrown) {
            ArgumentChanges.<RuntimeException>sneaky(thrown);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneaky(Throwable thrown) throws T {
        throw (T) thrown;
    }
}
