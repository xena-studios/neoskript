/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * A built-in function, like {@code floor(n: number)}. Arguments are passed by parameter, as for
 * {@link Effect}: an {@code Object[]} for a plural parameter, a single value for a singular one, and
 * {@code null} for an optional parameter that wasn't given.
 */
@FunctionalInterface
public interface Function {

    Object call(Object[] arguments);
}
