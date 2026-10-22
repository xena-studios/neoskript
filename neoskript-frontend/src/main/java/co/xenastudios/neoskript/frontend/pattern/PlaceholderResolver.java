/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
}
