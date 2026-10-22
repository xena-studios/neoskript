/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * An expression. Arguments are passed as for {@link Effect}. Returns a single value, an {@code Object[]}
 * for several, or {@code null} for none.
 */
@FunctionalInterface
public interface Expression {

    Object get(TriggerContext context, Object[] arguments);
}
