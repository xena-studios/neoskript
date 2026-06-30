package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * A literal list of expressions written with commas and {@code and}/{@code or}, e.g.
 * {@code 1, 2 and 3}. Evaluating it concatenates each element's values.
 */
public final class ListExpression implements Expression<Object> {

    private final List<Expression<?>> elements;

    public ListExpression(List<Expression<?>> elements) {
        this.elements = List.copyOf(elements);
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        List<Object> values = new ArrayList<>();
        for (Expression<?> element : elements) {
            for (Object value : element.getAll(ctx)) {
                values.add(value);
            }
        }
        return values.toArray();
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
