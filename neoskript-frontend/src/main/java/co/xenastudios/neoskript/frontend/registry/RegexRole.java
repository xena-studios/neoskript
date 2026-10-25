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
    /** A type name, as in {@code event-<.+>}. */
    TYPE,
    /** A type name or a kind of entity, as in {@code the zombie} or {@code event-creeper}: the event's entity. */
    TYPE_OR_ENTITY,
    /**
     * A kind of entity written alone, as {@code pig} in {@code yaw of pig}: the event's entity, as in Skript,
     * when the words don't name an item.
     */
    BARE_ENTITY,
    /**
     * What a loop value is called, as in {@code loop-<.+>}: {@code value}, {@code index}, {@code counter},
     * {@code iteration} or a type name, then optionally {@code -N}.
     */
    LOOP_VALUE,
    /** A condition, else an effect, else an expression: whatever code Skript's test {@code assert} wraps. */
    CODE,
    /**
     * Text that isn't a condition at all, as in Skript's test {@code assert <.+> to fail}: a condition that
     * can't even be parsed has failed as expected.
     */
    NOT_CONDITION
}
