package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;

/**
 * Seeds a default value for a variable declared in a {@code variables:} block — assigns it only if it
 * is currently unset, so persisted values are preserved across restarts.
 */
public final class DefaultVariableStatement implements Statement {

    private final VariableExpression variable;
    private final Expression<?> value;

    public DefaultVariableStatement(VariableExpression variable, Expression<?> value) {
        this.variable = variable;
        this.value = value;
    }

    @Override
    public void run(TriggerContext ctx) {
        if (variable.getSingle(ctx) == null) {
            variable.set(ctx, value.getSingle(ctx));
        }
    }
}
