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
 *
 * <p>Before backtracking, {@link #couldMatch(String)} rules out most patterns cheaply: the text has to
 * contain every literal the pattern can't do without, and end with one of the literals it can end with.
 */
public final class CompiledPattern {

    private final String source;
    private final PatternElement root;
    private final int placeholderCount;
    private final int regexCount;
    private final Set<String> leadingLiterals;
    private final Set<String> trailingLiterals;
    private final String[] requiredLiterals;

    CompiledPattern(String source, PatternElement root, int placeholderCount, int regexCount) {
        this.source = source;
        this.root = root;
        this.placeholderCount = placeholderCount;
        this.regexCount = regexCount;
        Edge first = edge(root, true);
        this.leadingLiterals = first.open || first.canBeEmpty ? null : Collections.unmodifiableSet(first.literals);
        Edge last = edge(root, false);
        this.trailingLiterals = last.open || last.canBeEmpty ? null : Collections.unmodifiableSet(last.literals);
        this.requiredLiterals = required(root).toArray(String[]::new);
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

    public int regexCount() {
        return regexCount;
    }

    /**
     * The lower-case literals any match must start with, or {@code null} if a match can start with a
     * placeholder or regex. {@link PatternIndex} uses this to skip patterns that can't match a line.
     */
    public Set<String> leadingLiterals() {
        return leadingLiterals;
    }

    /** Like {@link #leadingLiterals()}, for the end of a match. */
    public Set<String> trailingLiterals() {
        return trailingLiterals;
    }

    /**
     * Whether some part of {@code lowerText} could match: it contains every literal the pattern needs. Use
     * it once per line to rule out patterns for every span in it.
     */
    public boolean couldMatchWithin(String lowerText) {
        for (String literal : requiredLiterals) {
            if (!lowerText.contains(literal)) {
                return false;
            }
        }
        return true;
    }

    /** A quick check that rules out most text this pattern can't match. {@code lowerText} must be stripped. */
    public boolean couldMatch(String lowerText) {
        if (!couldMatchWithin(lowerText)) {
            return false;
        }
        if (trailingLiterals != null) {
            for (String literal : trailingLiterals) {
                if (lowerText.endsWith(literal)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /** Matches the whole of {@code input}, ignoring surrounding whitespace. */
    public MatchResult match(String input, PlaceholderResolver resolver) {
        return match(input, 0, input.length(), resolver);
    }

    /**
     * Matches {@code input[start, end)}, ignoring surrounding whitespace. Spans in the result are positions
     * in {@code input}, so placeholders are resolved against the original line.
     */
    public MatchResult match(String input, int start, int end, PlaceholderResolver resolver) {
        while (start < end && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(input.charAt(end - 1))) {
            end--;
        }
        int stop = end;
        State state = new Matcher(input, start, end, resolver).match(root, start, State.EMPTY,
                (pos, s) -> pos == stop ? s : null);
        return state == null ? null : state.toResult(placeholderCount, regexCount);
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

        State withCapture(boolean regex, int index, int start, int end, Object value) {
            return new State(mark, tags, new Capture(regex, index, start, end, value, captures));
        }

        MatchResult toResult(int placeholders, int regexCount) {
            Object[] values = new Object[placeholders];
            int[][] spans = new int[placeholders][];
            Object[] regexValues = new Object[regexCount];
            int[][] regexSpans = new int[regexCount][];
            for (Capture c = captures; c != null; c = c.previous) {
                if (c.regex) {
                    regexValues[c.index] = c.value;
                    regexSpans[c.index] = new int[] {c.start, c.end};
                } else {
                    values[c.index] = c.value;
                    spans[c.index] = new int[] {c.start, c.end};
                }
            }
            Set<String> tagSet = new LinkedHashSet<>();
            for (Tags t = tags; t != null; t = t.previous) {
                tagSet.add(t.tag);
            }
            return new MatchResult(mark, Collections.unmodifiableSet(tagSet), list(values), list(spans),
                    list(regexValues), list(regexSpans));
        }

        private static <T> List<T> list(T[] array) {
            return Collections.unmodifiableList(Arrays.asList(array));
        }
    }

    private record Tags(String tag, Tags previous) {}

    private record Capture(boolean regex, int index, int start, int end, Object value, Capture previous) {}

    /** What to do after an element matched: returns the final state, or {@code null} to backtrack. */
    @FunctionalInterface
    private interface Continuation {
        State next(int pos, State state);
    }

    private static final class Matcher {

        private final String input;
        private final int start;
        private final int end;
        private final PlaceholderResolver resolver;

        Matcher(String input, int start, int end, PlaceholderResolver resolver) {
            this.input = input;
            this.start = start;
            this.end = end;
            this.resolver = resolver;
        }

        State match(PatternElement element, int pos, State state, Continuation next) {
            return switch (element) {
                case PatternElement.Literal literal -> {
                    String text = literal.text();
                    yield pos + text.length() <= end && input.regionMatches(true, pos, text, 0, text.length())
                            ? next.next(pos + text.length(), state)
                            : null;
                }
                case PatternElement.Whitespace whitespace -> {
                    int after = pos;
                    while (after < end && Character.isWhitespace(input.charAt(after))) {
                        after++;
                    }
                    if (after > pos || pos == start || pos == end || Character.isWhitespace(input.charAt(pos - 1))) {
                        yield next.next(after, state);
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
                case PatternElement.Placeholder placeholder -> placeholder(placeholder, pos, state, next);
                case PatternElement.Regex regex -> {
                    java.util.regex.Matcher m = regex.regex().matcher(input);
                    String followedBy = regex.followedBy();
                    for (int to : regex.matchesCode() ? Spans.candidateEnds(input, pos, end) : allEnds(pos)) {
                        if (followedBy != null && !followedAt(to, followedBy)) {
                            continue;
                        }
                        m.region(pos, to);
                        if (m.matches()) {
                            Object value = resolver.resolveRegex(input, pos, to, regex.index(), m.toMatchResult());
                            if (value != null) {
                                State result = next.next(to, state.withCapture(true, regex.index(), pos, to, value));
                                if (result != null) {
                                    yield result;
                                }
                            }
                        }
                    }
                    yield null;
                }
            };
        }

        private State placeholder(PatternElement.Placeholder placeholder, int pos, State state, Continuation next) {
            String followedBy = placeholder.followedBy();
            for (int to : Spans.candidateEnds(input, pos, end)) {
                if (followedBy != null && !followedAt(to, followedBy)) {
                    continue;
                }
                Object value = resolver.resolve(input, pos, to, placeholder.spec());
                if (value != null) {
                    State result = next.next(to, state.withCapture(false, placeholder.index(), pos, to, value));
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

        private int[] allEnds(int pos) {
            int[] ends = new int[end - pos];
            for (int i = 0; i < ends.length; i++) {
                ends[i] = pos + 1 + i;
            }
            return ends;
        }

        /** Whether {@code literal} comes at {@code pos}, after any whitespace. */
        private boolean followedAt(int pos, String literal) {
            while (pos < end && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
            return pos + literal.length() <= end && input.regionMatches(true, pos, literal, 0, literal.length());
        }

        private State sequence(List<PatternElement> elements, int index, int pos, State state, Continuation next) {
            if (index == elements.size()) {
                return next.next(pos, state);
            }
            return match(elements.get(index), pos, state, (p, s) -> sequence(elements, index + 1, p, s, next));
        }
    }

    /**
     * The literals a match can start (or end) with. {@code open} means a match can start with a
     * placeholder or regex, or be empty, so there's nothing to check.
     */
    private record Edge(Set<String> literals, boolean canBeEmpty, boolean open) {}

    private static Edge edge(PatternElement element, boolean leading) {
        return switch (element) {
            case PatternElement.Literal literal ->
                    new Edge(new HashSet<>(Set.of(literal.text().toLowerCase(Locale.ROOT))), false, false);
            case PatternElement.Whitespace whitespace -> new Edge(new HashSet<>(), true, false);
            case PatternElement.Placeholder placeholder -> new Edge(new HashSet<>(), false, true);
            case PatternElement.Regex regex -> new Edge(new HashSet<>(), false, true);
            case PatternElement.Optional optional -> {
                Edge inner = edge(optional.choice(), leading);
                yield new Edge(inner.literals, true, inner.open);
            }
            case PatternElement.Choice choice -> {
                Set<String> literals = new HashSet<>();
                boolean canBeEmpty = false;
                boolean open = false;
                for (PatternElement.Alternative alternative : choice.alternatives()) {
                    Edge e = edge(alternative.body(), leading);
                    literals.addAll(e.literals);
                    canBeEmpty |= e.canBeEmpty;
                    open |= e.open;
                }
                yield new Edge(literals, canBeEmpty, open);
            }
            case PatternElement.Sequence sequence -> {
                Set<String> literals = new HashSet<>();
                boolean open = false;
                List<PatternElement> elements = sequence.elements();
                for (int i = 0; i < elements.size(); i++) {
                    Edge e = edge(elements.get(leading ? i : elements.size() - 1 - i), leading);
                    literals.addAll(e.literals);
                    open |= e.open;
                    if (!e.canBeEmpty) {
                        yield new Edge(literals, false, open);
                    }
                }
                yield new Edge(literals, true, true);
            }
        };
    }

    /** Lower-case literals that every match contains. */
    private static Set<String> required(PatternElement element) {
        return switch (element) {
            case PatternElement.Literal literal -> new HashSet<>(Set.of(literal.text().toLowerCase(Locale.ROOT)));
            case PatternElement.Sequence sequence -> {
                Set<String> all = new HashSet<>();
                sequence.elements().forEach(e -> all.addAll(required(e)));
                yield all;
            }
            case PatternElement.Choice choice -> {
                Set<String> common = null;
                for (PatternElement.Alternative alternative : choice.alternatives()) {
                    Set<String> r = required(alternative.body());
                    if (common == null) {
                        common = r;
                    } else {
                        common.retainAll(r);
                    }
                }
                yield common == null ? new HashSet<>() : common;
            }
            default -> new HashSet<>();
        };
    }
}
