package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

/**
 * Reads a reserved local value by name, such as {@code loop-value} or {@code loop-index} set by
 * {@link co.xenastudios.neoskript.core.runtime.LoopSection}.
 */
public final class NamedLocalExpression implements Expression<Object> {

    private final String localName;

    public NamedLocalExpression(String localName) {
        this.localName = localName;
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        return ctx.getLocal(localName);
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
