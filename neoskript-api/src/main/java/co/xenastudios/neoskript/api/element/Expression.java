/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * An expression. Arguments are passed as for {@link Effect}. Returns a single value, an {@code Object[]}
 * for several, or {@code null} for none. One that {@code set} and the like can change also implements
 * {@link Changeable}.
 */
@FunctionalInterface
public interface Expression {

    /**
     * The expression's values.
     *
     * @param context the running trigger
     * @param arguments the placeholders' values, by index
     * @return one value, an {@code Object[]} of several, or {@code null} for none
     */
    Object get(TriggerContext context, Object[] arguments);
}
