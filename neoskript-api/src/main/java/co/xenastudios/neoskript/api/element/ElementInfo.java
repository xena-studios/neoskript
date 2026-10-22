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
 */
public record ElementInfo(int pattern, int mark, Set<String> tags) {

    public ElementInfo {
        tags = Set.copyOf(tags);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
