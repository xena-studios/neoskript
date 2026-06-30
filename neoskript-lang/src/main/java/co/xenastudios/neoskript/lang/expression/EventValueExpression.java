package co.xenastudios.neoskript.lang.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.lang.event.EventValueResolver;

/**
 * Exposes a value of a given type from the current event (e.g. {@code event-block},
 * {@code event-world}), resolved reflectively by {@link EventValueResolver}.
 *
 * @param <T> the value type
 */
public final class EventValueExpression<T> implements Expression<T> {

    private final Class<T> type;

    public EventValueExpression(Class<T> type) {
        this.type = type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T[] getAll(TriggerContext ctx) {
        T value = getSingle(ctx);
        T[] array = (T[]) java.lang.reflect.Array.newInstance(type, value == null ? 0 : 1);
        if (value != null) {
            array[0] = value;
        }
        return array;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSingle(TriggerContext ctx) {
        return (T) EventValueResolver.resolve(ctx.event().orElse(null), type);
    }

    @Override
    public Class<T> returnType() {
        return type;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
