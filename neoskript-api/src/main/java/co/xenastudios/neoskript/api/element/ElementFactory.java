/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/** Creates an element for one place it's used in a script, when the script loads. */
@FunctionalInterface
public interface ElementFactory<T> {

    T create(ElementInfo info);
}
