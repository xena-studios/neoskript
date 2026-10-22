/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parses the lines of a trigger, function or other code section into statements. */
final class StatementParser {

    /** Skript's test-only section whose problems are captured for the test to check. */
    static final String PARSE_SECTION = "parse";

    /** {@code if any:}, {@code else if all:} and the like, whose lines are conditions. */
    private static final Pattern MULTILINE_CONDITIONAL =
            Pattern.compile("(else )?(parse )?if( (any|at least one( of)?|all))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARSE_IF = Pattern.compile("(else )?parse if .*", Pattern.CASE_INSENSITIVE);

    private final ParserContext context;
    private final Diagnostics diagnostics;

    StatementParser(ParserContext context, Diagnostics diagnostics) {
        this.context = context;
        this.diagnostics = diagnostics;
    }

    List<Statement> body(List<Node> nodes) {
        List<Statement> statements = new ArrayList<>(nodes.size());
        Statement previous = null;
        for (Node node : nodes) {
            Statement statement = switch (node) {
                case Node.Simple simple -> simple(simple.line());
                case Node.Section section -> section(section, previous);
            };
            statements.add(statement);
            previous = statement;
        }
        return statements;
    }

    private Statement simple(Line line) {
        LineParser parser = new LineParser(context, line.text());
        int end = line.text().length();
        Expr.FunctionCall call = parser.call(0, end);
        if (call != null) {
            return new Statement.Call(line, call);
        }
        ParsedSyntax effect = parser.effect(0, end);
        if (effect != null) {
            return new Statement.Effect(line, effect);
        }
        ParsedSyntax condition = parser.condition(0, end);
        if (condition != null) {
            return new Statement.Condition(line, condition);
        }
        error(line, "Can't understand this condition/effect: " + line.text());
        return new Statement.Invalid(line);
    }

    private Statement section(Node.Section section, Statement previous) {
        Line line = section.line();
        String key = line.key();
        if (key.equalsIgnoreCase(PARSE_SECTION)) {
            int mark = diagnostics.mark();
            List<Statement> body = body(section.children());
            List<Diagnostic> captured = diagnostics.takeSince(mark);
            return new Statement.Parse(line, body, captured);
        }
        LineParser parser = new LineParser(context, key);
        ParsedSyntax header = parser.section(0, key.length());
        if (header == null) {
            // An effect whose expression takes a section: 'set {_bar} to a new boss bar:'.
            ParsedSyntax effect = parser.effect(0, key.length());
            if (effect != null && hasSectionExpression(effect)) {
                return new Statement.Section(line, effect, body(section.children()));
            }
            Expr.FunctionCall call = parser.call(0, key.length());
            if (call != null && call.arguments().stream().anyMatch(StatementParser::hasSectionExpression)) {
                return new Statement.Section(line, null, body(section.children()));
            }
            error(line, "Can't understand this section: " + key);
            return new Statement.Invalid(line);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("else") && !continuesConditional(previous)) {
            error(line, "'else' has to be placed just after another 'if' or 'else if' section");
            return new Statement.Invalid(line);
        }
        if (MULTILINE_CONDITIONAL.matcher(key).matches() && !header.syntax().patterns().get(header.pattern())
                .source().endsWith("<.+>")) {
            return new Statement.Conditions(line, header, conditions(section.children()));
        }
        if (PARSE_IF.matcher(key).matches()) {
            // 'parse if' only parses its code when the condition is true while loading, which can't be
            // known here: parse it, but keep its problems out of the report.
            int mark = diagnostics.mark();
            List<Statement> body = body(section.children());
            diagnostics.takeSince(mark);
            return new Statement.Section(line, header, body);
        }
        return new Statement.Section(line, header, body(section.children()));
    }

    private List<ParsedSyntax> conditions(List<Node> nodes) {
        List<ParsedSyntax> conditions = new ArrayList<>();
        for (Node node : nodes) {
            Line line = node.line();
            LineParser parser = new LineParser(context, line.text());
            ParsedSyntax condition = node instanceof Node.Simple ? parser.condition(0, line.text().length()) : null;
            if (condition == null) {
                error(line, "Can't understand this condition: " + line.text());
            } else {
                conditions.add(condition);
            }
        }
        return conditions;
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
        return section.header().syntax().patterns().get(section.header().pattern()).source().startsWith("implicit:");
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), line.column(), line.text().length(), message);
    }
}
