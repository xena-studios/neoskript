/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * Values with the indices they came from, as a {@link KeyAware} expression is given a list variable: each value's
 * index is at the same place in {@link #keys()}. With {@link KeyAware#recursiveKeys()}, an index may be a path
 * through nested lists ({@code home::x}). Only NeoSkript makes these; an addon reads them.
 */
@Experimental
public interface Keyed {

    /**
     * The values, none {@code null}.
     *
     * @return the values
     */
    Object[] values();

    /**
     * Each value's index, or its path below the list, with {@code ::} between the parts.
     *
     * @return the indices
     */
    String[] keys();
}
