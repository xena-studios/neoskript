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
    static final String FOR = "SecFor";
    static final String WHILE = "SecWhile";
    static final String EXIT = "EffExit";
    static final String CONTINUE = "EffContinue";
    static final String RETURN = "EffReturn";
    static final String CHANGE = "EffChange";
    static final String LOOP_VALUE = "ExprLoopValue";
    static final String ARITHMETIC = "ExprArithmetic";
    static final String DO_IF = "EffDoIf";
    static final String TEST = "TestCase";
    static final String EXAMPLE = "StructExample";
    static final String ASSERT = "EffAssert";
    /** Skript's test section whose code can {@code return} values, for {@code the last return values}. */
    static final String RETURNABLE = "SecReturnable";
    /** Skript's test section that changes what a left-out value of a type is, for the code in it. */
    static final String CUSTOM_DEFAULT = "SecCustomDefault";
    /** {@code assert X to fail} where X isn't a condition at all: as in Skript, that has failed as expected. */
    static final String ASSERT_UNPARSED = "EffAssertUnparsed";
    static final String PARSE_LOGS = "ExprLastParseLogs";
    static final String DELAY = "Delay";
    static final String CAST = "ExprCast";
    static final String TERNARY = "ExprTernary";
    static final String HAS_DELAY = "ExprHasDelay";
    static final String DELAY_CHECK = "CondDelayCheck";
    static final String EVENT_VALUE = "ExprEventValue";
    static final String TIME_STATE = "ExprTimeState";
    static final String EVENT_VALUE_ENTITY = "ExprEventEntity";
    static final String EVENT_ENTITY_BARE = "ExprEventEntityBare";
    static final String ARGUMENT = "ExprArgument";
    static final String TYPED_ARGUMENT = "ExprTypedArgument";
    static final String CHOICE_ARGUMENT = "ExprChoiceArgument";
    /** A command's {@code suggestions:} entry: the suggestions for an argument, their filtering, what's typed. */
    static final String COMMAND_SUGGESTIONS = "ExprCommandSuggestions";
    static final String SUGGESTION_FILTERING = "EffSuggestionFilteringMode";
    static final String COMMAND_INPUT = "ExprCommandInput";
    static final String INDICES = "ExprIndices";
    static final String SORT = "EffSort";
    static final String PERIODICAL = "EvtPeriodical";
    static final String PARSE = "ExprParse";
    /** {@code X parsed as "%integer% and %text%"}: text parsed with a pattern of types, as Skript's ExprParse. */
    static final String PARSE_PATTERN = "ExprParse-pattern";
    static final String WHETHER = "ExprWhether";
    static final String CATCH_ERRORS = "SecCatchErrors";
    /** {@code suppress unreachable code warnings}, alone or as a section: handled while parsing. */
    static final String SUPPRESS_WARNINGS = "EffSecSuppressWarnings";
    static final String SUPPRESS_HINTS = "EffSecSuppressTypeHints";
    static final String FILTER = "ExprFilter";
    static final String CURRENT_SCRIPT = "ExprScript";
    static final String COPY = "EffCopy";
    static final String RECURSIVE = "ExprRecursive";
    static final String RANDOM = "ExprRandom";
    static final String SETS = "ExprSets";
    static final String TYPE_VALUE = "LitClassInfo";
    /** Skript's {@code ExprAmount} counting a list variable's values at every depth. */
    static final String RECURSIVE_SIZE = "ExprAmount-recursive";
    static final String ANY_OF = "ExprAnyOf";
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

    /**
     * An arithmetic operator: its sign, how tightly it binds (higher first, as Skript's priorities), and
     * whether a chain of it groups from the right ({@code 2^3^2} is {@code 2^(3^2)}).
     */
    record Operator(String sign, int priority, boolean fromRight) {}

    /** {@link #ARITHMETIC} operators, by pattern index. */
    static final List<Operator> OPERATORS = List.of(new Operator("+", 10, false), new Operator("-", 10, false),
            new Operator("*", 20, false), new Operator("/", 20, false), new Operator("^", 30, true));

    /** Skript's test-only custom operators, by pattern index of {@link #CUSTOM_ARITHMETIC}. */
    static final List<Operator> CUSTOM_OPERATORS = List.of(new Operator("bar!", 10, false),
            new Operator("foo!1", 5, false), new Operator("foo!2", 25, false), new Operator("blob!", 10, false));

    static final String CUSTOM_ARITHMETIC = "ExprArithmetic-custom";

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
        // With 'to fail' first: where both read the condition with unresolved words, the one leaning on fewer
        // wins, so 'X is set to fail' is X failing to be set, not X being 'set to fail'.
        library.core(SyntaxKind.EFFECT, ASSERT, null, List.of(RegexRole.CONDITION),
                "assert [unsafely] <.+> (fail:to fail) [with [error] %string%[, expected %objects%][, got %objects%]]",
                "assert [unsafely] <.+> [with [error] %string%[, expected %objects%][, got %objects%]]");
        library.core(SyntaxKind.EFFECT, ASSERT_UNPARSED, null, List.of(RegexRole.NOT_CONDITION),
                "assert [unsafely] <.+> to fail [with [error] %string%[, expected %objects%][, got %objects%]]");
        library.core(SyntaxKind.EXPRESSION, PARSE_LOGS, "string", List.of(), "[the] [last] parse logs");
        library.core(SyntaxKind.SECTION, RETURNABLE, null, List.of(), "returnable [:plural] %*classinfo% section");
        // An effect section that refuses a section, with the error it's given or none (EffSecErrorWhenSection), and a
        // section expression that does (ExprSecErroringSectionExpression).
        library.section("EffSecErrorWhenSection", "object", info -> (context, args) -> new Object[0],
                "error [%-*string%] when using [a] section [with %-object%]");
        library.sectionCheck(SyntaxKind.SECTION, "EffSecErrorWhenSection",
                (match, delay) -> match.values().getFirst() instanceof co.xenastudios.neoskript.frontend.parse.Expr
                        ? co.xenastudios.neoskript.frontend.registry.ParseCheck.ARGUMENT_TEXT + 0 : "");
        library.expression("ExprSecErroringSectionExpression", "object", info -> (context, args) -> new Object[0],
                "erroring section expression");
        library.sectionCheck(SyntaxKind.EXPRESSION, "ExprSecErroringSectionExpression",
                (match, delay) -> "erroring section expression");
        // Skript's custom test operators (CustomTestOperators), with the priorities it gives them.
        library.core(SyntaxKind.EXPRESSION, CUSTOM_ARITHMETIC, "object", List.of(),
                CUSTOM_OPERATORS.stream().map(op -> "%object%[ ]" + op.sign() + "[ ]%object%").toArray(String[]::new));
        co.xenastudios.neoskript.runtime.Arithmetic.registerCustom("bar!", String.class, String.class,
                (a, b) -> a + " bar " + b);
        co.xenastudios.neoskript.runtime.Arithmetic.registerCustom("foo!1", Number.class, Number.class,
                (a, b) -> (long) (a.intValue() + 2 * b.intValue()));
        co.xenastudios.neoskript.runtime.Arithmetic.registerCustom("foo!2", Number.class, Number.class,
                (a, b) -> (long) (a.intValue() + 2 * b.intValue()));
        co.xenastudios.neoskript.runtime.Arithmetic.registerCustom("blob!", String.class, Number.class,
                (a, b) -> a + (b.intValue() + 1));
        library.core(SyntaxKind.SECTION, CUSTOM_DEFAULT, null, List.of(),
                "run with custom default value %*object% for %*classinfo%");

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
        // 'for {_value} in X', 'for {_key}, {_value} in X': a loop that puts each value (and index) in variables.
        library.core(SyntaxKind.SECTION, FOR, null, List.of(), "for [each] [value] %~object% in %objects%",
                "for [each] [(key|index)] %~object%(,| and) [value] %~object% in %objects%",
                "loop %~object%(,| and) %~object% in %objects%");
        library.core(SyntaxKind.SECTION, LOOP, null, List.of(), "loop %objects%");
        // Skript's 'example:' structure: code that's parsed, for its errors, but never run.
        library.core(SyntaxKind.STRUCTURE, EXAMPLE, null, List.of(), "example");
        library.core(SyntaxKind.SECTION, WHILE, null, condition, "[do:do] while <.+>");

        // A count below 1 isn't understood, as Skript's init refuses it.
        library.core(SyntaxKind.EFFECT, EXIT, null, List.of(),
                "(exit|stop) [[the] trigger]",
                "(exit|stop) [1|a|the|this] (section:section|loop:loop|conditional:conditional)",
                "(exit|stop) <0*[1-9]\\d*(_\\d+)*> (section:section|loop:loop|conditional:conditional)s",
                "(exit|stop) all (section:section|loop:loop|conditional:conditional)s");
        library.core(SyntaxKind.EFFECT, CONTINUE, null, List.of(),
                "continue [this loop|[the] [current] loop]",
                "continue [the] <\\d+>(st|nd|rd|th) loop");
        library.core(SyntaxKind.EFFECT, RETURN, null, List.of(), "return %objects%");
        // Code after these never runs, so a delay before them doesn't carry past their section.
        library.stops(SyntaxKind.EFFECT, EXIT, 0);
        library.stops(SyntaxKind.EFFECT, RETURN, -1);
        // Code after these in the same section never runs.
        library.ends(SyntaxKind.EFFECT, EXIT);
        library.ends(SyntaxKind.EFFECT, RETURN);
        library.ends(SyntaxKind.EFFECT, CONTINUE);
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
        // In worlds, it runs for each, when the world's time is a multiple of the period.
        library.core(SyntaxKind.EVENT, PERIODICAL, null, List.of(), "every %timespan%",
                "every %timespan% in [world[s]] %worlds%");

        // 'copy {_a::*} into {_b::*}': lists with their indices and nested lists; 'recursive {_a::*}'.
        library.core(SyntaxKind.EFFECT, COPY, null, List.of(), "copy %~objects% [in]to %~objects%");
        library.core(SyntaxKind.EXPRESSION, RECURSIVE_SIZE, "integer", List.of(),
                "[the] recursive (amount|number|size) of %~objects%");
        library.core(SyntaxKind.EXPRESSION, RECURSIVE, "object", List.of(), "recursive %~objects%");
        // 'any of 1, 2 and 3' is the values as an or-list: '1 is any of {_a::*}'.
        library.core(SyntaxKind.EXPRESSION, ANY_OF, "object", List.of(), "(any [one]|one) of [the] %objects%");

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
        String[] suppress = {"[local[ly]] [stop:un]suppress [the] (conflict:conflict|save:variable save|conjunction:[missing] conjunction|starting:starting [with] expression[s]|colon:variable[ name][s] contain[s|ing] colon[s]|deprecated:deprecated syntax|unreachable:unreachable code|constant:constant condition[s]) warning[s]",
                "[start|stop:stop] [local[ly]] suppressing [the] (conflict:conflict|save:variable save|conjunction:[missing] conjunction|starting:starting [with] expression[s]|colon:variable[ name][s] contain[s|ing] colon[s]|deprecated:deprecated syntax|unreachable:unreachable code|constant:constant condition[s]) warning[s]"};
        library.core(SyntaxKind.EFFECT, SUPPRESS_WARNINGS, null, List.of(), suppress);
        // 'suppress local variable type hints', alone or as a section: handled while parsing.
        String[] suppressHints = {"[stop:un]suppress [local variable] type hints",
                "(start|stop:stop) suppressing [local variable] type hints"};
        library.core(SyntaxKind.EFFECT, SUPPRESS_HINTS, null, List.of(), suppressHints);
        library.core(SyntaxKind.SECTION, SUPPRESS_HINTS, null, List.of(), suppressHints);
        library.core(SyntaxKind.SECTION, SUPPRESS_WARNINGS, null, List.of(), suppress);
        library.suppressesWarnings(SyntaxKind.EFFECT, SUPPRESS_WARNINGS);
        library.suppressesWarnings(SyntaxKind.SECTION, SUPPRESS_WARNINGS);
        library.core(SyntaxKind.EXPRESSION, CAUGHT_ERRORS, "string", List.of(), "[the] last caught [run[ ]time] errors");

        // 'whether {_x} is set': a condition as a boolean.
        library.core(SyntaxKind.EXPRESSION, WHETHER, "boolean", List.of(RegexRole.CONDITION), "whether <.+>");

        // A list variable's indices, and sorting one: these work on the variable, not its values.
        library.core(SyntaxKind.EXPRESSION, INDICES, "string", List.of(),
                "[(the|all [[of] the])] (indexes|indices) of %~objects%",
                "%~objects%'[s] (indexes|indices)",
                "[sorted] (indices|indexes) of %~objects% in (ascending|descending:descending) order",
                "[sorted] %~objects%'[s] (indices|indexes) in (ascending|descending:descending) order");
        library.core(SyntaxKind.EFFECT, SORT, null, List.of(RegexRole.EXPRESSION),
                "sort %~objects% [in (descending:descending|ascending) order] [(by|based on) <.+>]");

        // Pattern 1 is the next value and 2 the previous one.
        library.core(SyntaxKind.EXPRESSION, TERNARY, "object", List.of(RegexRole.CONDITION),
                "%objects% if <.+>[,] (otherwise|else) %objects%");
        // 'a random number out of {_list::*}': one of the values of that type, or nothing.
        library.core(SyntaxKind.EXPRESSION, RANDOM, "object", List.of(RegexRole.TYPE),
                "[a] random <.+> [out] (of|from) %objects%");
        library.core(SyntaxKind.EXPRESSION, LOOP_VALUE, "object", List.of(RegexRole.LOOP_VALUE),
                "[the] [current] loop-<.+>", "[the] next loop-<.+>", "[the] previous loop-<.+>",
                // Skript's ExprLoopIteration also takes a space: 'the loop counter'.
                "[the] loop <(?:counter|iteration)(?:-\\d+)?>");
        // A command's arguments. Before arithmetic, so 'arg-1' isn't 'arg - 1'.
        // 'arg-1', 'the second argument', 'the last argument', 'the arguments'.
        String ordinal = "(first:first|second:second|third:third|<(\\d*1)st|(\\d*2)nd|(\\d*3)rd|(\\d*[4-90])th>)";
        library.core(SyntaxKind.EXPRESSION, ARGUMENT, "object", List.of(),
                "[the] last arg[ument]",
                "[the] arg[ument](-| )<(\\d+)>",
                "[the] " + ordinal + " arg[ument][s]",
                "[all [[of] the]|the] arg[ument][all:s]");
        // 'player-arg', 'the text argument 2': the Nth argument of that type.
        library.core(SyntaxKind.EXPRESSION, TYPED_ARGUMENT, "object", List.of(RegexRole.TYPE, RegexRole.ANY),
                "[the] <.+>( |-)arg[ument][( |-)<\\d+>]",
                "[the] arg[ument]( |-)<.+>[( |-)<\\d+>]");
        // The text typed for a choice, '(give|take)': 'the first choice argument'.
        library.core(SyntaxKind.EXPRESSION, CHOICE_ARGUMENT, "object", List.of(),
                "[the] choice arg[ument] <(\\d+)>",
                "[the] " + ordinal + " choice arg[ument][s]",
                "[the] [last] choice arg[ument]");
        library.core(SyntaxKind.EXPRESSION, COMMAND_SUGGESTIONS, "object", List.of(),
                "[the] [command] (suggestions|tab completions) (of|for) %objects%",
                "%objects%'[s] [command] (suggestions|tab completions)");
        library.core(SyntaxKind.EFFECT, SUGGESTION_FILTERING, null, List.of(),
                "(use no|disable) filtering (for|with) %objects%",
                "use starts with filtering (for|with) %objects%",
                "use contains filtering (for|with) %objects%");
        library.core(SyntaxKind.EXPRESSION, COMMAND_INPUT, "object", List.of(),
                "[the] [command] [suggestion|tab completion] (full|complete) input",
                "[the] [command] [suggestion|tab completion] (current|arg[ument]) input",
                "[the] [command] [suggestion|tab completion] (current|arg[ument]) input [start[ing]] (position|index)");
        library.core(SyntaxKind.EXPRESSION, ARITHMETIC, "object", List.of(),
                "%object%[ ]+[ ]%object%",
                "%object%[ ]-[ ]%object%",
                "%object%[ ]*[ ]%object%",
                "%object%[ ]/[ ]%object%",
                "%object%[ ]^[ ]%object%");
    }

    /** Syntax tried after everything else, so the library's elements win: event values match any type name. */
    static void registerLast(Library library) {
        // 'all colours': every value of a type that has a fixed set of them.
        library.core(SyntaxKind.EXPRESSION, SETS, "object", List.of(RegexRole.TYPE), "(all [[of] the]|every) <.+>");
        // 'X is a color': a type as a value, which a value is if it's of that type.
        library.core(SyntaxKind.EXPRESSION, TYPE_VALUE, "object", List.of(RegexRole.TYPE), "(a|an) <.+>");
        // 'number within {_x}': the values of that type. Its type comes from the type name. Late, as in Skript,
        // so 'items in {_inventory}' is the inventory's items.
        library.core(SyntaxKind.EXPRESSION, CAST, "object", List.of(RegexRole.TYPE),
                "[the] (<.+>|value[:s]) (within|in) %~objects%");
        // '"5" parsed as integer': text through the type's parser, or nothing if it isn't one. After the
        // library's expressions, as Skript tries it after most: 'difference between {_a} and "5" parsed as a
        // number' is the difference with a parsed number.
        library.core(SyntaxKind.EXPRESSION, PARSE, "object", List.of(RegexRole.TYPE), "%text% parsed as [a[n]] <.+>");
        library.core(SyntaxKind.EXPRESSION, PARSE_PATTERN, "object", List.of(), "%text% parsed as \"<.*>\"");
        // 'past event-block', 'event-item after the event': an event value before or after the event.
        library.core(SyntaxKind.EXPRESSION, TIME_STATE, "object", List.of(),
                "[the] (former|past|old) [state] [of] %objects%", "%objects% before [the event]",
                "[the] (future|to-be|new) [state] [of] %objects%", "%objects%(-to-be| after[(wards| the event)])");
        // 'player', 'event-world': the event's value of that type. Tried last, as it matches any type name.
        library.core(SyntaxKind.EXPRESSION, EVENT_VALUE, "object", List.of(RegexRole.TYPE), "[the] [event-]<.+>");
        // 'the zombie', 'event-creeper': a kind of entity is the event's entity only when it says so; alone,
        // 'baby chicken' is an entity type.
        library.core(SyntaxKind.EXPRESSION, EVENT_VALUE_ENTITY, "object", List.of(RegexRole.TYPE_OR_ENTITY),
                "the <.+>", "event-<.+>");
        // 'yaw of pig' in 'spawn a pig:': a kind of entity alone is the event's entity too, where an entity
        // type doesn't fit and the words don't name an item. Tried after everything else.
        library.core(SyntaxKind.EXPRESSION, EVENT_ENTITY_BARE, "object", List.of(RegexRole.BARE_ENTITY), "<.+>");
    }
}
