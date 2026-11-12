/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.List;
import java.util.Locale;

/**
 * Where each syntax element and function call is in a parsed script, for an editor's hover: one JSON object per
 * element, with its 1-based line, its 0-based start and end columns on that line, and what it is. A line's own
 * element (an effect, a condition, an event) covers the line; the expressions in it cover their own text.
 */
final class Elements {

    private final StringBuilder out = new StringBuilder();
    private boolean first = true;

    /** The elements of {@code script}, as a JSON array. */
    static String of(ParsedScript script) {
        Elements elements = new Elements();
        for (ParsedStructure structure : script.structures()) {
            elements.structure(structure);
        }
        return "[" + elements.out + "]";
    }

    private void structure(ParsedStructure structure) {
        switch (structure) {
            case ParsedStructure.Event event -> {
                root(event.structure().node().line(), event.event());
                statements(event.body());
            }
            case ParsedStructure.Command command -> statements(command.statements());
            case ParsedStructure.Function function -> statements(function.body());
            case ParsedStructure.Custom custom -> {
                if (custom.syntax() != null) {
                    root(custom.structure().node().line(), custom.syntax());
                }
                statements(custom.body());
            }
            case ParsedStructure.Parse parse -> parse.code().forEach(this::structure);
            case ParsedStructure.AutoReload autoReload -> {}
            case ParsedStructure.Data data -> {}
        }
    }

    private void statements(List<Statement> statements) {
        if (statements == null) {
            return;
        }
        for (Statement statement : statements) {
            switch (statement) {
                case Statement.Effect effect -> root(effect.line(), effect.effect());
                case Statement.Condition condition -> root(condition.line(), condition.condition());
                case Statement.Call call -> expression(call.line(), call.call());
                case Statement.Section section -> {
                    if (section.header() != null) {
                        root(section.line(), section.header());
                    }
                    if (section.call() != null) {
                        expression(section.line(), section.call());
                    }
                    statements(section.body());
                }
                case Statement.Conditions conditions -> {
                    if (conditions.header() != null) {
                        root(conditions.line(), conditions.header());
                    }
                    for (int i = 0; i < conditions.conditions().size() && i < conditions.conditionLines().size(); i++) {
                        root(conditions.conditionLines().get(i), conditions.conditions().get(i));
                    }
                }
                case Statement.Parse parse -> statements(parse.body());
                case Statement.Invalid invalid -> {}
            }
        }
    }

    /** A line's own element, over the whole line, and the expressions in it. */
    private void root(Line line, ParsedSyntax syntax) {
        syntax(line, line.column(), line.column() + line.key().length(), syntax);
        values(line, syntax);
    }

    private void values(Line line, ParsedSyntax syntax) {
        for (Object value : syntax.match().values()) {
            expression(line, value);
        }
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                values(line, inner);
            }
        }
    }

    private void expression(Line line, Object value) {
        int column = line.column();
        switch (value) {
            case Expr.Syntax syntax -> {
                syntax(line, column + syntax.start(), column + syntax.end(), syntax.syntax());
                values(line, syntax.syntax());
            }
            case Expr.FunctionCall call -> {
                function(line, column + call.start(), column + call.end(), call.function());
                call.arguments().forEach(argument -> expression(line, argument));
            }
            case Expr.ListOf list -> list.items().forEach(item -> expression(line, item));
            case Expr.Text text -> text.embedded().forEach(embedded -> expression(line, embedded));
            case Expr.Variable variable -> variable.embedded().forEach(embedded -> expression(line, embedded));
            case null, default -> {}
        }
    }

    private void syntax(Line line, int start, int end, ParsedSyntax syntax) {
        open(line, start, end);
        out.append(",\"kind\":").append(Json.quote(syntax.syntax().kind().name().toLowerCase(Locale.ROOT)))
                .append(",\"id\":").append(Json.quote(syntax.syntax().id()))
                .append('}');
    }

    private void function(Line line, int start, int end, FunctionInfo function) {
        open(line, start, end);
        out.append(",\"kind\":\"function\",\"name\":").append(Json.quote(function.name()))
                .append(",\"signature\":").append(Json.quote(signature(function)))
                .append('}');
    }

    private void open(Line line, int start, int end) {
        if (!first) {
            out.append(',');
        }
        first = false;
        out.append("{\"line\":").append(line.number())
                .append(",\"start\":").append(start)
                .append(",\"end\":").append(end);
    }

    /** A function as it's declared: {@code give(p: player, items: items) :: boolean}. */
    static String signature(FunctionInfo function) {
        StringBuilder text = new StringBuilder(function.name()).append('(');
        for (int i = 0; i < function.parameters().size(); i++) {
            FunctionInfo.Parameter parameter = function.parameters().get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(parameter.name()).append(": ").append(parameter.type());
            if (parameter.plural()) {
                text.append('s');
            }
            if (parameter.optional()) {
                text.append(" = …");
            }
        }
        text.append(')');
        if (function.returnType() != null) {
            text.append(" :: ").append(function.returnType()).append(function.returnsPlural() ? "s" : "");
        }
        return text.toString();
    }
}
