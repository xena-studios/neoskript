package co.xenastudios.neoskript.lang.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.ChangeMode;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * A numeric property of some holder (player/entity) that is both gettable and changeable. The holder
 * is produced by a target sub-expression (the {@code %...%} slot); {@code set}/{@code add}/
 * {@code remove}/{@code reset} dispatch here via the generic changer effect.
 *
 * @param <H> the holder type (e.g. {@code Player}, {@code LivingEntity}, {@code Entity})
 */
public final class NumericPropertyExpression<H> implements Expression<Object> {

    private final Expression<?> target;
    private final Class<H> holderClass;
    private final ToDoubleFunction<H> getter;
    private final ObjDoubleConsumer<H> setter;
    private final double resetValue;

    public NumericPropertyExpression(Expression<?> target, Class<H> holderClass,
                                     ToDoubleFunction<H> getter, ObjDoubleConsumer<H> setter, double resetValue) {
        this.target = target;
        this.holderClass = holderClass;
        this.getter = getter;
        this.setter = setter;
        this.resetValue = resetValue;
    }

    private H holder(TriggerContext ctx) {
        Object value = target.getSingle(ctx);
        return holderClass.isInstance(value) ? holderClass.cast(value) : null;
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        H holder = holder(ctx);
        return holder == null ? null : getter.applyAsDouble(holder);
    }

    @Override
    public Class<Object> returnType() {
        return Object.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE -> new Class<?>[]{Number.class};
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(TriggerContext ctx, Object[] delta, ChangeMode mode) {
        H holder = holder(ctx);
        if (holder == null) {
            return;
        }
        double amount = (delta != null && delta.length > 0 && delta[0] instanceof Number n) ? n.doubleValue() : 0;
        double current = getter.applyAsDouble(holder);
        switch (mode) {
            case SET -> setter.accept(holder, amount);
            case ADD -> setter.accept(holder, current + amount);
            case REMOVE -> setter.accept(holder, current - amount);
            case DELETE, RESET -> setter.accept(holder, resetValue);
            default -> {
            }
        }
    }
}
