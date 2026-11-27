/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.expressions.base;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.util.SimpleExpression;
import org.bukkit.event.Event;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.util.Priority;

/** An expression for a property of some values: {@code the x of %things%}. */
public abstract class PropertyExpression<F, T> extends SimpleExpression<T> {

    public static final Priority DEFAULT_PRIORITY = Priority.before(SyntaxInfo.PATTERN_MATCHES_EVERYTHING);

    private Expression<? extends F> expr;

    public PropertyExpression() {}

    public static String[] getPatterns(String property, String fromType) {
        return new String[] {"[the] " + property + " of %" + fromType + "%", "%" + fromType + "%'[s] " + property};
    }

    public static String[] getDefaultPatterns(String property, String fromType) {
        return new String[] {"[the] " + property + " [of %" + fromType + "%]", "%" + fromType + "%'[s] " + property};
    }

    public static <E extends Expression<T>, T> DefaultSyntaxInfos.Expression.Builder<? extends DefaultSyntaxInfos.Expression.Builder<?, E, T>, E, T>
            infoBuilder(Class<E> expressionClass, Class<T> returnType, String property, String fromType, boolean isDefault) {
        return DefaultSyntaxInfos.Expression.builder(expressionClass, returnType)
                .priority(DEFAULT_PRIORITY)
                .addPatterns(isDefault ? getDefaultPatterns(property, fromType) : getPatterns(property, fromType));
    }

    protected final void setExpr(Expression<? extends F> expr) {
        this.expr = expr;
    }

    public final Expression<? extends F> getExpr() {
        return expr;
    }

    @Override
    protected final T[] get(Event event) {
        return get(event, expr.getArray(event));
    }

    @Override
    public final T[] getAll(Event event) {
        return get(event, expr.getAll(event));
    }

    protected abstract T[] get(Event event, F[] source);

    protected T[] get(F[] source, Converter<? super F, ? extends T> converter) {
        return Converters.convertUnsafe(source, getReturnType(), converter);
    }

    @Override
    public boolean isSingle() {
        return expr.isSingle();
    }

    @Override
    public final boolean getAnd() {
        return expr.getAnd();
    }

    @Override
    public Expression<? extends T> simplify() {
        return this;
    }
}
