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
import java.util.TreeMap;

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
    private final List<Entry<T>> unindexed = new ArrayList<>();
    private int longestLiteral;
    private int size;

    public void add(CompiledPattern pattern, T owner) {
        Entry<T> entry = new Entry<>(size++, pattern, owner);
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
        return size;
    }

    /** The patterns that could match {@code line}, in registration order. */
    public List<Entry<T>> candidates(String line) {
        String text = line.strip().toLowerCase(Locale.ROOT);
        int wordEnd = 0;
        while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd))) {
            wordEnd++;
        }
        TreeMap<Integer, Entry<T>> found = new TreeMap<>();
        for (int end = 1; end <= Math.min(wordEnd, longestLiteral); end++) {
            List<Entry<T>> entries = byLiteral.get(text.substring(0, end));
            if (entries != null) {
                entries.forEach(e -> found.put(e.order(), e));
            }
        }
        unindexed.forEach(e -> found.put(e.order(), e));
        return new ArrayList<>(found.values());
    }
}
