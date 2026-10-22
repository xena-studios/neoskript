/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.frontend.registry.RegexRole;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import java.util.List;

/**
 * The syntax the compiler implements itself rather than calling an element: control flow, changing
 * variables, loop values and arithmetic. Patterns follow Skript's. {@link TriggerCompiler} relies on
 * the pattern order here.
 */
final class CoreSyntax {

    static final String CONDITIONAL = "SecConditional";
    static final String LOOP = "SecLoop";
    static final String WHILE = "SecWhile";
    static final String EXIT = "EffExit";
    static final String CONTINUE = "EffContinue";
    static final String RETURN = "EffReturn";
    static final String CHANGE = "EffChange";
    static final String LOOP_VALUE = "ExprLoopValue";
    static final String ARITHMETIC = "ExprArithmetic";

    /** {@link #CHANGE} patterns, by index. */
    enum ChangeMode {
        SET(0, 1),
        ADD(1, 0),
        INCREASE(0, 1),
        REMOVE_ALL(1, 0),
        REMOVE(1, 0),
        REDUCE(0, 1),
        DELETE(0, -1);

        final int target;
        final int value;

        ChangeMode(int target, int value) {
            this.target = target;
            this.value = value;
        }
    }

    /** {@link #ARITHMETIC} operators, by pattern index, from the lowest precedence. */
    static final char[] OPERATORS = {'+', '-', '*', '/', '^'};

    private CoreSyntax() {}

    static void register(Library library) {
        List<RegexRole> condition = List.of(RegexRole.CONDITION);
        library.core(SyntaxKind.SECTION, CONDITIONAL, null, condition,
                "else",
                "else [parse] if <.+>",
                "else [parse] if (any|at least one [of])",
                "else [parse] if [all]",
                "[parse] if (any|at least one [of])",
                "[parse] if [all]",
                "[parse] if <.+>",
                "then [run]",
                "implicit:<.+>");
        library.core(SyntaxKind.SECTION, LOOP, null, List.of(), "loop %objects%");
        library.core(SyntaxKind.SECTION, WHILE, null, condition, "[do:do] while <.+>");

        library.core(SyntaxKind.EFFECT, EXIT, null, List.of(),
                "(exit|stop) [[the] trigger]",
                "(exit|stop) [1|a|the|this] (section:section|loop:loop|conditional:conditional)",
                "(exit|stop) <\\d+> (section:section|loop:loop|conditional:conditional)s");
        library.core(SyntaxKind.EFFECT, CONTINUE, null, List.of(),
                "continue [this loop|[the] [current] loop]",
                "continue [the] <\\d+>(st|nd|rd|th) loop");
        library.core(SyntaxKind.EFFECT, RETURN, null, List.of(), "return %objects%");
        library.core(SyntaxKind.EFFECT, CHANGE, null, List.of(),
                "set %~objects% to %objects%",
                "(add|give) %objects% to %~objects%",
                "increase %~objects% by %objects%",
                "remove (all|every) %objects% from %~objects%",
                "(remove|subtract) %objects% from %~objects%",
                "reduce %~objects% by %objects%",
                "(delete|clear|reset) %~objects%");

        library.core(SyntaxKind.EXPRESSION, LOOP_VALUE, "object", List.of(), "[the] [current] loop-<.+>");
        library.core(SyntaxKind.EXPRESSION, ARITHMETIC, "object", List.of(),
                "%object%[ ]+[ ]%object%",
                "%object%[ ]-[ ]%object%",
                "%object%[ ]*[ ]%object%",
                "%object%[ ]/[ ]%object%",
                "%object%[ ]^[ ]%object%");
    }
}
