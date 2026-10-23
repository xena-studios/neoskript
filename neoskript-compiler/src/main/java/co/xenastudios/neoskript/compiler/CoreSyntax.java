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
    static final String EVENT_VALUE_ENTITY = "ExprEventEntity";
    static final String ARGUMENT = "ExprArgument";
    static final String TYPED_ARGUMENT = "ExprTypedArgument";
    static final String CHOICE_ARGUMENT = "ExprChoiceArgument";
    static final String INDICES = "ExprIndices";
    static final String SORT = "EffSort";
    static final String PERIODICAL = "EvtPeriodical";
    static final String PARSE = "ExprParse";
    static final String WHETHER = "ExprWhether";
    static final String CATCH_ERRORS = "SecCatchErrors";
    static final String FILTER = "ExprFilter";
    static final String CURRENT_SCRIPT = "ExprScript";
    static final String COPY = "EffCopy";
    static final String RECURSIVE = "ExprRecursive";
    static final String TRANSFORM = "ExprTransform";
    static final String REDUCE = "ExprReduce";
    static final String INPUT = "ExprInput";
    static final String REDUCED_VALUE = "ExprReducedValue";
    static final String TRANSFORM_EFFECT = "EffTransform";
    static final String FILTER_SECTION = "SecFilter";
    static final String CAUGHT_ERRORS = "ExprCaughtErrors";

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

        // 'every 5 minutes': runs on the global thread, from when the script loads.
        library.core(SyntaxKind.EVENT, PERIODICAL, null, List.of(), "every %timespan%");

        // 'copy {_a::*} into {_b::*}': lists with their indices and nested lists; 'recursive {_a::*}'.
        library.core(SyntaxKind.EFFECT, COPY, null, List.of(), "copy %~objects% [in]to %~objects%");
        library.core(SyntaxKind.EXPRESSION, RECURSIVE, "object", List.of(), "recursive %~objects%");

        // The script being compiled.
        library.core(SyntaxKind.EXPRESSION, CURRENT_SCRIPT, "script", List.of(), "[the] [current] script");

        // Inline functions over a list's values: 'where [input is "a"]', 'transformed using [input + 1]',
        // 'reduced with [reduced value + input]'. 'input' is the value being looked at.
        library.core(SyntaxKind.EXPRESSION, FILTER, "object", List.of(RegexRole.CONDITION),
                "%objects% (where|that match) \\[<.+>\\]", "%objects% (where|that match) \\(<.+>\\)");
        library.core(SyntaxKind.EXPRESSION, TRANSFORM, "object", List.of(RegexRole.EXPRESSION),
                "%objects% (transformed|mapped) (using|with) \\[<.+>\\]", "%objects% (transformed|mapped) (using|with) \\(<.+>\\)");
        library.core(SyntaxKind.EXPRESSION, REDUCE, "object", List.of(RegexRole.EXPRESSION),
                "%objects% (reduced|folded) (to|with|by) \\[<.+>\\]", "%objects% (reduced|folded) (to|with|by) \\(<.+>\\)");
        library.core(SyntaxKind.EXPRESSION, INPUT, "object", List.of(RegexRole.TYPE),
                "input", "<.+> input", "input index");
        library.core(SyntaxKind.EXPRESSION, REDUCED_VALUE, "object", List.of(),
                "[the] reduced value", "[the] (accumulator|accumulated) [value]", "[the] folded value");
        library.core(SyntaxKind.EFFECT, TRANSFORM_EFFECT, null, List.of(RegexRole.EXPRESSION),
                "(transform|map) %~objects% (using|with) <.+>");
        library.core(SyntaxKind.SECTION, FILTER_SECTION, null, List.of(), "filter %~objects% to match [any:any|all]");

        // Runtime errors reported inside are collected for 'last caught runtime errors', not logged.
        library.core(SyntaxKind.SECTION, CATCH_ERRORS, null, List.of(), "catch [run[ ]time] error[s]");
        library.core(SyntaxKind.EXPRESSION, CAUGHT_ERRORS, "string", List.of(), "[the] last caught [run[ ]time] errors");

        // 'whether {_x} is set': a condition as a boolean.
        library.core(SyntaxKind.EXPRESSION, WHETHER, "boolean", List.of(RegexRole.CONDITION), "whether <.+>");

        // '"5" parsed as integer': text through the type's parser, or nothing if it isn't one.
        library.core(SyntaxKind.EXPRESSION, PARSE, "object", List.of(RegexRole.TYPE), "%text% parsed as [a[n]] <.+>");

        // A list variable's indices, and sorting one: these work on the variable, not its values.
        library.core(SyntaxKind.EXPRESSION, INDICES, "string", List.of(),
                "[(the|all [[of] the])] (indexes|indices) of %~objects%",
                "%~objects%'[s] (indexes|indices)",
                "[sorted] (indices|indexes) of %~objects% in (ascending|descending:descending) order",
                "[sorted] %~objects%'[s] (indices|indexes) in (ascending|descending:descending) order");
        library.core(SyntaxKind.EFFECT, SORT, null, List.of(RegexRole.EXPRESSION),
                "sort %~objects% [in (descending:descending|ascending) order] [(by|based on) <.+>]");

        // Pattern 1 is the next value and 2 the previous one.
        // 'number within {_x}': the values of that type. Its type comes from the type name.
        library.core(SyntaxKind.EXPRESSION, TERNARY, "object", List.of(RegexRole.CONDITION),
                "%objects% if <.+>[,] (otherwise|else) %objects%");
        library.core(SyntaxKind.EXPRESSION, CAST, "object", List.of(RegexRole.TYPE),
                "[the] (<.+>|value[s]) (within|in) %~objects%");
        library.core(SyntaxKind.EXPRESSION, LOOP_VALUE, "object", List.of(RegexRole.LOOP_VALUE),
                "[the] [current] loop-<.+>", "[the] next loop-<.+>", "[the] previous loop-<.+>");
        // A command's arguments. Before arithmetic, so 'arg-1' isn't 'arg - 1'.
        // 'arg-1', 'the second argument', 'the last argument', 'the arguments'.
        String ordinal = "(first:first|second:second|third:third|<(\\d*1)st|(\\d*2)nd|(\\d*3)rd|(\\d*[4-90])th>)";
        library.core(SyntaxKind.EXPRESSION, ARGUMENT, "object", List.of(),
                "[the] last arg[ument]",
                "[the] arg[ument](-| )<(\\d+)>",
                "[the] " + ordinal + " arg[ument][s]",
                "[all [[of] the]|the] arg[ument][s]");
        // 'player-arg', 'the text argument 2': the Nth argument of that type.
        library.core(SyntaxKind.EXPRESSION, TYPED_ARGUMENT, "object", List.of(RegexRole.TYPE, RegexRole.ANY),
                "[the] <.+>( |-)arg[ument][( |-)<\\d+>]",
                "[the] arg[ument]( |-)<.+>[( |-)<\\d+>]");
        // The text typed for a choice, '(give|take)': 'the first choice argument'.
        library.core(SyntaxKind.EXPRESSION, CHOICE_ARGUMENT, "object", List.of(),
                "[the] choice arg[ument] <(\\d+)>",
                "[the] " + ordinal + " choice arg[ument][s]",
                "[the] [last] choice arg[ument]");
        library.core(SyntaxKind.EXPRESSION, ARITHMETIC, "object", List.of(),
                "%object%[ ]+[ ]%object%",
                "%object%[ ]-[ ]%object%",
                "%object%[ ]*[ ]%object%",
                "%object%[ ]/[ ]%object%",
                "%object%[ ]^[ ]%object%");
    }

    /** Syntax tried after everything else, so the library's elements win: event values match any type name. */
    static void registerLast(Library library) {
        // 'player', 'event-world': the event's value of that type. Tried last, as it matches any type name.
        library.core(SyntaxKind.EXPRESSION, EVENT_VALUE, "object", List.of(RegexRole.TYPE), "[the] [event-]<.+>");
        // 'the zombie', 'event-creeper': a kind of entity is the event's entity only when it says so; alone,
        // 'baby chicken' is an entity type.
        library.core(SyntaxKind.EXPRESSION, EVENT_VALUE_ENTITY, "object", List.of(RegexRole.TYPE_OR_ENTITY),
                "the <.+>", "event-<.+>");
    }
}
