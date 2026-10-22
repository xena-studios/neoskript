/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A compiled syntax pattern. Matching backtracks through the pattern: choices are tried in order,
 * optional parts are tried before being skipped, and placeholders try their shortest text first. The first
 * way that covers the whole input wins.
 */
public final class CompiledPattern {

    private final String source;
    private final PatternElement root;
    private final int placeholderCount;
    private final int regexCount;
    private final Set<String> leadingLiterals;

    CompiledPattern(String source, PatternElement root, int placeholderCount, int regexCount) {
        this.source = source;
        this.root = root;
        this.placeholderCount = placeholderCount;
        this.regexCount = regexCount;
        First first = first(root);
        this.leadingLiterals =
                first.anything || first.canBeEmpty ? null : Collections.unmodifiableSet(first.literals);
    }

    public String source() {
        return source;
    }

    public PatternElement root() {
        return root;
    }

    public int placeholderCount() {
        return placeholderCount;
    }

    /**
     * The lower-case literals any match must start with, or {@code null} if a match can start with a
     * placeholder or regex. {@link PatternIndex} uses this to skip patterns that can't match a line.
     */
    public Set<String> leadingLiterals() {
        return leadingLiterals;
    }

    /** Matches the whole of {@code input}, ignoring surrounding whitespace. */
    public MatchResult match(String input, PlaceholderResolver resolver) {
        String text = input.strip();
        State end = new Matcher(text, resolver).match(root, 0, State.EMPTY,
                (pos, state) -> pos == text.length() ? state : null);
        return end == null ? null : end.toResult(placeholderCount, regexCount);
    }

    @Override
    public String toString() {
        return source;
    }

    /** Parse state collected so far. Immutable, so backtracking needs no undo. */
    private record State(int mark, Tags tags, Capture captures) {

        static final State EMPTY = new State(0, null, null);

        State withAlternative(PatternElement.Alternative alternative) {
            if (alternative.mark() == 0 && alternative.tag() == null) {
                return this;
            }
            return new State(mark ^ alternative.mark(),
                    alternative.tag() == null ? tags : new Tags(alternative.tag(), tags), captures);
        }

        State withCapture(int kind, int index, int start, int end, Object value) {
            return new State(mark, tags, new Capture(kind, index, start, end, value, captures));
        }

        MatchResult toResult(int placeholders, int regexCount) {
            Object[] values = new Object[placeholders];
            int[][] spans = new int[placeholders][];
            java.util.regex.MatchResult[] regexes = new java.util.regex.MatchResult[regexCount];
            for (Capture c = captures; c != null; c = c.previous) {
                if (c.kind == Capture.PLACEHOLDER) {
                    values[c.index] = c.value;
                    spans[c.index] = new int[] {c.start, c.end};
                } else {
                    regexes[c.index] = (java.util.regex.MatchResult) c.value;
                }
            }
            Set<String> tagSet = new LinkedHashSet<>();
            for (Tags t = tags; t != null; t = t.previous) {
                tagSet.add(t.tag);
            }
            return new MatchResult(mark, Collections.unmodifiableSet(tagSet),
                    Collections.unmodifiableList(Arrays.asList(values)), Collections.unmodifiableList(Arrays.asList(spans)),
                    Collections.unmodifiableList(Arrays.asList(regexes)));
        }
    }

    private record Tags(String tag, Tags previous) {}

    private record Capture(int kind, int index, int start, int end, Object value, Capture previous) {
        static final int PLACEHOLDER = 0;
        static final int REGEX = 1;
    }

    /** What to do after an element matched: returns the final state, or {@code null} to backtrack. */
    @FunctionalInterface
    private interface Continuation {
        State next(int pos, State state);
    }

    private static final class Matcher {

        private final String input;
        private final PlaceholderResolver resolver;

        Matcher(String input, PlaceholderResolver resolver) {
            this.input = input;
            this.resolver = resolver;
        }

        State match(PatternElement element, int pos, State state, Continuation next) {
            return switch (element) {
                case PatternElement.Literal literal -> {
                    String text = literal.text();
                    yield input.regionMatches(true, pos, text, 0, text.length())
                            ? next.next(pos + text.length(), state)
                            : null;
                }
                case PatternElement.Whitespace whitespace -> {
                    int end = pos;
                    while (end < input.length() && Character.isWhitespace(input.charAt(end))) {
                        end++;
                    }
                    if (end > pos || pos == 0 || pos == input.length()
                            || Character.isWhitespace(input.charAt(pos - 1))) {
                        yield next.next(end, state);
                    }
                    yield null;
                }
                case PatternElement.Sequence sequence -> sequence(sequence.elements(), 0, pos, state, next);
                case PatternElement.Choice choice -> {
                    for (PatternElement.Alternative alternative : choice.alternatives()) {
                        State result = match(alternative.body(), pos, state.withAlternative(alternative), next);
                        if (result != null) {
                            yield result;
                        }
                    }
                    yield null;
                }
                case PatternElement.Optional optional -> {
                    State result = match(optional.choice(), pos, state, next);
                    yield result != null ? result : next.next(pos, state);
                }
                case PatternElement.Placeholder placeholder -> {
                    for (int end : Spans.candidateEnds(input, pos)) {
                        Object value = resolver.resolve(input, pos, end, placeholder.spec());
                        if (value != null) {
                            State result = next.next(end,
                                    state.withCapture(Capture.PLACEHOLDER, placeholder.index(), pos, end, value));
                            if (result != null) {
                                yield result;
                            }
                        }
                    }
                    yield null;
                }
                case PatternElement.Regex regex -> {
                    java.util.regex.Matcher m = regex.regex().matcher(input);
                    for (int end = pos + 1; end <= input.length(); end++) {
                        m.region(pos, end);
                        if (m.matches()) {
                            State result = next.next(end,
                                    state.withCapture(Capture.REGEX, regex.index(), pos, end, m.toMatchResult()));
                            if (result != null) {
                                yield result;
                            }
                        }
                    }
                    yield null;
                }
            };
        }

        private State sequence(List<PatternElement> elements, int index, int pos, State state, Continuation next) {
            if (index == elements.size()) {
                return next.next(pos, state);
            }
            return match(elements.get(index), pos, state,
                    (p, s) -> sequence(elements, index + 1, p, s, next));
        }
    }

    /** The literals a match of an element can start with. */
    private record First(Set<String> literals, boolean canBeEmpty, boolean anything) {}

    private static First first(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal ->
                    new First(new HashSet<>(Set.of(literal.text().toLowerCase(Locale.ROOT))), false, false);
            case PatternElement.Whitespace whitespace -> new First(new HashSet<>(), true, false);
            case PatternElement.Placeholder placeholder -> new First(new HashSet<>(), false, true);
            case PatternElement.Regex regex -> new First(new HashSet<>(), false, true);
            case PatternElement.Optional optional -> {
                First inner = first(optional.choice());
                yield new First(inner.literals, true, inner.anything);
            }
            case PatternElement.Choice choice -> {
                Set<String> literals = new HashSet<>();
                boolean canBeEmpty = false;
                boolean anything = false;
                for (PatternElement.Alternative alternative : choice.alternatives()) {
                    First f = first(alternative.body());
                    literals.addAll(f.literals);
                    canBeEmpty |= f.canBeEmpty;
                    anything |= f.anything;
                }
                yield new First(literals, canBeEmpty, anything);
            }
            case PatternElement.Sequence sequence -> {
                Set<String> literals = new HashSet<>();
                boolean anything = false;
                for (PatternElement e : sequence.elements()) {
                    First f = first(e);
                    literals.addAll(f.literals);
                    anything |= f.anything;
                    if (!f.canBeEmpty) {
                        yield new First(literals, false, anything);
                    }
                }
                yield new First(literals, true, anything);
            }
        };
    }
}
