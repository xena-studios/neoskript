/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.List;

/** A parsed expression, covering {@code [start, end)} of its line. */
public sealed interface Expr {

    int start();

    int end();

    /** The id of the type it returns, or {@code object} if that isn't known until run time. */
    String type();

    /** A number, boolean, timespan, time or closed-set literal like a game mode. */
    record Literal(int start, int end, String type, String text) implements Expr {}

    /** A quoted string, with the expressions embedded in it as {@code %...%}. */
    record Text(int start, int end, List<Expr> embedded) implements Expr {

        public Text {
            embedded = List.copyOf(embedded);
        }

        @Override
        public String type() {
            return "string";
        }
    }

    /** {@code {name}}, with the expressions embedded in its name. */
    record Variable(int start, int end, String name, boolean local, boolean list, List<Expr> embedded)
            implements Expr {

        public Variable {
            embedded = List.copyOf(embedded);
        }

        @Override
        public String type() {
            return "object";
        }
    }

    record FunctionCall(int start, int end, String name, List<Expr> arguments, String type) implements Expr {

        public FunctionCall {
            arguments = List.copyOf(arguments);
        }
    }

    /** A registered expression. */
    record Syntax(int start, int end, ParsedSyntax syntax, String type) implements Expr {}

    /** {@code a, b and c} or {@code a or b}. */
    record ListOf(int start, int end, List<Expr> items, boolean and, String type) implements Expr {

        public ListOf {
            items = List.copyOf(items);
        }
    }

    /**
     * Word-like text that might be a literal of a type whose values the frontend can't list, like item or
     * entity names. The standard library resolves or rejects it later.
     */
    record Unresolved(int start, int end, String text) implements Expr {

        @Override
        public String type() {
            return "object";
        }
    }
}
