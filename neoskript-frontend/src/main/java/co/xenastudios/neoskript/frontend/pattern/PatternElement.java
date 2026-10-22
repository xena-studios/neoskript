/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.List;

/** A compiled piece of a syntax pattern. */
public sealed interface PatternElement {

    /** Text matched without regard to case. Never contains whitespace. */
    record Literal(String text) implements PatternElement {}

    /**
     * A space in the pattern. It matches one or more whitespace characters, or nothing at the start or end
     * of the input or right after other whitespace, so {@code [the] player} matches {@code player}.
     */
    record Whitespace() implements PatternElement {}

    record Sequence(List<PatternElement> elements) implements PatternElement {

        public Sequence {
            elements = List.copyOf(elements);
        }
    }

    /** {@code (a|b)}: the first alternative that leads to a full match wins. */
    record Choice(List<Alternative> alternatives) implements PatternElement {

        public Choice {
            alternatives = List.copyOf(alternatives);
        }
    }

    /**
     * One alternative of a choice. {@code mark} comes from {@code 2¦text} and {@code tag} from
     * {@code name:text} or {@code :text}; both are recorded in the match when the alternative is used.
     */
    record Alternative(PatternElement body, int mark, String tag) {}

    /** {@code [...]}: tries to match the choice, then tries skipping it. */
    record Optional(Choice choice) implements PatternElement {}

    /**
     * {@code %type%}, numbered from 0 in pattern order. The matcher uses two hints to avoid parsing text
     * that can't lead to a match: {@code followedBy}, the literals one of which comes next in the same
     * sequence (after optional whitespace), or null if anything can; and {@code atEnd}, whether nothing can
     * follow the placeholder, so it has to cover the rest of the input.
     */
    record Placeholder(int index, PlaceholderSpec spec, List<String> followedBy, boolean atEnd)
            implements PatternElement {}

    /**
     * {@code <regex>}, numbered from 0 in pattern order. {@code followedBy} works as for placeholders.
     * {@code matchesCode} is true for {@code <.+>}-style regexes that match a piece of code (a condition,
     * an expression): those can only end where the code is balanced, which saves trying every position.
     */
    record Regex(int index, java.util.regex.Pattern regex, List<String> followedBy, boolean matchesCode,
            boolean atEnd) implements PatternElement {}
}
