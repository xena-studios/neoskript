/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * An expression that keeps its values' indices, as {@code first 2 elements of keyed {_list::*}} does. An
 * argument written keyed (or a list variable, where indices are wanted anyway, as in a loop) is passed as
 * {@code co.xenastudios.neoskript.runtime.KeyedValues}, and the expression may return one; where only values
 * are wanted, the indices are dropped. A pausing effect may be key-aware too, for its arguments. Experimental until keyed values have a type in the API.
 */
@Experimental
public interface KeyAware {

    /**
     * Whether list variables are always passed with their indices, as {@code indices of the value 1 in {_x::*}}
     * needs.
     *
     * @return whether they are
     */
    default boolean implicitKeys() {
        return false;
    }

    /**
     * Whether the indices it gives are kept even where it isn't written {@code keyed}: setting a list to it
     * keeps them, as Skript does for expressions whose keys it recommends.
     *
     * @return whether they are
     */
    default boolean keysRecommended() {
        return false;
    }

    /**
     * Whether a list variable is passed with every value under it, each at its path ({@code {_rows::1::2}} at
     * {@code 1::2}), as {@code recursive {_rows::*}} gives them, rather than only the values directly in it. For
     * skript-db's batch arguments, a list of lists. Implies {@link #implicitKeys}.
     *
     * @return whether they are
     */
    default boolean recursiveKeys() {
        return false;
    }
}
