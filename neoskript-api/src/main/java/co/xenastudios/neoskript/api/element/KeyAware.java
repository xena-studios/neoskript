/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * An expression that keeps its values' indices, as {@code first 2 elements of keyed {_list::*}} does. An
 * argument written keyed (or a list variable, where indices are wanted anyway, as in a loop) is passed as
 * {@code co.xenastudios.neoskript.runtime.KeyedValues}, and the expression may return one; where only values
 * are wanted, the indices are dropped.
 */
public interface KeyAware {

    /** Whether list variables are always passed with their indices, as {@code indices of the value 1 in {_x::*}} needs. */
    default boolean implicitKeys() {
        return false;
    }

    /**
     * Whether the indices it gives are kept even where it isn't written {@code keyed}: setting a list to it
     * keeps them, as Skript does for expressions whose keys it recommends.
     */
    default boolean keysRecommended() {
        return false;
    }
}
