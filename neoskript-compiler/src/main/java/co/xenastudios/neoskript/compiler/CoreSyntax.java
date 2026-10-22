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
    static final String DO_IF = "EffDoIf";
    static final String TEST = "TestCase";
    static final String ASSERT = "EffAssert";
    static final String PARSE_LOGS = "ExprLastParseLogs";
    static final String DELAY = "Delay";
    static final String CAST = "ExprCast";
    static final String TERNARY = "ExprTernary";
    static final String HAS_DELAY = "ExprHasDelay";
    static final String DELAY_CHECK = "CondDelayCheck";
    static final String EVENT_VALUE = "ExprEventValue";

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

    /** A delay state as Skript's tests write it. */
    static String kleenean(co.xenastudios.neoskript.frontend.registry.DelayState delay) {
        return switch (delay) {
            case DELAYED -> "true";
            case NOT_DELAYED -> "false";
            case MAYBE_DELAYED -> "unknown";
        };
    }

    /** Skript's test syntax, as its own test suite uses it: only registered in test mode. */
    static void registerTesting(Library library) {
        library.core(SyntaxKind.STRUCTURE, TEST, null, List.of(RegexRole.CONDITION), "test %string% [when <.+>]");
        library.core(SyntaxKind.EFFECT, ASSERT, null, List.of(RegexRole.CONDITION),
                "assert [unsafely] <.+> [fail:to fail] [with [error] %string%[, expected %objects%][, got %objects%]]");
        library.core(SyntaxKind.EXPRESSION, PARSE_LOGS, "string", List.of(), "[the] last parse logs");

        // Whether the trigger may have waited at this point: true, false or unknown.
        library.literalType("kleenean", "kleenean", java.util.Set.of("unknown"), word -> word);
        library.core(SyntaxKind.EXPRESSION, HAS_DELAY, "kleenean", List.of(), "[the] has delay before");
        library.core(SyntaxKind.CONDITION, DELAY_CHECK, null, List.of(),
                "has delay before is (true:true|false:false|unknown:unknown) failing if wrong");
        library.check(SyntaxKind.CONDITION, DELAY_CHECK, (match, delay) -> {
            String expected = match.hasTag("true") ? "true" : match.hasTag("false") ? "false" : "unknown";
            String actual = kleenean(delay);
            return expected.equals(actual) ? null : "has delay before is " + actual + ", not " + expected;
        });
    }

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
        library.core(SyntaxKind.EFFECT, DELAY, null, List.of(), "(wait|halt) [for] %timespan%");
        library.delays(SyntaxKind.EFFECT, DELAY);
        library.core(SyntaxKind.EFFECT, DO_IF, null, List.of(RegexRole.EFFECT, RegexRole.CONDITION), "<.+> if <.+>");
        library.core(SyntaxKind.EFFECT, CHANGE, null, List.of(),
                "set %~objects% to %objects%",
                "(add|give) %objects% to %~objects%",
                "increase %~objects% by %objects%",
                "remove (all|every) %objects% from %~objects%",
                "(remove|subtract) %objects% from %~objects%",
                "reduce %~objects% by %objects%",
                "(delete|clear|reset:reset) %~objects%");

        // Pattern 1 is the next value and 2 the previous one.
        // 'number within {_x}': the values of that type. Its type comes from the type name.
        library.core(SyntaxKind.EXPRESSION, TERNARY, "object", List.of(RegexRole.CONDITION),
                "%objects% if <.+>[,] (otherwise|else) %objects%");
        library.core(SyntaxKind.EXPRESSION, CAST, "object", List.of(RegexRole.TYPE),
                "[the] (<.+>|value[s]) (within|in) %~objects%");
        library.core(SyntaxKind.EXPRESSION, LOOP_VALUE, "object", List.of(),
                "[the] [current] loop-<.+>", "[the] next loop-<.+>", "[the] previous loop-<.+>");
        library.core(SyntaxKind.EXPRESSION, ARITHMETIC, "object", List.of(),
                "%object%[ ]+[ ]%object%",
                "%object%[ ]-[ ]%object%",
                "%object%[ ]*[ ]%object%",
                "%object%[ ]/[ ]%object%",
                "%object%[ ]^[ ]%object%");
        // 'player', 'event-world': the event's value of that type. Tried last, as it matches any type name.
        library.core(SyntaxKind.EXPRESSION, EVENT_VALUE, "object", List.of(RegexRole.TYPE), "[the] [event-]<.+>");
    }
}
