/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

import java.util.Set;

/**
 * How a syntax element was matched, for its factory to set it up.
 *
 * @param pattern the index of the pattern that matched
 * @param mark the parse marks of the alternatives used ({@code 2¦text}), combined with XOR
 * @param tags the parse tags of the alternatives used ({@code tag:text})
 * @param orArguments a bit for each argument written as an or-list ({@code 1, 2 or 3}), lowest bit first
 * @param listArguments a bit for each argument written as a list: a list variable or several values
 */
public record ElementInfo(int pattern, int mark, Set<String> tags, long orArguments, long listArguments) {

    public ElementInfo {
        tags = Set.copyOf(tags);
    }

    public ElementInfo(int pattern, int mark, Set<String> tags) {
        this(pattern, mark, tags, 0, 0);
    }

    /** Whether argument {@code index} was written as a list ({@code {_x::*}}, {@code a and b}), not one value. */
    public boolean isList(int index) {
        return index < 64 && (listArguments & (1L << index)) != 0;
    }

    /**
     * Whether argument {@code index} means all its values ({@code a and b}, a list variable), rather than any
     * one of them ({@code a or b}), as conditions check them.
     */
    public boolean and(int index) {
        return index >= 64 || (orArguments & (1L << index)) == 0;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
