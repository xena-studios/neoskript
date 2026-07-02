package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

/**
 * A binary arithmetic expression ({@code +}, {@code -}, {@code *}, {@code /}, {@code ^}) over numeric
 * operands. Operands that are not numbers (and cannot be parsed as one) make the result {@code null};
 * division by zero yields {@code null}.
 */
public final class ArithmeticExpression implements Expression<Double> {

    private final Expression<?> left;
    private final char operator;
    private final Expression<?> right;

    public ArithmeticExpression(Expression<?> left, char operator, Expression<?> right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public Double[] getAll(TriggerContext ctx) {
        Double value = getSingle(ctx);
        return value == null ? new Double[0] : new Double[]{value};
    }

    @Override
    public Double getSingle(TriggerContext ctx) {
        Double a = toNumber(left.getSingle(ctx));
        Double b = toNumber(right.getSingle(ctx));
        if (a == null || b == null) {
            return null;
        }
        return switch (operator) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> b == 0 ? null : a / b;
            case '^' -> Math.pow(a, b);
            default -> null;
        };
    }

    @Override
    public Class<Double> returnType() {
        return Double.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    private static Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
