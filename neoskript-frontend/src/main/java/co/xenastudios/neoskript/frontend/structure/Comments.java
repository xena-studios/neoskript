/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Removes comments from a line of code.
 *
 * <p>{@code #} starts a comment in code, including in {@code %expressions%} inside strings and variable
 * names. It's an ordinary character inside a string or a variable name, so {@code "#ff0000"} and
 * {@code {##x}} keep theirs. Outside strings and variable names, {@code ##} stands for one {@code #}.
 */
final class Comments {

    /** A line containing only this opens or closes a block comment. */
    static final String BLOCK_DELIMITER = "###";

    private enum Context {
        CODE,
        STRING,
        VARIABLE,
        EXPRESSION
    }

    private Comments() {}

    /** Returns {@code line} without its comment. Unclosed strings or variables are left for the parser. */
    static String strip(String line) {
        StringBuilder out = new StringBuilder(line.length());
        Deque<Context> contexts = new ArrayDeque<>();
        contexts.push(Context.CODE);
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            switch (contexts.peek()) {
                case CODE, EXPRESSION -> {
                    if (c == '#') {
                        if (contexts.peek() == Context.CODE && next == '#') {
                            out.append('#');
                            i++;
                            continue;
                        }
                        return out.toString();
                    }
                    if (c == '"') {
                        contexts.push(Context.STRING);
                    } else if (c == '{') {
                        contexts.push(Context.VARIABLE);
                    } else if (c == '%' && contexts.peek() == Context.EXPRESSION) {
                        contexts.pop();
                    }
                }
                case STRING -> {
                    if (c == '"') {
                        if (next == '"') {
                            out.append(c).append(next);
                            i++;
                            continue;
                        }
                        contexts.pop();
                    } else if (c == '%') {
                        if (next == '%') {
                            out.append(c).append(next);
                            i++;
                            continue;
                        }
                        contexts.push(Context.EXPRESSION);
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
            out.append(c);
        }
        return out.toString();
    }
}
