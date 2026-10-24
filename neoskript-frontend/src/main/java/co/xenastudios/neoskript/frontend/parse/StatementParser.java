/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.DelayState;
import co.xenastudios.neoskript.frontend.registry.ParseCheck;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses the lines of a trigger, function or other code section into statements.
 *
 * <p>It also tracks whether the trigger may have waited by each line, as Skript does: each branch of an
 * {@code if} chain starts from the state before the chain, and the state after it is merged from every
 * branch (see {@link DelayState#merge}). Code that may run any number of times (a loop) can only make the
 * state "maybe".
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

    /** The {@code if} chain being parsed in the current block. */
    private static final class Chain {
        final DelayState before;
        final List<DelayState> ends = new ArrayList<>();
        boolean hasElse;

        Chain(DelayState before) {
            this.before = before;
        }
    }

    StatementParser(ParserContext context, Diagnostics diagnostics) {
        this.context = context;
        this.diagnostics = diagnostics;
    }

    /** The delay state before each line parsed so far, by line. */
    Map<Line, DelayState> delays() {
        return delays;
    }

    /** Parses a trigger's or function's code, which starts with no delay. */
    List<Statement> body(List<Node> nodes) {
        delay = DelayState.NOT_DELAYED;
        return statements(nodes);
    }

    private List<Statement> statements(List<Node> nodes) {
        List<Statement> statements = new ArrayList<>(nodes.size());
        Statement previous = null;
        Chain chain = null;
        for (Node node : nodes) {
            String key = node.line().key().strip().toLowerCase(Locale.ROOT);
            boolean section = node instanceof Node.Section;
            boolean continuesChain = section && (key.startsWith("else") || key.startsWith("then"));
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
            previous = statement;
        }
        if (chain != null) {
            delay = endChain(chain);
        }
        return statements;
    }

    private Statement line(Node node, Statement previous) {
        delays.put(node.line(), delay);
        return switch (node) {
            case Node.Simple simple -> simple(simple.line());
            case Node.Section section -> section(section, previous);
        };
    }

    private static boolean isConditional(String key) {
        return key.startsWith("if") || key.startsWith("else") || key.startsWith("then") || key.startsWith("parse if");
    }

    private static DelayState endChain(Chain chain) {
        List<DelayState> ends = new ArrayList<>(chain.ends);
        if (!chain.hasElse) {
            ends.add(chain.before);
        }
        return chain.before.merge(ends);
    }

    /** One section of an {@code if} chain: an {@code if}, {@code else if}, {@code else} or {@code then}. */
    private Statement branch(Node.Section section, Statement previous, Chain chain) {
        String key = section.line().key().strip().toLowerCase(Locale.ROOT);
        if (!key.startsWith("then")) {
            // Every branch is parsed from the state before the chain, including its condition.
            delay = chain.before;
        }
        Statement statement = line(section, previous);
        if (key.equals("else")) {
            chain.hasElse = true;
        }
        // A multi-line condition's code is in the 'then' that follows, so its branch ends there.
        if (!(statement instanceof Statement.Conditions)) {
            chain.ends.add(delay);
        }
        return statement;
    }

    private Statement simple(Line line) {
        if (line.text().strip().equalsIgnoreCase(PARSE_SECTION + ":")) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        LineParser parser = new LineParser(context, line.text());
        int end = line.text().length();
        Expr.FunctionCall call = parser.call(0, end);
        if (call != null) {
            return new Statement.Call(line, call);
        }
        ParsedSyntax effect = parser.effect(0, end);
        if (effect != null) {
            check(line, effect);
            if (context.registry().delays(effect.syntax())) {
                delay = DelayState.DELAYED;
            }
            return new Statement.Effect(line, effect);
        }
        ParsedSyntax condition = parser.condition(0, end);
        if (condition != null) {
            check(line, condition);
            return new Statement.Condition(line, condition);
        }
        String problem = parser.functionProblem();
        error(line, problem != null ? problem : "Can't understand this condition/effect: " + line.text());
        return new Statement.Invalid(line);
    }

    private Statement section(Node.Section section, Statement previous) {
        Line line = section.line();
        String key = line.key();
        if (key.equalsIgnoreCase(PARSE_SECTION) && section.children().isEmpty()) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        if (key.equalsIgnoreCase(PARSE_SECTION)) {
            // Skript's parse section: its code counts towards the trigger (including any delay), but
            // its problems are captured for the test.
            int mark = diagnostics.mark();
            List<Statement> body = statements(section.children());
            List<Diagnostic> captured = diagnostics.takeSince(mark);
            return new Statement.Parse(line, body, captured);
        }
        LineParser parser = new LineParser(context, key);
        ParsedSyntax header = parser.section(0, key.length());
        if (header == null) {
            // An effect whose expression takes a section: 'set {_bar} to a new boss bar:'.
            ParsedSyntax effect = parser.effect(0, key.length());
            if (effect != null && hasSectionExpression(effect)) {
                return new Statement.Section(line, effect, repeatable(section.children()));
            }
            Expr.FunctionCall call = parser.call(0, key.length());
            if (call != null && call.arguments().stream().anyMatch(a -> a != null && hasSectionExpression(a))) {
                return new Statement.Section(line, null, repeatable(section.children()));
            }
            error(line, "Can't understand this section: " + key);
            return new Statement.Invalid(line);
        }
        check(line, header);
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("else") && !continuesConditional(previous)) {
            error(line, "'else' has to be placed just after another 'if' or 'else if' section");
            return new Statement.Invalid(line);
        }
        if (lower.startsWith("then") && !(previous instanceof Statement.Conditions)) {
            // Skript's wording, typo included.
            error(line, "'then' has to placed just after a multiline 'if' or 'else if' section");
            return new Statement.Invalid(line);
        }
        if (MULTILINE_CONDITIONAL.matcher(key).matches() && !header.syntax().patterns().get(header.pattern())
                .source().endsWith("<.+>")) {
            List<Line> lines = new ArrayList<>();
            List<ParsedSyntax> conditions = conditions(section.children(), lines);
            return new Statement.Conditions(line, header, conditions, lines);
        }
        if (PARSE_IF.matcher(key).matches()) {
            // 'parse if' only parses its code when the condition is true while loading, which can't be
            // known here: parse it, but keep its problems out of the report.
            int mark = diagnostics.mark();
            List<Statement> body = sectionBody(section.children());
            diagnostics.takeSince(mark);
            return new Statement.Section(line, header, body);
        }
        if (isConditional(lower)) {
            return new Statement.Section(line, header, sectionBody(section.children()));
        }
        return new Statement.Section(line, header, repeatable(section.children()));
    }

    /**
     * A section's code. If it always ends the trigger ({@code stop}, {@code return}), a delay in it doesn't
     * carry past the section, as Skript's execution intents have it.
     */
    private List<Statement> sectionBody(List<Node> nodes) {
        DelayState before = delay;
        List<Statement> body = statements(nodes);
        if (stops(body)) {
            delay = before;
        }
        return body;
    }

    private boolean stops(List<Statement> body) {
        for (Statement statement : body) {
            if (statement instanceof Statement.Effect effect
                    && context.registry().stops(effect.effect().syntax(), effect.effect().pattern())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Code in a section that may run any number of times, like a loop. Afterwards the trigger is delayed
     * only if it already was; if the code may wait, it's maybe delayed.
     */
    private List<Statement> repeatable(List<Node> nodes) {
        DelayState before = delay;
        List<Statement> body = sectionBody(nodes);
        delay = before.merge(List.of(before, delay));
        return body;
    }

    private List<ParsedSyntax> conditions(List<Node> nodes, List<Line> lines) {
        List<ParsedSyntax> conditions = new ArrayList<>();
        for (Node node : nodes) {
            Line line = node.line();
            delays.put(line, delay);
            LineParser parser = new LineParser(context, line.text());
            ParsedSyntax condition = node instanceof Node.Simple ? parser.condition(0, line.text().length()) : null;
            if (condition == null) {
                error(line, "Can't understand this condition: " + line.text());
            } else {
                check(line, condition);
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
            String key = section.line().key().toLowerCase(Locale.ROOT);
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
        Expr expr = new LineParser(context, text).expression(0, text.length(), expected);
        if (expr == null) {
            error(line, "Can't understand this expression: '" + text + "'");
        }
        return expr;
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), line.column(), line.text().length(), message);
    }
}
