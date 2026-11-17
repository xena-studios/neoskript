/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * Creates an element for one place it's used in a script, when the script loads. It may be called more than once
 * for one place, and on whichever thread loads scripts, so it mustn't have side effects.
 *
 * @param <T> the kind of element it makes
 */
@FunctionalInterface
public interface ElementFactory<T> {

    /**
     * Makes the element.
     *
     * @param info how the element was matched there
     * @return the element
     * @throws SyntaxError if it matched but can't be used as written
     */
    T create(ElementInfo info);
}
