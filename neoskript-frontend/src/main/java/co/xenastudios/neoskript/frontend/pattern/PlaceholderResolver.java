/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.pattern;

/**
 * Parses the text a placeholder might cover. The matcher calls this for each candidate span, shortest
 * first, so implementations should memoize by span and spec.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * Returns what {@code input[start, end)} parses to as {@code spec}, or {@code null} if it doesn't.
     * The span never starts or ends with whitespace and is balanced (see {@link Spans}).
     */
    Object resolve(String input, int start, int end, PlaceholderSpec spec);

    /**
     * Checks the text a {@code <regex>} matched, e.g. that the {@code <.+>} in {@code if <.+>} is a
     * condition. Returns what it parses to, or {@code null} to reject it. By default any match is accepted.
     */
    /**
     * Whether {@code value}, what a placeholder resolved to, is Skript's unparsed literal that no type's parser
     * takes. A placeholder before it then tries longer text, as Skript's {@code TypePatternElement} does, and the
     * first match with one is kept only if none comes without.
     */
    default boolean unparsed(Object value) {
        return false;
    }

    default Object resolveRegex(String input, int start, int end, int index, java.util.regex.MatchResult match) {
        return match;
    }
}
