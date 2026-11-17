/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * A {@code loop-<name>} an addon gives for loops over one of its expressions, as skript-yaml's {@code loop-node}
 * is the full path of each key in {@code loop yaml node keys "x" from "config"}. Registered with
 * {@link co.xenastudios.neoskript.api.registration.Registrar#loopValue}.
 */
@FunctionalInterface
public interface LoopValue {

    /**
     * The value, each time it's used in the loop.
     * @param context the running trigger
     * @param arguments the looped expression's arguments, evaluated again now, as for {@link Expression#get}
     * @param current the loop's current value, as {@code loop-value} gives it
     * @return one value, an {@code Object[]} of several, or {@code null} for none
     */
    Object get(TriggerContext context, Object[] arguments, Object current);
}
