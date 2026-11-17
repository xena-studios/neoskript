/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * An expression that keeps its values' indices, as {@code first 2 elements of keyed {_list::*}} does. An
 * argument written keyed (or a list variable, where indices are wanted anyway, as in a loop) is passed as
 * {@code co.xenastudios.neoskript.runtime.KeyedValues}, which is {@link Keyed}, and the expression may return one;
 * where only values are wanted, the indices are dropped. Experimental until keyed values have a type in the API.
 *
 * <p>It may also return a {@link java.util.Map}: each key is an index, and a value that's a map, a collection or an
 * array makes a list under its index, as a pausing effect's stored result does ({@link PausingEffect}).
 * {@code {"home": {"x": 10}}} is the value 10 at {@code home::x}, so setting {@code {_data::*}} to it sets
 * {@code {_data::home::x}}.
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
     * Whether list variables are passed with every value under them, nested lists included, each at its path below
     * the list ({@code home::x} for {@code {_data::home::x}}), as {@code recursive keyed {_data::*}} gives them. Only
     * for list variables written as an argument of this expression; it implies {@link #implicitKeys()}.
     *
     * @return whether they are
     */
    default boolean recursiveKeys() {
        return false;
    }
}
