/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A successful match of a pattern against a line.
 *
 * @param mark the parse marks of the alternatives used, combined with XOR as Skript does
 * @param tags the parse tags of the alternatives used
 * @param values what each placeholder resolved to, by placeholder index; {@code null} where an optional
 *     part containing the placeholder wasn't used
 * @param spans the {@code [start, end)} of each placeholder's text, or {@code null} where it wasn't used
 * @param regexValues what each regex's text resolved to (see {@link PlaceholderResolver#resolveRegex}), by
 *     regex index; {@code null} where it wasn't used
 * @param regexSpans the {@code [start, end)} of each regex's text, or {@code null} where it wasn't used
 */
public record MatchResult(int mark, Set<String> tags, List<Object> values, List<int[]> spans,
        List<Object> regexValues, List<int[]> regexSpans) {

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    @Override
    public String toString() {
        return "MatchResult[mark=" + mark + ", tags=" + tags + ", values=" + values + ", spans="
                + spans.stream().map(s -> s == null ? "null" : Arrays.toString(s)).toList() + "]";
    }
}
