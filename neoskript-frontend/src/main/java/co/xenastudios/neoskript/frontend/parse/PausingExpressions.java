/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * The pausing expressions in a statement: an addon's expressions that make the trigger wait for their value, which
 * the registry marks as delaying. A line with one counts as a delay, from the line itself on, as the rest of the
 * line runs after the wait.
 */
public final class PausingExpressions {

    private PausingExpressions() {}

    /**
     * The pausing expressions in {@code parsed} (a {@link ParsedSyntax}, an {@link Expr} or a list of them), in the
     * order they're worked out: those in another's arguments first, then left to right.
     */
    public static List<Expr.Syntax> in(Object parsed, SyntaxRegistry registry) {
        if (!registry.delayingExpressions()) {
            return List.of();
        }
        List<Expr.Syntax> found = new ArrayList<>();
        collect(parsed, registry, found);
        return found;
    }

    /** Whether {@code parsed} has a pausing expression in it. */
    public static boolean any(Object parsed, SyntaxRegistry registry) {
        return !in(parsed, registry).isEmpty();
    }

    private static void collect(Object value, SyntaxRegistry registry, List<Expr.Syntax> found) {
        switch (value) {
            case ParsedSyntax syntax -> {
                syntax.match().values().forEach(inner -> collect(inner, registry, found));
                syntax.match().regexValues().forEach(inner -> collect(inner, registry, found));
            }
            case Expr.Syntax syntax -> {
                collect(syntax.syntax(), registry, found);
                if (syntax.syntax().syntax().kind() == SyntaxKind.EXPRESSION && registry.delays(syntax.syntax().syntax())) {
                    found.add(syntax);
                }
            }
            case Expr.ListOf list -> list.items().forEach(item -> collect(item, registry, found));
            case Expr.FunctionCall call -> call.arguments().forEach(argument -> collect(argument, registry, found));
            case Expr.Text text -> text.embedded().forEach(embedded -> collect(embedded, registry, found));
            case Expr.Variable variable -> variable.embedded().forEach(embedded -> collect(embedded, registry, found));
            case List<?> list -> list.forEach(item -> collect(item, registry, found));
            case null, default -> {}
        }
    }
}
