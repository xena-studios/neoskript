/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where strings, variables and parentheses end. Strings can embed {@code %expressions%}, which can
 * contain strings and variables of their own; {@code ""} and {@code %%} are escapes inside strings.
 */
final class Scanner {

    private Scanner() {}

    /**
     * Returns the index just after the string opened by the quote at {@code open}, or -1 if it doesn't
     * close before {@code limit}. Adds the {@code [start, end)} of each embedded expression to
     * {@code embedded} if it isn't null.
     */
    static int stringEnd(String s, int open, int limit, List<int[]> embedded) {
        int i = open + 1;
        while (i < limit) {
            char c = s.charAt(i);
            char next = i + 1 < limit ? s.charAt(i + 1) : '\0';
            if (c == '"') {
                if (next == '"') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            if (c == '%') {
                if (next == '%') {
                    i += 2;
                    continue;
                }
                int close = expressionEnd(s, i + 1, limit);
                if (close < 0) {
                    return -1;
                }
                if (embedded != null) {
                    embedded.add(new int[] {i + 1, close});
                }
                i = close + 1;
                continue;
            }
            i++;
        }
        return -1;
    }

    /** The index of the {@code %} closing an embedded expression that starts at {@code start}, or -1. */
    static int expressionEnd(String s, int start, int limit) {
        int i = start;
        while (i < limit) {
            char c = s.charAt(i);
            if (c == '%') {
                return i;
            }
            if (c == '"') {
                i = stringEnd(s, i, limit, null);
            } else if (c == '{') {
                i = braceEnd(s, i, limit, null);
            } else {
                i++;
                continue;
            }
            if (i < 0) {
                return -1;
            }
        }
        return -1;
    }

    /** The index just after the {@code }} closing the variable opened at {@code open}, or -1. */
    static int braceEnd(String s, int open, int limit, List<int[]> embedded) {
        int i = open + 1;
        while (i < limit) {
            char c = s.charAt(i);
            if (c == '}') {
                return i + 1;
            }
            if (c == '%' && i + 1 < limit && s.charAt(i + 1) == '%') {
                i += 2;
            } else if (c == '%') {
                int close = expressionEnd(s, i + 1, limit);
                if (close < 0) {
                    return -1;
                }
                if (embedded != null) {
                    embedded.add(new int[] {i + 1, close});
                }
                i = close + 1;
            } else if (c == '{') {
                i = braceEnd(s, i, limit, embedded);
                if (i < 0) {
                    return -1;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    /** The index just after the {@code )} closing the parenthesis at {@code open}, or -1. */
    static int parenEnd(String s, int open, int limit) {
        int depth = 0;
        int i = open;
        while (i < limit) {
            char c = s.charAt(i);
            if (c == '"') {
                i = stringEnd(s, i, limit, null);
            } else if (c == '{') {
                i = braceEnd(s, i, limit, null);
            } else {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    return i + 1;
                }
                i++;
                continue;
            }
            if (i < 0) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Splits {@code s[start, end)} at top-level occurrences of {@code separator}, outside strings,
     * variables and brackets. Returns the {@code [start, end)} of each piece.
     */
    static List<int[]> split(String s, int start, int end, char separator) {
        List<int[]> pieces = new ArrayList<>();
        int depth = 0;
        int pieceStart = start;
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            if (c == '"') {
                i = stringEnd(s, i, end, null);
            } else if (c == '{') {
                i = braceEnd(s, i, end, null);
            } else {
                if (c == '(' || c == '[') {
                    depth++;
                } else if (c == ')' || c == ']') {
                    depth--;
                } else if (c == separator && depth == 0) {
                    pieces.add(new int[] {pieceStart, i});
                    pieceStart = i + 1;
                }
                i++;
                continue;
            }
            if (i < 0) {
                return List.of(new int[] {start, end});
            }
        }
        pieces.add(new int[] {pieceStart, end});
        return pieces;
    }

    /**
     * The top-level list separators in {@code s[start, end)}: {@code ,}, {@code and}, {@code or} and
     * {@code nor} (with or without a comma before), outside strings, variables and brackets. Returns the
     * {@code [start, end)} of each. {@code and[0]} is set to whether the list uses {@code and} (or only
     * commas) rather than {@code or}.
     */
    static List<int[]> listSeparators(String s, int start, int end, boolean[] and) {
        List<int[]> separators = new ArrayList<>();
        int depth = 0;
        boolean usesOr = false;
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            if (c == '"') {
                i = stringEnd(s, i, end, null);
                if (i < 0) {
                    return List.of();
                }
                continue;
            }
            if (c == '{') {
                i = braceEnd(s, i, end, null);
                if (i < 0) {
                    return List.of();
                }
                continue;
            }
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (depth == 0 && (c == ',' || c == ' ')) {
                int separatorEnd = c == ',' ? i + 1 : -1;
                int after = skipSpaces(s, c == ',' ? i + 1 : i, end);
                for (String word : new String[] {"and", "or", "nor"}) {
                    if (wordAt(s, after, end, word) && after + word.length() < end
                            && s.charAt(after + word.length()) == ' ') {
                        separatorEnd = after + word.length();
                        usesOr |= !word.equals("and");
                    }
                }
                if (separatorEnd > 0) {
                    separators.add(new int[] {i, separatorEnd});
                    i = separatorEnd;
                    continue;
                }
            }
            i++;
        }
        and[0] = !usesOr;
        return separators;
    }

    private static int skipSpaces(String s, int i, int end) {
        while (i < end && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean wordAt(String s, int i, int end, String word) {
        return i + word.length() <= end && s.regionMatches(true, i, word, 0, word.length())
                && (i + word.length() == end || !Character.isLetterOrDigit(s.charAt(i + word.length())));
    }
}
