package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.function.Function;

/**
 * A single-valued expression computed by a function of the context. Lets built-ins and addons define
 * simple expressions with a lambda instead of a full class.
 */
public final class ComputedExpression implements Expression<Object> {

    private final Function<TriggerContext, Object> compute;

    public ComputedExpression(Function<TriggerContext, Object> compute) {
        this.compute = compute;
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        return compute.apply(ctx);
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
