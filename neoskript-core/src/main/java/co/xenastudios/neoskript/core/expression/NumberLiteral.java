package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

/**
 * A constant numeric expression, e.g. {@code 42} or {@code 3.5}. Values are represented as
 * {@link Double}, matching NeoSkript's {@code number} type.
 */
public final class NumberLiteral implements Expression<Double> {

    private final double value;

    public NumberLiteral(double value) {
        this.value = value;
    }

    @Override
    public Double[] getAll(TriggerContext ctx) {
        return new Double[]{value};
    }

    @Override
    public Double getSingle(TriggerContext ctx) {
        return value;
    }

    @Override
    public Class<Double> returnType() {
        return Double.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
