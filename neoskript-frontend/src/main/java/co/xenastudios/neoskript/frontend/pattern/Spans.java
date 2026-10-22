/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Finds where an expression starting at some position could end. A placeholder's text can't end inside a
 * string, a {@code {variable}}, parentheses or square brackets, and can't run past a closing bracket it
 * didn't open.
 */
final class Spans {

    private enum Context {
        CODE,
        STRING,
        VARIABLE,
        EXPRESSION,
        PARENTHESES,
        BRACKETS
    }

    private Spans() {}

    /**
     * Returns, in increasing order, every index {@code end > start} such that {@code input[start, end)} is
     * balanced and doesn't start or end with whitespace.
     */
    static int[] candidateEnds(String input, int start) {
        int length = input.length();
        if (start >= length || Character.isWhitespace(input.charAt(start))) {
            return new int[0];
        }
        int[] ends = new int[length - start];
        int count = 0;
        Deque<Context> contexts = new ArrayDeque<>();
        contexts.push(Context.CODE);
        for (int i = start; i < length; i++) {
            char c = input.charAt(i);
            char next = i + 1 < length ? input.charAt(i + 1) : '\0';
            switch (contexts.peek()) {
                case CODE, EXPRESSION, PARENTHESES, BRACKETS -> {
                    if (c == '"') {
                        contexts.push(Context.STRING);
                    } else if (c == '{') {
                        contexts.push(Context.VARIABLE);
                    } else if (c == '(') {
                        contexts.push(Context.PARENTHESES);
                    } else if (c == '[') {
                        contexts.push(Context.BRACKETS);
                    } else if (c == ')' || c == ']') {
                        if (contexts.peek() != (c == ')' ? Context.PARENTHESES : Context.BRACKETS)) {
                            return trim(ends, count);
                        }
                        contexts.pop();
                    } else if (c == '%' && contexts.peek() == Context.EXPRESSION) {
                        contexts.pop();
                    } else if (c == '}' && contexts.peek() == Context.CODE) {
                        return trim(ends, count);
                    }
                }
                case STRING -> {
                    if (c == '"') {
                        if (next == '"') {
                            i++;
                        } else {
                            contexts.pop();
                        }
                    } else if (c == '%') {
                        if (next == '%') {
                            i++;
                        } else {
                            contexts.push(Context.EXPRESSION);
                        }
                    }
                }
                case VARIABLE -> {
                    if (c == '}') {
                        contexts.pop();
                    } else if (c == '%') {
                        contexts.push(Context.EXPRESSION);
                    }
                }
            }
            if (contexts.size() == 1 && !Character.isWhitespace(input.charAt(i))) {
                ends[count++] = i + 1;
            }
        }
        return trim(ends, count);
    }

    private static int[] trim(int[] ends, int count) {
        return java.util.Arrays.copyOf(ends, count);
    }
}
