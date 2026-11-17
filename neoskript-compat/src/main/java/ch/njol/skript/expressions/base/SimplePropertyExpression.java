/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.expressions.base;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.skriptlang.skript.lang.converter.Converter;

/** A property expression that converts each value on its own: {@link #convert}. */
public abstract class SimplePropertyExpression<F, T> extends PropertyExpression<F, T> implements Converter<F, T> {

    protected String rawExpr;

    public SimplePropertyExpression() {}

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        if (LiteralUtils.hasUnparsedLiteral(expressions[0])) {
            setExpr(LiteralUtils.defendExpression(expressions[0]));
            return LiteralUtils.canInitSafely(getExpr());
        }
        setExpr((Expression<? extends F>) expressions[0]);
        rawExpr = parseResult.expr;
        return true;
    }

    @Override
    public abstract T convert(F from);

    @Override
    protected T[] get(Event event, F[] source) {
        return super.get(source, this);
    }

    protected abstract String getPropertyName();

    @Override
    public String toString(Event event, boolean debug) {
        return getPropertyName() + " of " + getExpr().toString(event, debug);
    }
}
