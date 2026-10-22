/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * An effect, like {@code send %objects% [to %commandsenders%]}. The compiler evaluates the placeholders
 * and passes their values in {@code arguments}, by placeholder index: an {@code Object[]} for a plural
 * placeholder, a single value (or {@code null}) for a singular one, and {@code null} for a placeholder
 * in an optional part that wasn't used.
 */
@FunctionalInterface
public interface Effect {

    void execute(TriggerContext context, Object[] arguments);
}
