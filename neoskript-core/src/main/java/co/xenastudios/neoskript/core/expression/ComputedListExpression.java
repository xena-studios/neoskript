package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.function.Function;

/**
 * A multi-valued expression computed by a function of the context (e.g. {@code split}, {@code sorted}).
 * Lets built-ins and addons define list-producing expressions with a lambda.
 */
public final class ComputedListExpression implements Expression<Object> {

    private final Function<TriggerContext, Object[]> compute;

    public ComputedListExpression(Function<TriggerContext, Object[]> compute) {
        this.compute = compute;
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object[] values = compute.apply(ctx);
        return values == null ? new Object[0] : values;
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        Object[] all = getAll(ctx);
        return all.length > 0 ? all[0] : null;
    }

    @Override
    public Class<Object> returnType() {
        return Object.class;
    }

    @Override
    public boolean isSingle() {
        return false;
    }
}
