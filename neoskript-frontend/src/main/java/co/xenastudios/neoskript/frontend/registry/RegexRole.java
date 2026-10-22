/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

/**
 * How to check the text a {@code <regex>} in a pattern matched. Skript elements parse that text
 * themselves (the condition in {@code if <.+>}, the effect in {@code <.+> if <.+>}), so the registry says
 * what it is.
 */
public enum RegexRole {
    /** Any text the regex matches. */
    ANY,
    CONDITION,
    EFFECT,
    EXPRESSION,
    /** A type name, as in {@code event-<.+>} or {@code loop-<.+>}. */
    TYPE,
    /** A condition, else an effect, else an expression: whatever code Skript's test {@code assert} wraps. */
    CODE
}
