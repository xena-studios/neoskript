package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.ScriptFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * A call to a user-defined function, e.g. {@code greet(player, 3)}. Resolves the target lazily
 * through the {@link FunctionRegistry} at runtime, so calls may precede definitions in source.
 */
public final class FunctionCallExpression implements Expression<Object> {

    private final String name;
    private final List<Expression<?>> arguments;
    private final FunctionRegistry registry;

    public FunctionCallExpression(String name, List<Expression<?>> arguments, FunctionRegistry registry) {
        this.name = name;
        this.arguments = List.copyOf(arguments);
        this.registry = registry;
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        ScriptFunction function = registry.get(name);
        if (function == null) {
            return null;
        }
        List<Object> values = new ArrayList<>(arguments.size());
        for (Expression<?> argument : arguments) {
            values.add(argument == null ? null : argument.getSingle(ctx));
        }
        return function.call(values, ctx);
    }

    @Override
    public Class<Object> returnType() {
        return Object.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
