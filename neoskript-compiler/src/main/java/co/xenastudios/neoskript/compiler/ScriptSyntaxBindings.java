/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.ScriptSyntaxContext;
import java.util.Map;

/**
 * What syntax a script declared is bound to while its load compiles: an element like an addon's, whose every use
 * calls the declaring structure's code, compiled as a hidden function, with the use's arguments, pattern and mark.
 */
final class ScriptSyntaxBindings {

    private ScriptSyntaxBindings() {}

    static Object factory(SyntaxInfo syntax, Map<FunctionInfo, FunctionHandle> functions) {
        FunctionHandle code = functions.get(syntax.script().body());
        if (syntax.script().declared().property()) {
            return property(syntax, code);
        }
        return switch (syntax.kind()) {
            case EFFECT -> (ElementFactory<Effect>) info -> (context, arguments) -> {
                if (code != null) {
                    ScriptSyntaxContext.run(code, context, arguments, info.pattern(), info.mark(), info.tags());
                }
            };
            case CONDITION -> (ElementFactory<Condition>) info -> (context, arguments) -> code != null
                    && ScriptSyntaxContext.check(code, context, arguments, info.pattern(), info.mark(), info.tags());
            case EXPRESSION -> (ElementFactory<Expression>) info -> (context, arguments) -> code == null ? null
                    : ScriptSyntaxContext.get(code, context, arguments, info.pattern(), info.mark(), info.tags());
            default -> null;
        };
    }

    /**
     * A property's: the code runs for each of the first argument's values, given alone, as Skript's property
     * expressions and conditions get each owner's.
     */
    private static Object property(SyntaxInfo syntax, FunctionHandle code) {
        return switch (syntax.kind()) {
            case CONDITION -> (ElementFactory<Condition>) info -> (context, arguments) -> {
                if (code == null) {
                    return false;
                }
                boolean any = !info.and(0);
                boolean passed = !any;
                for (Object owner : owners(arguments)) {
                    Object[] alone = arguments.clone();
                    alone[0] = owner;
                    boolean one = ScriptSyntaxContext.check(code, context, alone, info.pattern(), info.mark(),
                            info.tags());
                    passed = any ? passed || one : passed && one;
                }
                // The second pattern is the negated one ('is not').
                return info.pattern() == 1 ? !passed : passed;
            };
            case EXPRESSION -> (ElementFactory<Expression>) info -> (context, arguments) -> {
                if (code == null) {
                    return null;
                }
                java.util.List<Object> values = new java.util.ArrayList<>();
                for (Object owner : owners(arguments)) {
                    Object[] alone = arguments.clone();
                    alone[0] = owner;
                    Object value = ScriptSyntaxContext.get(code, context, alone, info.pattern(), info.mark(),
                            info.tags());
                    if (value instanceof Object[] several) {
                        java.util.Collections.addAll(values, several);
                    } else if (value != null) {
                        values.add(value);
                    }
                }
                return values.toArray();
            };
            default -> null;
        };
    }

    private static Object[] owners(Object[] arguments) {
        Object first = arguments.length == 0 ? null : arguments[0];
        return first instanceof Object[] several ? several : first == null ? new Object[0] : new Object[] {first};
    }
}
