/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the patterns that could match a line without trying all of them. Patterns are filed under the
 * literals they must start with; a line is only tried against patterns filed under a prefix of its first
 * word, plus the patterns that can start with a placeholder. Results keep registration order, which is
 * the order Skript tries syntax in.
 *
 * @param <T> what each pattern belongs to, e.g. a syntax element
 */
public final class PatternIndex<T> {

    public record Entry<T>(int order, CompiledPattern pattern, T owner) {}

    private final Map<String, List<Entry<T>>> byLiteral = new HashMap<>();
    private final List<Entry<T>> all = new ArrayList<>();
    private final List<Entry<T>> unindexed = new ArrayList<>();
    private int longestLiteral;

    public void add(CompiledPattern pattern, T owner) {
        Entry<T> entry = new Entry<>(all.size(), pattern, owner);
        all.add(entry);
        if (pattern.leadingLiterals() == null) {
            unindexed.add(entry);
            return;
        }
        for (String literal : pattern.leadingLiterals()) {
            byLiteral.computeIfAbsent(literal, k -> new ArrayList<>()).add(entry);
            longestLiteral = Math.max(longestLiteral, literal.length());
        }
    }

    public int size() {
        return all.size();
    }

    /** Every entry, in registration order. Entry {@code i} has order {@code i}. */
    public List<Entry<T>> all() {
        return all;
    }

    /** The entries that can start with a placeholder, in registration order. */
    public List<Entry<T>> unindexed() {
        return unindexed;
    }

    /** The patterns that could match {@code line}, in registration order. */
    public List<Entry<T>> candidates(String line) {
        return merge(indexed(line), unindexed);
    }

    /** The entries filed under a prefix of the first word of {@code line}, in registration order. */
    public List<Entry<T>> indexed(String line) {
        String text = line.strip().toLowerCase(Locale.ROOT);
        int wordEnd = 0;
        while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd))) {
            wordEnd++;
        }
        List<Entry<T>> found = List.of();
        for (int end = 1; end <= Math.min(wordEnd, longestLiteral); end++) {
            List<Entry<T>> entries = byLiteral.get(text.substring(0, end));
            if (entries != null) {
                found = found.isEmpty() ? entries : merge(found, entries);
            }
        }
        return found;
    }

    /** Merges two lists sorted by order, dropping duplicates. */
    public static <T> List<Entry<T>> merge(List<Entry<T>> a, List<Entry<T>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        List<Entry<T>> merged = new ArrayList<>(a.size() + b.size());
        int i = 0;
        int j = 0;
        while (i < a.size() || j < b.size()) {
            Entry<T> next;
            if (j >= b.size() || (i < a.size() && a.get(i).order() <= b.get(j).order())) {
                next = a.get(i++);
            } else {
                next = b.get(j++);
            }
            if (merged.isEmpty() || merged.getLast().order() != next.order()) {
                merged.add(next);
            }
        }
        return merged;
    }
}
