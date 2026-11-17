/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * Where Skript's parser tries a syntax element, as its priority levels: an element of a lower level is tried before
 * one of a higher level, and among one level, Skript's own before an addon's ({@link Registrar#priority}). Experimental,
 * as it's for addons whose syntax is a Skript addon's, to be read as that addon's is.
 */
@Experimental
public enum SyntaxPriority {
    /** Skript's {@code SIMPLE}: an expression whose patterns are mostly words. */
    SIMPLE,
    /** Skript's {@code EVENT}: an event value expression. */
    EVENT_VALUE,
    /** Skript's {@code COMBINED}: what most effects, conditions and expressions with placeholders are. */
    COMBINED,
    /** Skript's {@code PROPERTY}: a property expression, {@code [the] x of %owners%}. */
    PROPERTY,
    /** Skript's {@code PATTERN_MATCHES_EVERYTHING}: a pattern that matches almost anything, as {@code %a% %b%}. */
    PATTERN_MATCHES_EVERYTHING
}
