/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.stub;

import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.Statement;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts how often each syntax element and function is used in parsed scripts: the ranking M4
 * implements the standard library in. Keys are {@code kind id} (e.g. {@code EFFECT EffMessage}) and
 * {@code FUNCTION name}.
 */
public final class SyntaxUsage {

    private final Map<String, Integer> counts = new TreeMap<>();

    public static SyntaxUsage of(List<ParsedScript> scripts) {
        SyntaxUsage usage = new SyntaxUsage();
        for (ParsedScript script : scripts) {
            usage.structures(script.structures());
        }
        return usage;
    }

    /** Uses, most-used first. */
    public Map<String, Integer> ranked() {
        Map<String, Integer> ranked = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> ranked.put(entry.getKey(), entry.getValue()));
        return ranked;
    }

    private void count(String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private void structures(List<ParsedStructure> structures) {
        for (ParsedStructure structure : structures) {
            switch (structure) {
                case ParsedStructure.Event event -> {
                    syntax(event.event());
                    body(event.body());
                }
                case ParsedStructure.Command command -> {
                    count("STRUCTURE command");
                    body(command.trigger());
                }
                case ParsedStructure.Function function -> {
                    count("STRUCTURE function");
                    function.defaults().forEach(this::expr);
                    body(function.body());
                }
                case ParsedStructure.Custom custom -> {
                    syntax(custom.syntax());
                    body(custom.body());
                }
                case ParsedStructure.Parse parse -> structures(parse.code());
                case ParsedStructure.Data data -> {}
            }
        }
    }

    private void body(List<Statement> body) {
        for (Statement statement : body) {
            switch (statement) {
                case Statement.Effect effect -> syntax(effect.effect());
                case Statement.Condition condition -> syntax(condition.condition());
                case Statement.Call call -> expr(call.call());
                case Statement.Section section -> {
                    if (section.header() != null) {
                        syntax(section.header());
                    }
                    body(section.body());
                }
                case Statement.Conditions conditions -> {
                    if (conditions.header() != null) {
                        syntax(conditions.header());
                    }
                    conditions.conditions().forEach(this::syntax);
                }
                case Statement.Parse parse -> body(parse.body());
                case Statement.Invalid invalid -> {}
            }
        }
    }

    private void syntax(ParsedSyntax syntax) {
        if (syntax == null) {
            return;
        }
        count(syntax.syntax().kind() + " " + syntax.syntax().id());
        values(syntax.match().values());
        values(syntax.match().regexValues());
    }

    private void values(List<Object> values) {
        for (Object value : values) {
            if (value instanceof Expr expr) {
                expr(expr);
            } else if (value instanceof ParsedSyntax syntax) {
                syntax(syntax);
            }
        }
    }

    private void expr(Expr expr) {
        if (expr == null) {
            return;
        }
        switch (expr) {
            case Expr.Syntax syntax -> syntax(syntax.syntax());
            case Expr.FunctionCall call -> {
                count("FUNCTION " + call.function().name());
                call.arguments().forEach(this::expr);
            }
            case Expr.Text text -> text.embedded().forEach(this::expr);
            case Expr.Variable variable -> variable.embedded().forEach(this::expr);
            case Expr.ListOf list -> list.items().forEach(this::expr);
            default -> {}
        }
    }
}
