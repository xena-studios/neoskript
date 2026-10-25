/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.List;

/** A line in a trigger, function or other code section. */
public sealed interface Statement {

    Line line();

    record Effect(Line line, ParsedSyntax effect) implements Statement {}

    /** A condition on its own line: the rest of the section only runs if it's true. */
    record Condition(Line line, ParsedSyntax condition) implements Statement {}

    /** A function called as an effect: {@code heal_all()}. */
    record Call(Line line, Expr.FunctionCall call) implements Statement {}

    /**
     * A section like {@code if ...:} or {@code loop ...:} and the statements in it; or, with no header, a
     * function call whose argument takes the section ({@code f(a worldborder):}).
     */
    record Section(Line line, ParsedSyntax header, List<Statement> body, Expr.FunctionCall call) implements Statement {

        public Section {
            body = List.copyOf(body);
        }

        public Section(Line line, ParsedSyntax header, List<Statement> body) {
            this(line, header, body, null);
        }
    }

    /** {@code if any:} or {@code if all:}, whose lines are conditions. A {@code then:} section follows. */
    /** {@code if all:}/{@code if any:} and its conditions, each on its own line ({@code conditionLines}). */
    record Conditions(Line line, ParsedSyntax header, List<ParsedSyntax> conditions, List<Line> conditionLines)
            implements Statement {

        public Conditions {
            conditions = List.copyOf(conditions);
            conditionLines = List.copyOf(conditionLines);
        }
    }

    /**
     * Skript's test-only {@code parse:} section. Its lines are parsed but problems are captured instead of
     * reported, for the test to check with {@code last parse logs}.
     */
    record Parse(Line line, List<Statement> body, List<Diagnostic> captured) implements Statement {

        public Parse {
            body = List.copyOf(body);
            captured = List.copyOf(captured);
        }
    }

    /** A line that couldn't be parsed. Its error has been reported. */
    record Invalid(Line line) implements Statement {}
}
