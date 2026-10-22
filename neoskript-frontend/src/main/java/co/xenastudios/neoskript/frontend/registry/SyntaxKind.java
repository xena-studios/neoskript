/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

/** What a syntax element is, which decides where in a script it can appear. */
public enum SyntaxKind {
    /** Top-level, e.g. {@code test "x":} or {@code auto reload}. */
    STRUCTURE,
    /** What follows {@code on} in an event header. */
    EVENT,
    /** A line ending in {@code :} inside a trigger, e.g. {@code if ...:} or {@code loop ...:}. */
    SECTION,
    EFFECT,
    CONDITION,
    EXPRESSION
}
