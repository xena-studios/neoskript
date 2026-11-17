/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Text;
import co.xenastudios.neoskript.frontend.registry.DelayState;
import co.xenastudios.neoskript.frontend.registry.EventValues;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.ParseCheck;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the lines of a trigger, function or other code section into statements.
 *
 * <p>It also tracks whether the trigger may have waited by each line, as Skript does: each branch of an
 * {@code if} chain starts from the state before the chain, and the state after it is merged from every
 * branch (see {@link DelayState#merge}). Code that may run any number of times (a loop) can only make the
 * state "maybe". Code Skript loads as a trigger of its own (a delayed section's, a spawn section's) starts with no
 * delay, as its parser is reset for it.
 */
final class StatementParser {

    /** Skript's test-only section whose problems are captured for the test to check. */
    static final String PARSE_SECTION = "parse";

    /** {@code if any:}, {@code else if all:} and the like, whose lines are conditions. */
    private static final Pattern MULTILINE_CONDITIONAL =
            Pattern.compile("(else )?(parse )?if( (any|at least one( of)?|all))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARSE_IF = Pattern.compile("(else )?parse if .*", Pattern.CASE_INSENSITIVE);

    private final ParserContext context;
    private final Diagnostics diagnostics;
    /** The delay state before each statement's line, for the compiler. */
    private final Map<Line, DelayState> delays = new IdentityHashMap<>();
    private DelayState delay = DelayState.NOT_DELAYED;
    /**
     * Whether the code is loaded as a trigger of its own, apart from the function it's in, where {@code return}
     * can't be used: a delayed section's or a spawn section's.
     */
    private boolean detached;
    /** The id of the section Skript loads as a trigger of its own that the code is in ({@code EffSecSpawn}), or null. */
    private String linkedId;
    /** The warnings the script suppresses so far, by the tag naming them ({@code unreachable}). */
    private final java.util.Set<String> suppressed = new java.util.HashSet<>();
    /** What was suppressed at each statement's and structure's line, for the compiler's warnings (by identity). */
    private final Map<Line, java.util.Set<String>> suppressions = new IdentityHashMap<>();

    /** The {@code if} chain being parsed in the current block. */
    private static final class Chain {
        final DelayState before;
        final List<DelayState> ends = new ArrayList<>();
        /** How each branch's code stops execution, as {@link #intent}s ({@code null} for not at all). */
        final List<Integer> intents = new ArrayList<>();
        boolean hasElse;

        Chain(DelayState before) {
            this.before = before;
        }
    }

    /**
     * Skript's execution intents, which say how far a statement stops execution: {@link #STOP_TRIGGER} for the whole
     * trigger ({@code stop}, a function's {@code return}), or a number of sections ({@code exit 2 loops}). A statement
     * that doesn't stop execution has none. By identity.
     */
    private final Map<Statement, Integer> intents = new IdentityHashMap<>();
    private static final int STOP_TRIGGER = 0;

    /**
     * The sections the code being parsed is in, outermost first, as Skript's parser's current sections: {@code loop}
     * for its loops, {@code conditional}, {@code returnable} for its returnable section, {@code section} for others.
     * Code Skript loads as a trigger of its own starts with none.
     */
    private java.util.List<String> sections = new ArrayList<>();

    StatementParser(ParserContext context, Diagnostics diagnostics) {
        this.context = context;
        this.diagnostics = diagnostics;
    }

    /**
     * For section headers, the error reading the whole line as a statement gives, where it's one Skript keeps: see
     * {@link ParsedScript#statementError}.
     */
    private final Map<Line, String> statementErrors = new IdentityHashMap<>();

    Map<Line, String> statementErrors() {
        return statementErrors;
    }

    /** For each line warned to be unreachable, by number, the line of the statement that stops the code before it. */
    private final Map<Integer, Integer> unreachableAfter = new java.util.HashMap<>();

    Map<Integer, Integer> unreachableAfter() {
        return unreachableAfter;
    }

    /** Sections Skript loads as triggers of their own that failed as their code may wait: see {@link ParsedScript}. */
    private final Map<Line, ParsedScript.WaitingSection> waitingSections = new IdentityHashMap<>();
    /** Section headers nothing takes the section of, read as statements: see {@link ParsedScript#sectionStatement}. */
    private final Map<Line, Statement> sectionStatements = new IdentityHashMap<>();

    Map<Line, ParsedScript.WaitingSection> waitingSections() {
        return waitingSections;
    }

    Map<Line, Statement> sectionStatements() {
        return sectionStatements;
    }

    /**
     * Skript reads a section header that fails as a statement too, and its error is the line's if it has one that
     * isn't that the statement isn't understood ({@code ScriptLoader.loadItems}): 'if the potion effect has
     * particles:', read as words compared, says 'particles' isn't an item type. The compiler may yet fail the header.
     */
    private void statementError(Line line, String key) {
        String error = statementProblem(line, key);
        if (error != null) {
            statementErrors.put(line, error);
        }
    }

    /**
     * The error reading a section header as a statement gives, if it's one Skript keeps over the section's: not
     * that the statement isn't understood. Nothing is reported.
     */
    private String statementProblem(Line line, String key) {
        if (ELSE_IF_HEADER.matcher(key).matches()) {
            // Read as a statement, an 'else if' says it doesn't understand the 'else' (or 'else parse').
            return Statement.orphanElse(Text.lower(key.strip()));
        }
        int mark = diagnostics.mark();
        DelayState before = delay;
        try {
            Statement statement = simple(new Line(line.number(), line.indent(), key));
            List<Diagnostic> found = diagnostics.takeSince(mark);
            if (statement instanceof Statement.Invalid) {
                for (Diagnostic diagnostic : found) {
                    if (diagnostic.severity() == Severity.ERROR
                            && !diagnostic.message().startsWith("Can't understand this condition/effect:")) {
                        return diagnostic.message();
                    }
                }
            }
            return null;
        } finally {
            delay = before;
        }
    }

    /** The delay state before each line parsed so far, by line. */
    Map<Line, DelayState> delays() {
        return delays;
    }

    /** The warnings suppressed at each line noted so far, by line. */
    Map<Line, java.util.Set<String>> suppressions() {
        return suppressions;
    }

    /** Notes what's suppressed at {@code line}: a statement's, or a structure's header. */
    void note(Line line) {
        suppressions.put(line, java.util.Set.copyOf(suppressed));
    }

    /** Parses a trigger's or function's code, which starts with no delay. */
    List<Statement> body(List<Node> nodes) {
        return body(nodes, Map.of());
    }

    /** {@link #body(List)}, with what types local variables start with: a function's parameters'. */
    List<Statement> body(List<Node> nodes, Map<String, java.util.Set<String>> hints) {
        delay = DelayState.NOT_DELAYED;
        detached = false;
        sections = new ArrayList<>();
        context.hints().reset(hints);
        return statements(nodes);
    }

    private List<Statement> statements(List<Node> nodes) {
        List<Statement> statements = new ArrayList<>(nodes.size());
        Statement previous = null;
        Chain chain = null;
        boolean ended = false;
        Line endedBy = null;
        for (Node node : nodes) {
            String key = Text.lower(node.line().key().strip());
            boolean section = node instanceof Node.Section;
            boolean continuesChain = section && (key.startsWith("else") || key.startsWith("then"));
            Chain chainBefore = chain;
            DelayState delayBefore = delay;
            if (chain != null && !continuesChain) {
                delay = endChain(chain);
                chain = null;
            }
            if (section && (key.startsWith("if") || key.startsWith("parse if"))) {
                chain = new Chain(delay);
            }
            Statement statement = section && chain != null && isConditional(key)
                    ? branch((Node.Section) node, previous, chain)
                    : line(node, previous);
            statements.add(statement);
            if (statement instanceof Statement.Invalid) {
                // As in Skript, a line that fails isn't there: an 'if' before it goes on, so an 'else' after it
                // belongs to that 'if' (Skript's ScriptLoader.loadItems leaves it out of the items an 'else' looks
                // back at).
                chain = chainBefore;
                delay = delayBefore;
                continue;
            }
            previous = statement;
            // A return that can't be used where it is fails too, though only the compiler says so: it doesn't stop
            // the code.
            if (statement instanceof Statement.Effect effect && failingReturn(effect)) {
                continue;
            }
            if (ended && !suppressed.contains("unreachable")) {
                // After the line's own problems, and only for a line that loads, as in Skript. Only if the line that
                // stops the code loads too, which the compiler may yet find it doesn't.
                diagnostics.warning(node.line().number(), node.line().column(), node.line().text().length(),
                        "Unreachable code. The previous statement stops further execution.");
                unreachableAfter.put(node.line().number(), endedBy.number());
            }
            // As Skript's execution intents: a statement, or a section all of whose code does, that stops it.
            Integer intent = intent(statement, chain);
            if (intent != null) {
                intents.put(statement, intent);
            }
            ended = intent != null;
            endedBy = node.line();
            if (statement instanceof Statement.Effect effect) {
                stopsHints(effect.effect(), false);
            }
        }
        if (chain != null) {
            delay = endChain(chain);
        }
        return statements;
    }

    private Statement line(Node node, Statement previous) {
        delays.put(node.line(), delay);
        note(node.line());
        EventValues.Scope scope = context.eventScope();
        if (scope == null || !(node instanceof Node.Section)) {
            // (A line on its own tries that itself.)
            return parse(node, previous);
        }
        int mark = diagnostics.mark();
        DelayState before = delay;
        Statement statement = parse(node, previous);
        if (!(statement instanceof Statement.Invalid)) {
            return statement;
        }
        // Nothing parses without event values the code can't have: parsed with them after all, the compiler
        // says which one there's none of, as Skript does when nothing else parses.
        diagnostics.takeSince(mark);
        delay = before;
        context.eventScope(null);
        try {
            return parse(node, previous);
        } finally {
            context.eventScope(scope);
        }
    }

    private Statement parse(Node node, Statement previous) {
        long outer = context.startLine();
        try {
            return switch (node) {
                case Node.Simple simple -> simple(simple.line());
                case Node.Section section -> section(section, previous);
            };
        } catch (StackOverflowError tooDeep) {
            // Brackets or calls nested hundreds deep run the parser out of stack: an error in the script, not a
            // crash of the reload.
            error(node.line(), TOO_DEEP);
            return new Statement.Invalid(node.line());
        } catch (LineParser.TooComplex tooComplex) {
            error(node.line(), LineParser.TOO_COMPLEX);
            return new Statement.Invalid(node.line());
        } finally {
            context.endLine(outer);
        }
    }

    /** The error for a line nested too deeply to parse. */
    static final String TOO_DEEP = "This line is nested too deeply to be understood";

    /**
     * A section's code, parsed by {@code body}. Unless {@code header} only keeps the event values of the code
     * around it (as {@code if} does), any may be used in it, as the section may give its own.
     */
    private List<Statement> sectionCode(ParsedSyntax header, java.util.function.Supplier<List<Statement>> body) {
        EventValues values = context.registry().eventValues();
        EventValues.Scope later = header == null || values == null ? null : values.later(header.syntax());
        if (later != null) {
            // Code that runs later, for its own event: that event's values, and no delay yet.
            EventValues.Scope scope = context.eventScope(later);
            DelayState before = delay;
            delay = DelayState.NOT_DELAYED;
            try {
                return body.get();
            } finally {
                delay = before;
                context.eventScope(scope);
            }
        }
        if (context.eventScope() == null || header != null && values != null && values.keepsEventValues(header.syntax())) {
            return body.get();
        }
        // A section whose code is in an event of its own (a spawn section's) can use that event's values only.
        EventValues.Scope own = header != null && values != null ? values.section(header.syntax()) : null;
        EventValues.Scope scope = context.eventScope(own);
        try {
            return body.get();
        } finally {
            context.eventScope(scope);
        }
    }

    private static boolean isConditional(String key) {
        return key.startsWith("if") || key.startsWith("else") || key.startsWith("then") || key.startsWith("parse if");
    }

    private static DelayState endChain(Chain chain) {
        if (chain.before == DelayState.DELAYED) {
            // Skript's SecConditional only works the state out when the code hadn't surely waited before the chain:
            // else it's what the last branch left, which a teleport there makes 'maybe'.
            return chain.ends.isEmpty() ? chain.before : chain.ends.getLast();
        }
        List<DelayState> ends = new ArrayList<>(chain.ends);
        if (!chain.hasElse) {
            ends.add(chain.before);
        }
        return chain.before.merge(ends);
    }

    /** One section of an {@code if} chain: an {@code if}, {@code else if}, {@code else} or {@code then}. */
    private Statement branch(Node.Section section, Statement previous, Chain chain) {
        String key = Text.lower(section.line().key().strip());
        if (!key.startsWith("then")) {
            // Every branch is parsed from the state before the chain, including its condition.
            delay = chain.before;
        }
        Statement statement = line(section, previous);
        if (key.equals("else") && !(statement instanceof Statement.Invalid)) {
            chain.hasElse = true;
        }
        // A multi-line condition's code is in the 'then' that follows, so its branch ends there. A branch that
        // fails isn't one.
        if (!(statement instanceof Statement.Conditions) && !(statement instanceof Statement.Invalid)) {
            chain.ends.add(delay);
            chain.intents.add(statement instanceof Statement.Section branch ? use(bodyIntent(branch.body())) : null);
        }
        return statement;
    }

    /** How far {@code statement} stops execution, as its {@code executionIntent} in Skript, or {@code null}. */
    private Integer intent(Statement statement, Chain chain) {
        if (statement instanceof Statement.Effect effect) {
            return effectIntent(effect.effect());
        }
        if (!(statement instanceof Statement.Section section) || section.header() == null) {
            return null;
        }
        ParsedSyntax header = section.header();
        switch (header.syntax().id()) {
            case "SecConditional" -> {
                // Only a chain with an 'else' can stop execution: at least as little as its branch that stops least.
                if (chain == null || !Text.lower(section.line().key().strip()).equals("else")) {
                    return null;
                }
                Integer least = null;
                for (Integer branch : chain.intents) {
                    if (branch == null) {
                        return null;
                    }
                    if (least == null || rank(branch) < rank(least)) {
                        least = branch;
                    }
                }
                return least;
            }
            // A loop stops execution as its code does only if it surely runs: over literal values. Not a for loop:
            // Skript's SecFor sets itself up without SecLoop's check, so it never says it surely runs.
            case "SecLoop" -> {
                // A single value can't be looped: the loop fails, and stops nothing.
                Object looped = header.match().values().getFirst();
                return looped instanceof Expr expr && !Plurality.single(expr, context.registry()) && guaranteed(looped)
                        ? use(bodyIntent(section.body())) : null;
            }
            case "SecWhile" -> {
                return header.match().tags().contains("do") ? use(bodyIntent(section.body())) : null;
            }
            default -> {
                return null;
            }
        }
    }

    /** As Skript's EffExit, EffContinue and EffReturn: how far they stop, or {@code null} if they can't be used. */
    private Integer effectIntent(ParsedSyntax effect) {
        List<Object> regex = effect.match().regexValues();
        switch (effect.syntax().id()) {
            case "EffExit" -> {
                if (effect.pattern() == 0) {
                    return STOP_TRIGGER;
                }
                String type = effect.match().tags().contains("loop") ? "loop"
                        : effect.match().tags().contains("conditional") ? "conditional" : "section";
                List<Integer> typed = sectionsOf(type);
                if (effect.pattern() == 3) {
                    // Up to the outermost section of that type.
                    return typed.isEmpty() ? null : sections.size() - typed.getFirst();
                }
                int levels = effect.pattern() == 1 ? 1 : regex.getFirst() instanceof java.util.regex.MatchResult n
                        ? Integer.parseInt(n.group().replace("_", "")) : 1;
                // Skript's quirk: the levels given, not the sections between.
                return levels < 1 || levels > typed.size() ? null : levels;
            }
            case "EffContinue" -> {
                List<Integer> loops = sectionsOf("loop");
                int level = effect.pattern() == 0 ? loops.size() : regex.getFirst() instanceof java.util.regex.MatchResult n
                        ? Integer.parseInt(n.group()) : 0;
                int levels = loops.size() - level + 1;
                if (loops.isEmpty() || level < 1 || levels <= 0) {
                    return null;
                }
                return sections.size() - loops.get(Math.max(loops.size() - levels, 0));
            }
            case "EffReturn" -> {
                // To the returnable section it's in, else out of the function.
                int returnable = sections.lastIndexOf("returnable");
                return returnable < 0 ? STOP_TRIGGER : sections.size() - returnable;
            }
            default -> {
                return null;
            }
        }
    }

    /** Where the current sections of {@code type} are, outermost first ({@code section} is any). */
    private List<Integer> sectionsOf(String type) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            String kind = sections.get(i);
            if (type.equals("section") || kind.equals(type)) {
                found.add(i);
            }
        }
        return found;
    }

    /** How the first statement in {@code body} that stops execution stops it, or {@code null}. */
    private Integer bodyIntent(List<Statement> body) {
        for (Statement statement : body) {
            Integer intent = intents.get(statement);
            if (intent != null) {
                return intent;
            }
        }
        return null;
    }

    /** An intent as seen from the section around: one level fewer, as Skript's {@code ExecutionIntent.use}. */
    private static Integer use(Integer intent) {
        return intent == null || intent == STOP_TRIGGER ? intent : intent > 1 ? intent - 1 : null;
    }

    /** Skript's order of intents: stopping fewer sections is less, stopping the trigger the most. */
    private static int rank(int intent) {
        return intent == STOP_TRIGGER ? Integer.MAX_VALUE : intent;
    }

    /**
     * As Skript's SecLoop: whether looping {@code value} surely runs at least once: a literal, or an 'and' list with
     * one, an 'or' list of them. Skript simplifies an expression to a literal as it's parsed, when what it's given
     * is one: text with nothing in it, and these over a literal, whose values there always are. A list given to them
     * isn't one yet.
     */
    private static boolean guaranteed(Object value) {
        return switch (value) {
            case Expr.ListOf list -> list.and() ? list.items().stream().anyMatch(StatementParser::guaranteed)
                    : list.items().stream().allMatch(StatementParser::guaranteed);
            case null -> false;
            default -> literal(value);
        };
    }

    /** Skript's simplified expressions whose values there always are, as literals, if what they're given is. */
    private static final java.util.Set<String> SIMPLIFIED = java.util.Set.of("ExprArithmetic", "ExprJoinSplit",
            "ExprReversedList", "ExprSortedList", "ExprShuffledList", "ExprAlphabetList");

    /** Whether Skript has {@code value} as a literal once it's loaded: see {@link #guaranteed}. */
    private static boolean literal(Object value) {
        return switch (value) {
            case Expr.Literal literal -> true;
            // Words a type reads, as Skript's unparsed literal becomes that type's literal.
            case Expr.Unresolved words -> words.verified();
            // A quoted string with nothing in it is Skript's LiteralString.
            case Expr.Text text -> text.embedded().isEmpty();
            case Expr.Syntax syntax -> SIMPLIFIED.contains(syntax.syntax().syntax().id())
                    && syntax.syntax().match().values().stream().allMatch(v -> v == null || literal(v))
                    && syntax.syntax().match().values().stream().anyMatch(v -> v != null);
            default -> false;
        };
    }

    /** Parses code in a section of {@code kind} (see {@link #sections}). */
    private <T> T inSection(String kind, java.util.function.Supplier<T> code) {
        sections.add(kind);
        try {
            return code.get();
        } finally {
            sections.removeLast();
        }
    }

    /** Parses code Skript loads as a trigger of its own, which starts in no sections. */
    private <T> T apart(java.util.function.Supplier<T> code) {
        java.util.List<String> outer = sections;
        sections = new ArrayList<>();
        try {
            return code.get();
        } finally {
            sections = outer;
        }
    }

    /** Set by {@link #simpleOnce} when the line has a pausing expression and was read as not after a delay. */
    private boolean pausing;

    /**
     * A line on its own. One with an addon's pausing expression waits for its value before anything else on the line
     * runs, so for Skript's rules it's as if the line started with a {@code wait}: certainly after a delay, from the
     * line itself on. Skript has no such expressions; reading the line again from that state makes its own checks
     * ({@code return}, {@code cancel event}, changing the event's values) follow the rules they follow after a wait.
     */
    private Statement simple(Line line) {
        int mark = diagnostics.mark();
        pausing = false;
        Statement statement = simpleOnce(line);
        if (!pausing) {
            return statement;
        }
        pausing = false;
        diagnostics.takeSince(mark);
        delay = DelayState.DELAYED;
        delays.put(line, delay);
        statement = simpleOnce(line);
        // It stays certain after the line, whatever the line does.
        delay = DelayState.DELAYED;
        return statement;
    }

    private Statement simpleOnce(Line line) {
        context.delay(delay);
        if (line.text().strip().equalsIgnoreCase(PARSE_SECTION + ":")) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        String invalid = invalidLine(line.text());
        if (invalid != null) {
            error(line, invalid);
            return new Statement.Invalid(line);
        }
        LineParser parser = new LineParser(context, line.text());
        int end = line.text().length();
        Object parsed = simple(parser, end);
        EventValues.Scope scope = context.eventScope();
        if (scope != null && parser.refusedEventValues() && (parsed == null || unresolved(parsed) > 0)) {
            // Without the event values the code can't have, nothing parses, or only by leaning on words nothing
            // knows: with them it may, for the compiler to say which there's none of, as Skript does when nothing
            // else parses ('event-number' isn't 'event - number').
            context.eventScope(null);
            try {
                LineParser loose = new LineParser(context, line.text());
                Object other = simple(loose, end);
                if (other != null && (parsed == null || unresolved(other) < unresolved(parsed))) {
                    parser = loose;
                    parsed = other;
                }
            } finally {
                context.eventScope(scope);
            }
        }
        if (parsed instanceof ParsedSyntax syntax && experimentalOnly(parser, syntax)) {
            error(line, parser.experimentProblem());
            return new Statement.Invalid(line);
        }
        // A change whose value is text Skript can only read as an unparsed literal fails as its init does, while parsing.
        String unusable = parsed instanceof ParsedSyntax syntax ? unusableChanger(line.text(), syntax) : null;
        if (unusable != null) {
            error(line, unusable);
            return new Statement.Invalid(line);
        }
        if (delay != DelayState.DELAYED && parsed != null && PausingExpressions.any(parsed, context.registry())) {
            // The line waits for an addon's value before anything else on it runs, so it's read again as after a
            // wait (see pausingLine).
            pausing = true;
            return new Statement.Invalid(line);
        }
        if (parsed instanceof Expr.FunctionCall call) {
            return new Statement.Call(line, call);
        }
        // A type property turned down for its values' type is the line's error, as in Skript, where else only
        // words nothing understands would be read.
        if (parser.propertyProblem(parsed) != null) {
            error(line, parser.propertyProblem(parsed));
            return new Statement.Invalid(line);
        }
        ParsedSyntax effect = parsed instanceof ParsedSyntax syntax && syntax.syntax().kind() == SyntaxKind.EFFECT
                ? syntax : null;
        if (parsed != null) {
            parser.listWarnings(parsed);
        }
        warn(line, parser);
        if (effect != null && !sectionOnly(effect).isEmpty()) {
            error(line, "This expression requires a section.");
            return new Statement.Invalid(line);
        }
        if (effect != null) {
            check(line, effect);
            if (context.registry().suppressesWarnings(effect.syntax())) {
                suppress(line, effect);
            }
            if (effect.syntax().id().equals("EffTeleport") && "EffSecSpawn".equals(linkedId)) {
                // Skript's EffTeleport refuses it first thing, so it doesn't make the code wait either.
                error(line, "You cannot teleport an entity that hasn't spawned yet. Ensure you're using the location"
                        + " expression from the spawn section pattern.");
                return new Statement.Invalid(line);
            }
            hint(effect);
            delayAfter(effect);
            return new Statement.Effect(line, effect);
        }
        if (parsed instanceof ParsedSyntax condition) {
            check(line, condition);
            return new Statement.Condition(line, condition);
        }
        String problem = context.hints().active() ? hintProblem(line) : null;
        if (problem == null) {
            problem = parser.functionProblem();
        }
        if (problem == null) {
            problem = LineParser.oddPercents(line.text());
        }
        if (problem == null) {
            problem = changerProblem(line.text());
        }
        if (problem == null) {
            problem = compareProblem(line.text());
        }
        error(line, problem != null ? problem : "Can't understand this condition/effect: " + line.text());
        return new Statement.Invalid(line);
    }

    /**
     * Whether the line only parsed by leaning on words nothing understands, where experimental syntax the script
     * can't use would have gone: Skript reports the experiment then, as no literal is better than its error.
     */
    private static boolean experimentalOnly(LineParser parser, ParsedSyntax parsed) {
        return parser.experimentProblem() != null && LineParser.countUnresolved(parsed) > 0;
    }

    /**
     * For a change effect whose value is text Skript can only read as an unparsed literal (not words), the error its
     * init gives: the literal's own, or that it can't understand it.
     */
    private static String unusableChanger(String key, ParsedSyntax effect) {
        // Only where any value goes (a variable): else Skript says what the target can't take, which the compiler does.
        if (!effect.syntax().id().equals("EffChange") || !effect.match().values().stream().allMatch(value -> value == null
                || value instanceof Expr.Unresolved || value instanceof Expr.Variable)) {
            return null;
        }
        for (Object value : effect.match().values()) {
            if (value instanceof Expr.Unresolved words && !words.verified() && !Literals.isWords(words.text())) {
                return words.problem() != null ? words.problem()
                        : "Can't understand this expression: '" + words.text().strip() + "'";
            }
        }
        return null;
    }

    /** A one-line conditional's header, with its condition. */
    private static final Pattern IF_HEADER = Pattern.compile("(?i)(?:else )?(?:parse )?if (.+)");
    private static final Pattern ELSE_IF_HEADER = Pattern.compile("(?i)else (?:parse )?if\\b.*");
    /** Skript's while loop's header: {@code [do] while <.+>}. */
    private static final Pattern WHILE_HEADER = Pattern.compile("(?i)(?:do )?while (.+)");

    /** Skript's change effect's shapes, with what's changed and what it's changed by. */
    private static final Pattern CHANGE = Pattern.compile(
            "(?i)(?:(?<set>set) (?<t1>.+?) to (?<d1>.+)|(?:add|give) (?<d2>.+?) to (?<t2>.+)"
                    + "|(?:remove (?:all |every )?|subtract )(?<d3>.+?) from (?<t3>.+)|(?:increase|reduce) (?<t4>.+?) by (?<d4>.+))");

    /**
     * For a change effect whose target is understood but not what it's changed by, Skript's error for that
     * value: {@code set {_y} to 1 +} gives {@code Can't understand this expression: '1 +'}.
     */
    private String changerProblem(String text) {
        Matcher matcher = CHANGE.matcher(text.strip());
        if (!matcher.matches()) {
            return null;
        }
        int offset = text.indexOf(text.strip());
        for (int i = 1; i <= 4; i++) {
            if (matcher.group("t" + i) == null) {
                continue;
            }
            LineParser parser = new LineParser(context, text);
            int targetStart = offset + matcher.start("t" + i);
            int deltaStart = offset + matcher.start("d" + i);
            String delta = Text.lower(matcher.group("d" + i));
            if (delta.contains(",") || delta.contains(" and ") || delta.contains(" or ")) {
                // A list isn't one value Skript can say it doesn't understand.
                return null;
            }
            if (parser.isExpression(targetStart, offset + matcher.end("t" + i))
                    && !parser.isExpression(deltaStart, offset + matcher.end("d" + i))) {
                return "Can't understand this expression: '" + matcher.group("d" + i).strip() + "'";
            }
        }
        return null;
    }

    /** Skript's comparison's operators, in the order its patterns try them. */
    private static final Pattern[] COMPARISONS = {
        Pattern.compile("(?i)(?<a>.+?)\\s*(?:>=|<=|!=|>|<|=)\\s*(?<b>.+)"),
        Pattern.compile("(?i)(?<a>.+?) (?:is|are|isn't|aren't)(?: not)? (?<b>.+)"),
        Pattern.compile("(?i)(?<a>.+?) (?:was|were|wasn't|weren't)(?: not)? (?<b>.+)"),
        Pattern.compile("(?i)(?<a>.+?) (?:will be|won't be|will not be) (?<b>.+)"),
    };

    /**
     * For a comparison with a side that's a value of a type and one Skript takes as an unparsed literal that
     * isn't plain words (those the compiler checks), Skript's comparison's error: {@code Can't compare a player
     * with 'in the region {regions::3}'}.
     */
    private String compareProblem(String text) {
        String condition = text.strip();
        for (Pattern comparison : COMPARISONS) {
            Matcher matcher = comparison.matcher(condition);
            if (!matcher.matches()) {
                continue;
            }
            LineParser parser = new LineParser(context, condition);
            String a = compared(parser, condition, matcher.start("a"), matcher.end("a"));
            String b = compared(parser, condition, matcher.start("b"), matcher.end("b"));
            if (a == null || b == null || a.startsWith("'") == b.startsWith("'")) {
                return null;
            }
            return "Can't compare " + a + " with " + b;
        }
        return null;
    }

    /**
     * A side of a comparison as Skript's error writes it: a value of a type as that type, text that's no
     * expression and not plain words as itself, quoted; {@code null} if it's neither.
     */
    private String compared(LineParser parser, String text, int start, int end) {
        String side = text.substring(start, end).strip();
        Expr expr = parser.expression(start, end, Expected.OBJECTS);
        if (expr == null || expr instanceof Expr.Unresolved || expr instanceof Expr.Literal) {
            return Literals.isWords(side) || invalidLine(side) != null ? null : "'" + side + "'";
        }
        TypeRef ref = expr.type() == null || expr instanceof Expr.Variable ? null : context.registry().resolveType(expr.type());
        if (ref == null || ref.type().id().equals(SyntaxRegistry.OBJECT)) {
            return null;
        }
        String name = Text.lower(ref.type().name());
        return ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
    }

    /**
     * Skript's check of a line before it's parsed ({@code SkriptParser.validateLine}): its error if the quotes
     * don't pair up, or a text, variable or bracket isn't closed, else {@code null}.
     */
    static String invalidLine(String line) {
        int quotes = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                quotes++;
            }
        }
        if (quotes % 2 != 0) {
            return "Invalid use of quotes (\"). If you want to use quotes in \"quoted text\", double them: \"\".";
        }
        for (int i = 0; i < line.length(); i = next(line, i)) {
            if (i == -1) {
                return "Invalid amount or placement of brackets. Please make sure that each opening bracket has a"
                        + " corresponding closing bracket.";
            }
        }
        return null;
    }

    /** Skript's {@code SkriptParser.next}: the index after the text, variable or bracket at {@code start}, or -1. */
    private static int next(String line, int start) {
        if (start >= line.length()) {
            return -1;
        }
        switch (line.charAt(start)) {
            case '"' -> {
                boolean inExpression = false;
                for (int i = start + 1; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '"' && !inExpression) {
                        if (i == line.length() - 1 || line.charAt(i + 1) != '"') {
                            return i + 1;
                        }
                        i++;
                    } else if (c == '%') {
                        inExpression = !inExpression;
                    }
                }
                return -1;
            }
            case '{' -> {
                int depth = 0;
                for (int i = start + 1; i < line.length(); i++) {
                    if (line.charAt(i) == '}') {
                        if (depth == 0) {
                            return i + 1;
                        }
                        depth--;
                    } else if (line.charAt(i) == '{') {
                        depth++;
                    }
                }
                return -1;
            }
            case '(' -> {
                for (int i = start + 1; i >= 0 && i < line.length(); i = next(line, i)) {
                    if (line.charAt(i) == ')') {
                        return i + 1;
                    }
                }
                return -1;
            }
            default -> {
                return start + 1;
            }
        }
    }

    /** A line as a function call, else an effect, else a condition, or {@code null}. */
    private static Object simple(LineParser parser, int end) {
        Expr.FunctionCall call = parser.call(0, end);
        if (call != null) {
            return call;
        }
        ParsedSyntax effect = parser.effect(0, end);
        return effect != null ? effect : parser.condition(0, end);
    }

    /** How much of a parsed line is left to words nothing parses. */
    private static int unresolved(Object parsed) {
        return parsed instanceof ParsedSyntax syntax ? LineParser.countUnresolved(syntax)
                : LineParser.countUnresolved((Expr) parsed);
    }

    private Statement section(Node.Section section, Statement previous) {
        Line line = section.line();
        context.delay(delay);
        String key = line.key();
        String invalid = invalidLine(key);
        if (invalid != null) {
            error(line, invalid);
            return new Statement.Invalid(line);
        }
        if (key.equalsIgnoreCase(PARSE_SECTION) && section.children().isEmpty()) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        if (key.equalsIgnoreCase(PARSE_SECTION)) {
            // Skript's parse section: its code counts towards the trigger (including any delay), but
            // its problems are captured for the test.
            int mark = diagnostics.mark();
            List<Statement> body = inSection("section", () -> statements(section.children()));
            List<Diagnostic> captured = diagnostics.takeSince(mark);
            return new Statement.Parse(line, body, captured);
        }
        LineParser strict = new LineParser(context, key);
        ParsedSyntax parsed = strict.section(0, key.length());
        EventValues.Scope scope = context.eventScope();
        if (scope != null && strict.refusedSkriptValues() && (parsed == null || unresolved(parsed) > 0)) {
            // As for other lines: with the event values the code can't have, for the compiler to say which.
            context.eventScope(null);
            try {
                LineParser loose = new LineParser(context, key);
                ParsedSyntax other = loose.section(0, key.length());
                if (other != null && (parsed == null || unresolved(other) < unresolved(parsed))) {
                    strict = loose;
                    parsed = other;
                }
            } finally {
                context.eventScope(scope);
            }
        }
        LineParser parser = strict;
        ParsedSyntax header = parsed;
        if (header == null) {
            // An effect whose expression takes a section: 'set {_bar} to a new boss bar:'.
            ParsedSyntax effect = parser.effect(0, key.length());
            List<Expr.Syntax> claimants = sectionOnly(effect);
            if (claimants.size() > 1) {
                error(line, claimError(key, claimants.get(1), "'" + text(key, claimants.getFirst()) + "'"));
                return new Statement.Invalid(line);
            }
            if (effect != null && effect.syntax().id().equals("Delay") && claimants.isEmpty()) {
                // Skript's delayed section: its code runs after the delay, the code after it straight away.
                parser.listWarnings(effect);
                warn(line, parser);
                return new Statement.Section(line, effect, apart(() -> delayedBody(section.children())));
            }
            if (effect != null && hasSectionExpression(effect)) {
                parser.listWarnings(effect);
                warn(line, parser);
                String linked = LINKED.get(sectionExpressionId(effect));
                List<Statement> body = sectionCode(null, () -> linked == null
                        ? inSection("section", () -> repeatable(section.children()))
                        : apart(() -> linkedBody(line, sectionExpressionId(effect), linked, section.children())));
                return body == null ? new Statement.Invalid(line) : new Statement.Section(line, effect, body);
            }
            Expr.FunctionCall call = parser.call(0, key.length());
            if (call != null && call.arguments().stream().anyMatch(a -> a != null && hasSectionExpression(a))) {
                String linkedId = call.arguments().stream().filter(a -> a != null && hasSectionExpression(a))
                        .map(StatementParser::sectionExpressionId).filter(LINKED::containsKey).findFirst().orElse(null);
                String linked = LINKED.get(linkedId);
                List<Statement> body = sectionCode(null, () -> linked == null
                        ? inSection("section", () -> repeatable(section.children()))
                        : apart(() -> linkedBody(line, linkedId, linked, section.children())));
                return body == null ? new Statement.Invalid(line) : new Statement.Section(line, null, body, call);
            }
            if (call != null && effect == null) {
                error(line, "The line '" + key + "' is a valid function call but cannot function as a section (:) because"
                        + " there is no parameter to manage it.");
                return new Statement.Invalid(line);
            }
            String unusable = effect == null ? null : unusableChanger(key, effect);
            if (unusable != null) {
                // Skript's change effect fails to set up before anything claims the section, with its error.
                error(line, unusable);
                return new Statement.Invalid(line);
            }
            if (effect != null) {
                // Skript then reads the line as a statement: if that fails, its error is kept, which the compiler may find.
                Line statement = new Line(line.number(), line.indent(), key);
                delays.put(statement, delay);
                note(statement);
                sectionStatements.put(line, new Statement.Effect(statement, effect));
            }
            if (effect != null || call != null || parser.condition(0, key.length()) != null) {
                error(line, "The line '" + key + "' is a valid statement but cannot function as a section (:) because"
                        + " there is no syntax in the line to manage it.");
                return new Statement.Invalid(line);
            }
            Matcher conditional = IF_HEADER.matcher(key);
            Matcher whileLoop = WHILE_HEADER.matcher(key);
            String lowerKey = Text.lower(key);
            if (parser.experimentProblem() != null) {
                error(line, parser.experimentProblem());
            } else if (ELSE_IF_HEADER.matcher(key).matches()) {
                // Skript then reads the line as a statement, which says it doesn't understand the 'else' (or
                // 'else parse'), and keeps that error.
                error(line, Statement.orphanElse(lowerKey));
            } else if (conditional.matches()) {
                String problem = statementProblem(line, key);
                if (problem == null) {
                    problem = compareProblem(conditional.group(1));
                }
                error(line, problem != null ? problem : "Can't understand this condition: '" + conditional.group(1) + "'");
            } else if (whileLoop.matches()) {
                // Skript's while section's error, unless reading the line as a statement gives one it keeps.
                String problem = statementProblem(line, key);
                error(line, problem != null ? problem : "Can't understand this condition: " + whileLoop.group(1));
            } else if (key.regionMatches(true, 0, "loop ", 0, 5)) {
                error(line, "Can't understand this loop: '" + key.substring(5) + "'");
            } else {
                // 'set {_bar} to a keyed bossbar:': Skript's change effect's error for the value it can't understand.
                String problem = changerProblem(key);
                error(line, problem != null ? problem : "Can't understand this section: " + key);
            }
            return new Statement.Invalid(line);
        }
        if (experimentalOnly(parser, header)) {
            error(line, parser.experimentProblem());
            return new Statement.Invalid(line);
        }
        // What takes the section may refuse it, as Skript's section expressions, then its effect sections, do.
        String refused = argumentText(key, header, sectionRefusal(header));
        if (refused != null) {
            error(line, refused.isEmpty() ? "The line '" + key + "' is a valid statement but cannot function as a section (:)"
                    + " because there is no syntax in the line to manage it." : refused);
            return new Statement.Invalid(line);
        }
        List<Expr.Syntax> claimants = sectionOnly(header);
        if (!claimants.isEmpty()) {
            error(line, claimError(key, claimants.getFirst(), "'" + key + "'"));
            return new Statement.Invalid(line);
        }
        // As for a simple line: a type property turned down for its values' type is the error, not the words.
        if (parser.propertyProblem(header) != null) {
            error(line, parser.propertyProblem(header));
            return new Statement.Invalid(line);
        }
        check(line, header);
        parser.listWarnings(header);
        warn(line, parser);
        String lower = Text.lower(key);
        if (lower.startsWith("else") && !continuesConditional(previous)) {
            error(line, Statement.orphanElse(lower));
            return new Statement.Invalid(line);
        }
        if (lower.startsWith("then") && !(previous instanceof Statement.Conditions)) {
            // Skript's wording, typo included.
            error(line, "'then' has to placed just after a multiline 'if' or 'else if' section");
            return new Statement.Invalid(line);
        }
        if (MULTILINE_CONDITIONAL.matcher(key).matches() && !header.syntax().patterns().get(header.pattern())
                .source().endsWith("<.+>")) {
            String kind = any(lower) ? "'if any'" : "'if all'";
            if (section.children().size() < 2) {
                error(line, kind + " sections must contain at least two conditions");
                return new Statement.Invalid(line);
            }
            List<Line> lines = new ArrayList<>();
            List<ParsedSyntax> conditions = conditions(section.children(), lines, kind);
            if (conditions == null) {
                // As in Skript, one condition that fails fails the whole 'if', so neither its 'then' nor its 'else'
                // is there to run.
                return new Statement.Invalid(line);
            }
            return new Statement.Conditions(line, header, conditions, lines);
        }
        if (PARSE_IF.matcher(key).matches()) {
            // 'parse if' only parses its code when the condition is true while loading, which can't be
            // known here: parse it, but keep its problems out of the report.
            int mark = diagnostics.mark();
            List<Statement> body = inSection("conditional", () -> sectionBody(section.children()));
            diagnostics.takeSince(mark);
            return new Statement.Section(line, header, body);
        }
        if (isConditional(lower)) {
            statementError(line, key);
            return new Statement.Section(line, header, inSection("conditional", () -> sectionBody(section.children())));
        }
        if (header.syntax().id().equals(SUPPRESS_HINTS)) {
            boolean before = context.hints().suppressed();
            context.hints().suppress(!header.match().tags().contains("stop"));
            // Its code runs once, in place: a delay in it carries on after it.
            List<Statement> body = inSection("section", () -> sectionBody(section.children()));
            context.hints().suppress(before);
            return new Statement.Section(line, header, body);
        }
        if (context.registry().suppressesWarnings(header.syntax())) {
            // Only for the section's code.
            java.util.Set<String> before = java.util.Set.copyOf(suppressed);
            suppress(line, header);
            List<Statement> body = inSection("section", () -> sectionBody(section.children()));
            suppressed.clear();
            suppressed.addAll(before);
            return new Statement.Section(line, header, body);
        }
        if (header.syntax().id().equals(FILTER)) {
            return filter(section, header);
        }
        if (context.registry().delays(header.syntax())) {
            // A pausing section (a database transaction): it waits before its code, which runs once, and after.
            delay = DelayState.DELAYED;
            List<Statement> body = inSection("section", () -> sectionCode(header, () -> statements(section.children())));
            delay = DelayState.DELAYED;
            return new Statement.Section(line, header, body);
        }
        String id = header.syntax().id();
        String linked = LINKED.get(id);
        if (linked != null) {
            List<Statement> body = sectionCode(header, () -> apart(() -> linkedBody(line, id, linked, section.children())));
            return body == null ? new Statement.Invalid(line) : new Statement.Section(line, header, body);
        }
        List<Statement> body = inSection(switch (id) {
            case "SecLoop", "SecFor", "SecWhile" -> "loop";
            case "SecReturnable" -> "returnable";
            default -> "section";
        }, () -> switch (id) {
            case "SecCatchErrors" -> catchErrorsBody(line, section.children());
            // Skript's test sections that run their code once, in place.
            case "SecReturnable" -> {
                boolean outer = detached;
                detached = false;
                try {
                    yield sectionBody(section.children());
                } finally {
                    detached = outer;
                }
            }
            case "SecCustomDefault" -> sectionBody(section.children());
            case "SecRunOn" -> {
                // On Folia the trigger may wait to get to the thread that runs it, and to get back.
                if (delay != DelayState.DELAYED) {
                    delay = DelayState.MAYBE_DELAYED;
                }
                yield sectionCode(header, () -> repeatable(section.children()));
            }
            default -> sectionCode(header, () -> repeatable(section.children(), id.equals("SecFor") ? header : null));
        });
        return body == null ? new Statement.Invalid(line) : new Statement.Section(line, header, body);
    }

    /**
     * Skript's sections whose code it loads as a trigger of its own, run apart from the code around them, by the
     * names their errors give: {@code Delays can't be used within a 'spawn' section}.
     */
    private static final Map<String, String> LINKED = Map.of(
            "EffSecSpawn", "spawn",
            "EffSecShoot", "shoot",
            "ExprSecCreateBossBar", "create bossbar",
            "ExprSecCreateWorldBorder", "create worldborder",
            "ExprSecDamageSource", "custom damage source",
            "ExprSecPotionEffect", "create potion effect",
            "ExprSecBlankEquipComp", "blank equippable component",
            "ExprSecCreateLootContext", "create loot context");

    /**
     * The code of a section Skript loads as a trigger of its own ({@code spawn a pig:}): it starts with no delay, as
     * Skript resets its parser for it, and leaves the delay of the code around it as it was. It can't wait, so if it
     * may, the section fails and this is {@code null}. As the section's line then fails as a whole, Skript keeps only
     * the first error its code gave, or else its own, and none of the warnings.
     */
    private List<Statement> linkedBody(Line line, String id, String name, List<Node> nodes) {
        DelayState before = delay;
        boolean outer = detached;
        String outerId = linkedId;
        delay = DelayState.NOT_DELAYED;
        detached = true;
        linkedId = id;
        int mark = diagnostics.mark();
        context.hints().enter();
        boolean waits;
        List<Statement> body;
        try {
            body = statements(nodes);
            waits = delay != DelayState.NOT_DELAYED;
        } finally {
            context.hints().exit();
            delay = before;
            detached = outer;
            linkedId = outerId;
        }
        if (!waits) {
            return body;
        }
        // Skript keeps the first error of the best quality: words nothing understands are a lesser error than the
        // section's own, which comes after its code's.
        Diagnostic first = diagnostics.takeSince(mark).stream().filter(d -> d.severity() == Severity.ERROR
                && !Diagnostic.lesserQuality(d.message())).findFirst().orElse(null);
        if (first != null) {
            diagnostics.add(first);
        } else {
            error(line, "Delays can't be used within a '" + name + "' section");
        }
        // An earlier error only compiling finds is the one Skript keeps instead: the compiler checks.
        waitingSections.put(line, new ParsedScript.WaitingSection(id, name, body, first == null ? -1 : first.line()));
        return null;
    }

    /**
     * A catch errors section's code, which starts with no delay and can't certainly wait, as Skript's
     * SecCatchErrors. The delay before it carries on after it; if it fails, its code's does, as in Skript.
     */
    private List<Statement> catchErrorsBody(Line line, List<Node> nodes) {
        DelayState before = delay;
        delay = DelayState.NOT_DELAYED;
        List<Statement> body = sectionBody(nodes);
        if (delay == DelayState.DELAYED) {
            error(line, "Delays can't be used within a catch errors section.");
            return null;
        }
        delay = before;
        return body;
    }

    /**
     * How an effect leaves the delay, as its {@code init} does in Skript: a wait waits; a teleport may (to load the
     * chunk), unless forced, whatever came before; so does a do-if's effect, condition or not.
     */
    private void delayAfter(ParsedSyntax effect) {
        if (context.registry().delays(effect.syntax())) {
            delay = DelayState.DELAYED;
            return;
        }
        switch (effect.syntax().id()) {
            case "EffTeleport" -> {
                if (!effect.match().tags().contains("force")) {
                    delay = DelayState.MAYBE_DELAYED;
                }
            }
            case "EffLoadServerIcon" -> delay = DelayState.DELAYED;
            case "EffDoIf" -> {
                if (!effect.match().regexValues().isEmpty() && effect.match().regexValues().getFirst() instanceof ParsedSyntax inner) {
                    delayAfter(inner);
                }
            }
            default -> {}
        }
    }

    /**
     * Whether {@code statement} is a {@code return} that can't be used where it is, so doesn't stop the code: after a
     * code that may have waited, or in code loaded apart from its function. The compiler gives the error.
     */
    private boolean failingReturn(Statement.Effect statement) {
        return statement.effect().syntax().id().equals("EffReturn")
                && (detached || delays.getOrDefault(statement.line(), DelayState.NOT_DELAYED) != DelayState.NOT_DELAYED);
    }

    /** The id of the section expression in {@code syntax} that takes the section under it, or {@code null}. */
    private static String sectionExpressionId(ParsedSyntax syntax) {
        if (syntax.syntax().id().startsWith("ExprSec")) {
            return syntax.syntax().id();
        }
        for (Object value : syntax.match().values()) {
            String id = value instanceof Expr expr ? sectionExpressionId(expr) : null;
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static String sectionExpressionId(Expr expr) {
        return switch (expr) {
            case Expr.Syntax syntax -> sectionExpressionId(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().map(StatementParser::sectionExpressionId)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            default -> null;
        };
    }

    /** Skript's filter section ({@code filter {_l::*} to match all:}), whose lines are conditions. */
    private static final String FILTER = "SecFilter";

    /**
     * A filter section. As Skript's {@code SecFilter}, each line is a condition, and the first that isn't one fails
     * the whole section with its error, and the lines after it aren't read: the filter isn't there to run.
     */
    private Statement filter(Node.Section section, ParsedSyntax header) {
        Line line = section.line();
        int mark = diagnostics.mark();
        List<Statement> body = sectionCode(header, () -> repeatable(section.children(), null));
        List<Diagnostic> found = new ArrayList<>(diagnostics.takeSince(mark));
        Statement failed = null;
        for (Statement statement : body) {
            if (statement instanceof Statement.Condition) {
                continue;
            }
            failed = statement;
            Line at = statement.line();
            if (section.children().stream().anyMatch(child -> child.line() == at && child instanceof Node.Section)) {
                found.removeIf(d -> d.line() >= at.number());
                error(at, "Filter sections may not contain other sections");
            } else {
                found.removeIf(d -> d.line() > at.number());
                // Skript's error for a line no condition understands; a more particular one is kept.
                found.replaceAll(d -> d.line() == at.number() && d.message().startsWith("Can't understand this condition/effect: ")
                        ? new Diagnostic(d.severity(), d.file(), d.line(), d.column(), d.length(),
                                "Can't understand this condition: '" + at.text().strip() + "'")
                        : d);
                if (!(statement instanceof Statement.Invalid)) {
                    found.add(new Diagnostic(co.xenastudios.neoskript.frontend.source.Severity.ERROR, diagnostics.file()
                            .name(), at.number(), at.column(), at.text().length(), "Can't understand this condition: '"
                            + at.text().strip() + "'"));
                }
            }
            break;
        }
        found.forEach(diagnostics::add);
        if (failed != null) {
            return new Statement.Invalid(line);
        }
        return new Statement.Section(line, header, body);
    }

    /** Whether a multiline conditional is an {@code if any} one. */
    private static boolean any(String lower) {
        return lower.contains(" any") || lower.contains("at least one");
    }

    /** A {@code for} loop's code: its value variable may also be any of what the list holds. */
    private List<Statement> loopBody(List<Node> nodes, ParsedSyntax loop) {
        DelayState before = delay;
        context.hints().enter();
        List<Statement> body;
        try {
            List<Object> values = loop.match().values();
            Object looped = values.getLast();
            Object value = loop.pattern() == 1 ? null : values.get(loop.pattern() == 0 ? 0 : 1);
            if (value instanceof Expr.Variable variable && simple(variable) && looped instanceof Expr list) {
                java.util.Set<String> types = new java.util.LinkedHashSet<>(types(list));
                if (!types.isEmpty()) {
                    types.addAll(context.hints().get(variable.name()));
                    context.hints().set(variable.name(), types);
                }
            }
            body = statements(nodes);
        } finally {
            context.hints().exit();
        }
        if (stops(body)) {
            delay = before;
        }
        return body;
    }

    /**
     * A delayed section's code. The code after the section hasn't waited, as in Skript's Delay. Nor, to Skript's
     * parser, has the code in it: Skript loads it as a trigger of its own, which resets the parser's delay after
     * its Delay marks it delayed, so its code is read as not having waited, and {@code return} can't be used in it.
     */
    private List<Statement> delayedBody(List<Node> nodes) {
        DelayState before = delay;
        boolean outer = detached;
        delay = DelayState.NOT_DELAYED;
        detached = true;
        context.hints().enter();
        try {
            return statements(nodes);
        } finally {
            context.hints().exit();
            delay = before;
            detached = outer;
        }
    }

    /**
     * A section's code. If it always ends the trigger ({@code stop}, {@code return}), a delay in it doesn't
     * carry past the section, as Skript's execution intents have it.
     */
    private List<Statement> sectionBody(List<Node> nodes) {
        DelayState before = delay;
        context.hints().enter();
        List<Statement> body;
        try {
            body = statements(nodes);
        } finally {
            context.hints().exit();
        }
        if (stops(body)) {
            delay = before;
        }
        return body;
    }

    /**
     * Why a line that didn't parse fails by its type hints, as Skript says: the line is parsed again without
     * them, and the first variable whose hints don't fit where it was put is the reason. {@code null} if
     * the line doesn't parse that way either, or every variable fits.
     */
    private String hintProblem(Line line) {
        LineParser loose = new LineParser(context, line.text(), false);
        int end = line.text().length();
        Object parsed = loose.call(0, end);
        if (parsed == null) {
            parsed = loose.effect(0, end);
        }
        if (parsed == null) {
            parsed = loose.condition(0, end);
        }
        if (parsed == null) {
            return null;
        }
        LineParser strict = new LineParser(context, line.text());
        return hintProblem(parsed, strict);
    }

    private String hintProblem(Object node, LineParser strict) {
        switch (node) {
            case ParsedSyntax syntax -> {
                co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs = specs(syntax);
                List<Object> values = syntax.match().values();
                for (int i = 0; i < values.size(); i++) {
                    String problem = i < specs.length && specs[i] != null
                            ? hintProblem(values.get(i), context.expected(specs[i]), strict) : null;
                    if (problem != null) {
                        return problem;
                    }
                }
                for (Object value : syntax.match().regexValues()) {
                    String problem = hintProblem(value, strict);
                    if (problem != null) {
                        return problem;
                    }
                }
            }
            case Expr.FunctionCall call -> {
                for (int i = 0; i < call.arguments().size() && i < call.function().parameters().size(); i++) {
                    FunctionInfo.Parameter parameter = call.function().parameters().get(i);
                    TypeRef ref = context.registry().resolveType(parameter.type());
                    if (ref != null && call.arguments().get(i) != null) {
                        String problem = hintProblem(call.arguments().get(i),
                                context.expected(new TypeRef(ref.type(), parameter.plural())), strict);
                        if (problem != null) {
                            return problem;
                        }
                    }
                }
            }
            case Expr.Syntax syntax -> {
                return hintProblem(syntax.syntax(), strict);
            }
            default -> {}
        }
        return null;
    }

    private String hintProblem(Object value, Expected expected, LineParser strict) {
        if (value instanceof Expr.Variable variable) {
            return strict.hintFits(variable, expected) ? null : strict.hintProblem();
        }
        if (value instanceof Expr.ListOf list) {
            for (Expr item : list.items()) {
                String problem = hintProblem(item, expected, strict);
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        return hintProblem(value, strict);
    }

    private static co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs(ParsedSyntax syntax) {
        co.xenastudios.neoskript.frontend.pattern.CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs =
                new co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        return specs;
    }

    private static void collect(co.xenastudios.neoskript.frontend.pattern.PatternElement element,
            co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs) {
        switch (element) {
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Placeholder placeholder ->
                    specs[placeholder.index()] = placeholder.spec();
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Sequence sequence ->
                    sequence.elements().forEach(e -> collect(e, specs));
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Choice choice ->
                    choice.alternatives().forEach(a -> collect(a.body(), specs));
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Optional optional ->
                    collect(optional.choice(), specs);
            default -> {}
        }
    }

    /** Skript's {@code suppress local variable type hints}, alone or as a section. */
    private static final String SUPPRESS_HINTS = "EffSecSuppressTypeHints";

    /** What an effect tells the type hints: {@code set {_x} to 10}, {@code delete {_x}}, {@code copy}... */
    private void hint(ParsedSyntax effect) {
        TypeHints hints = context.hints();
        List<Object> values = effect.match().values();
        switch (effect.syntax().id()) {
            case SUPPRESS_HINTS -> hints.suppress(!effect.match().tags().contains("stop"));
            case "EffChange" -> {
                switch (effect.pattern()) {
                    case 0 -> forEachVariable(values.get(0), v -> hints.set(v.name(), types((Expr) values.get(1))));
                    case 1 -> forEachVariable(values.get(1), v -> hints.add(v.name(), types((Expr) values.get(0))));
                    case 6 -> forEachVariable(values.get(0), v -> hints.delete(v.name()));
                    default -> {}
                }
            }
            case "EffCopy" -> forEachVariable(values.get(1), v -> hints.set(v.name(),
                    values.get(0) instanceof Expr source ? types(source) : java.util.Set.of()));
            case "EffTransform" -> {
                if (!effect.match().regexValues().isEmpty() && effect.match().regexValues().getFirst() instanceof Expr mapping) {
                    forEachVariable(values.get(0), v -> hints.set(v.name(), types(mapping)));
                }
            }
            default -> {}
        }
    }

    /** How far an effect stops execution, for the type hints: {@code stop}, {@code exit 2 sections}. */
    private void stopsHints(ParsedSyntax effect, boolean conditional) {
        switch (effect.syntax().id()) {
            case "EffExit" -> {
                int levels = -1;
                if (effect.pattern() == 1) {
                    levels = 1;
                } else if (effect.pattern() == 2 && effect.match().regexValues().getFirst() instanceof java.util.regex.MatchResult n) {
                    levels = Integer.parseInt(n.group());
                }
                context.hints().stop(levels, conditional);
            }
            case "EffReturn" -> context.hints().stop(-1, conditional);
            case "EffContinue" -> context.hints().stop(1, conditional);
            case "EffDoIf" -> {
                if (effect.match().regexValues().getFirst() instanceof ParsedSyntax inner) {
                    stopsHints(inner, true);
                }
            }
            default -> {}
        }
    }

    private static void forEachVariable(Object target, java.util.function.Consumer<Expr.Variable> action) {
        if (target instanceof Expr.Variable variable && simple(variable)) {
            action.accept(variable);
        } else if (target instanceof Expr.ListOf list) {
            list.items().forEach(item -> forEachVariable(item, action));
        }
    }

    /** A local variable whose name is all written out: only those have hints, as in Skript. */
    static boolean simple(Expr.Variable variable) {
        return variable.local() && variable.embedded().isEmpty();
    }

    /** The types {@code expr}'s values may be, as far as is known while parsing; none if unknown. */
    private java.util.Set<String> types(Expr expr) {
        return switch (expr) {
            case Expr.Variable variable when simple(variable) -> context.hints().get(variable.name());
            case Expr.ListOf list -> {
                java.util.Set<String> all = new java.util.LinkedHashSet<>();
                for (Expr item : list.items()) {
                    java.util.Set<String> types = types(item);
                    if (types.isEmpty()) {
                        yield java.util.Set.of();
                    }
                    all.addAll(types);
                }
                yield all;
            }
            case Expr.Variable variable -> java.util.Set.of();
            case Expr.Unresolved unresolved -> java.util.Set.of();
            default -> expr.type() == null || expr.type().equals(SyntaxRegistry.OBJECT) ? java.util.Set.of()
                    : java.util.Set.of(expr.type());
        };
    }

    /** {@code suppress unreachable code warnings}, or {@code unsuppress}: changes what's suppressed from here on. */
    private void suppress(Line line, ParsedSyntax syntax) {
        if (syntax.match().tags().contains("conflict")) {
            diagnostics.warning(line.number(), line.column(), line.text().length(),
                    "Variable conflict warnings no longer need suppression, as they have been removed altogether");
        }
        boolean allow = syntax.match().tags().contains("stop");
        for (String tag : syntax.match().tags()) {
            if (!tag.equals("stop")) {
                if (allow) {
                    suppressed.remove(tag);
                } else {
                    suppressed.add(tag);
                }
            }
        }
    }

    /** Reports the warnings the line's parser found ({@code tag:message}), unless they're suppressed. */
    private void warn(Line line, LineParser parser) {
        warn(line, parser.warnings());
    }

    /** Reports {@code warnings} ({@code tag:message}) at {@code line}, unless they're suppressed, and clears them. */
    void warn(Line line, java.util.Set<String> warnings) {
        for (String warning : warnings) {
            int colon = warning.indexOf(':');
            if (!suppressed.contains(warning.substring(0, colon))) {
                diagnostics.warning(line.number(), line.column(), line.text().length(), warning.substring(colon + 1));
            }
        }
        warnings.clear();
    }

    /** Whether {@code body} surely stops the trigger, as Skript's {@code triggerExecutionIntent} being StopTrigger. */
    private boolean stops(List<Statement> body) {
        Integer intent = bodyIntent(body);
        return intent != null && intent == STOP_TRIGGER;
    }

    /**
     * Code in a section that may run any number of times, like a loop. Afterwards the trigger is delayed
     * only if it already was; if the code may wait, it's maybe delayed.
     */
    private List<Statement> repeatable(List<Node> nodes) {
        return repeatable(nodes, null);
    }

    /** {@link #repeatable(List)}, with a loop's variables hinted first ({@code for {_value} in X}). */
    private List<Statement> repeatable(List<Node> nodes, ParsedSyntax loop) {
        DelayState before = delay;
        List<Statement> body = loop == null ? sectionBody(nodes) : loopBody(nodes, loop);
        // As Skript's loadOptionalCode: if the code had surely waited, the state is what the code left.
        if (before != DelayState.DELAYED) {
            delay = before.merge(List.of(before, delay));
        }
        return body;
    }

    /**
     * The conditions of a multiline {@code if}, each on its own line, or {@code null} once one fails, as Skript's
     * {@code SecConditional} stops at the first: its error is reported and the rest aren't read.
     */
    private List<ParsedSyntax> conditions(List<Node> nodes, List<Line> lines, String kind) {
        List<ParsedSyntax> conditions = new ArrayList<>();
        for (Node node : nodes) {
            Line line = node.line();
            if (node instanceof Node.Section) {
                error(line, kind + " sections may not contain other sections");
                return null;
            }
            delays.put(line, delay);
            note(line);
            LineParser parser = new LineParser(context, line.text());
            ParsedSyntax condition = node instanceof Node.Simple ? parser.condition(0, line.text().length()) : null;
            if (node instanceof Node.Simple && context.eventScope() != null && parser.refusedEventValues()
                    && (condition == null || unresolved(condition) > 0)) {
                // With event values the code can't have, for the compiler to say which, as for other lines.
                EventValues.Scope scope = context.eventScope(null);
                try {
                    ParsedSyntax other = new LineParser(context, line.text()).condition(0, line.text().length());
                    if (other != null && (condition == null || unresolved(other) < unresolved(condition))) {
                        condition = other;
                    }
                } finally {
                    context.eventScope(scope);
                }
            }
            if (condition != null && experimentalOnly(parser, condition)) {
                condition = null;
            }
            if (condition == null) {
                // Skript keeps an error from the code in a text, or about a function, over its own.
                String specific = parser.functionProblem();
                boolean kept = specific != null && (specific.startsWith("The function ")
                        || specific.startsWith("Can't understand this expression: ")
                                && !specific.startsWith("Can't understand this expression: '"));
                error(line, parser.experimentProblem() != null ? parser.experimentProblem()
                        : kept ? specific : "Can't understand this condition: '" + line.text() + "'");
                return null;
            } else {
                check(line, condition);
                parser.listWarnings(condition);
                warn(line, parser);
                conditions.add(condition);
                lines.add(line);
            }
        }
        return conditions;
    }

    /** Runs the parse-time checks of every element in a parsed line. */
    private void check(Line line, ParsedSyntax syntax) {
        ParseCheck check = context.registry().check(syntax.syntax());
        if (check != null) {
            String problem = check.check(syntax.match(), delay);
            if (problem != null) {
                error(line, problem);
            }
        }
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                check(line, inner);
            }
        }
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr.Syntax inner) {
                check(line, inner.syntax());
            }
        }
    }

    /** {@code problem}, or for {@link ParseCheck#ARGUMENT_TEXT} and an index, the text written for that argument. */
    private static String argumentText(String key, ParsedSyntax syntax, String problem) {
        if (problem == null || !problem.startsWith(ParseCheck.ARGUMENT_TEXT)) {
            return problem;
        }
        int index = Integer.parseInt(problem.substring(ParseCheck.ARGUMENT_TEXT.length()));
        Object value = index < syntax.match().values().size() ? syntax.match().values().get(index) : null;
        if (!(value instanceof Expr.Text text)) {
            return "";
        }
        return key.substring(text.start() + 1, text.end() - 1).replace("\"\"", "\"");
    }

    /** Why the line's syntax won't take the section under it, or {@code null} if it does. */
    private String sectionRefusal(ParsedSyntax header) {
        for (Object value : header.match().values()) {
            if (value instanceof Expr.Syntax inner) {
                ParseCheck check = context.registry().sectionCheck(inner.syntax().syntax());
                String problem = check == null ? null : check.check(inner.syntax().match(), delay);
                if (problem != null) {
                    return problem;
                }
            }
        }
        ParseCheck check = context.registry().sectionCheck(header.syntax());
        return check == null ? null : check.check(header.match(), delay);
    }

    /** The expressions in a parsed line that can only be used with a section, in order. */
    private List<Expr.Syntax> sectionOnly(ParsedSyntax syntax) {
        List<Expr.Syntax> found = new ArrayList<>();
        if (syntax != null) {
            sectionOnly(syntax, found);
        }
        return found;
    }

    private void sectionOnly(ParsedSyntax syntax, List<Expr.Syntax> found) {
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr expr) {
                sectionOnly(expr, found);
            }
        }
        // The condition of 'if X:' and the like.
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                sectionOnly(inner, found);
            }
        }
    }

    private void sectionOnly(Expr expr, List<Expr.Syntax> found) {
        switch (expr) {
            case Expr.Syntax syntax -> {
                if (context.registry().sectionOnly(syntax.syntax().syntax())) {
                    found.add(syntax);
                }
                sectionOnly(syntax.syntax(), found);
            }
            case Expr.ListOf list -> list.items().forEach(item -> sectionOnly(item, found));
            default -> {}
        }
    }

    /** Skript's error for a second syntax claiming a line's section. */
    private static String claimError(String key, Expr.Syntax claimant, String claimedBy) {
        return "The syntax '" + text(key, claimant) + "' tried to claim the current section, but it was already claimed by "
                + claimedBy + ". You cannot have two section-starters in the same line.";
    }

    private static String text(String key, Expr.Syntax syntax) {
        return key.substring(syntax.start(), syntax.end()).strip();
    }

    /** Whether a parsed effect contains an expression that takes a section (an {@code ExprSec} element). */
    private static boolean hasSectionExpression(ParsedSyntax syntax) {
        if (syntax.syntax().id().startsWith("ExprSec")) {
            return true;
        }
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr expr && hasSectionExpression(expr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSectionExpression(Expr expr) {
        return switch (expr) {
            case Expr.Syntax syntax -> hasSectionExpression(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().anyMatch(StatementParser::hasSectionExpression);
            default -> false;
        };
    }

    private static boolean continuesConditional(Statement previous) {
        if (previous instanceof Statement.Conditions) {
            return true;
        }
        if (previous instanceof Statement.Section section) {
            String key = Text.lower(section.line().key());
            if (key.startsWith("then")) {
                return true;
            }
            return !key.equals("else") && (key.contains("if ") || key.startsWith("if") || isImplicitCondition(section));
        }
        return false;
    }

    private static boolean isImplicitCondition(Statement.Section section) {
        return section.header() != null
                && section.header().syntax().patterns().get(section.header().pattern()).source().startsWith("implicit:");
    }

    /** Parses {@code text} on its own as a value of {@code type}, reporting it at {@code line} if it doesn't. */
    Expr expression(Line line, String text, String type, boolean plural) {
        TypeInfo info = context.registry().type(type);
        Expected expected = info == null ? Expected.OBJECTS : context.expected(new TypeRef(info, plural));
        // Values given on their own, like defaults, may be any event value: the compiler says if there's none.
        EventValues.Scope scope = context.eventScope(null);
        Expr expr;
        try {
            expr = new LineParser(context, text).expression(0, text.length(), expected);
        } finally {
            context.eventScope(scope);
        }
        if (expr == null) {
            error(line, "Can't understand this expression: '" + text + "'");
        }
        return expr;
    }

    /**
     * Parses an entry's value as text that may have code in {@code %...%}, as Skript's variable string entries are,
     * reporting Skript's error at {@code line} if it doesn't parse (its code's, if that's what fails).
     */
    Expr text(Line line, String value) {
        String text = '"' + value.replace("\"", "\"\"") + '"';
        TypeInfo info = context.registry().type("string");
        Expected expected = info == null ? Expected.OBJECTS : context.expected(new TypeRef(info, false));
        EventValues.Scope scope = context.eventScope(null);
        LineParser parser = new LineParser(context, text);
        Expr expr;
        try {
            expr = parser.expression(0, text.length(), expected);
        } finally {
            context.eventScope(scope);
        }
        if (expr == null) {
            String problem = parser.functionProblem();
            error(line, problem != null ? problem : "Can't understand this expression: '" + text + "'");
        }
        return expr;
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), line.column(), line.text().length(), message);
    }
}
