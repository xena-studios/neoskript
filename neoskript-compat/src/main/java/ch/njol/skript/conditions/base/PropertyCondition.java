/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.conditions.base;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import java.util.function.Predicate;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.util.Priority;

/** A condition on a property of some values: {@code %things% (is|are) x}. */
public abstract class PropertyCondition<T> extends Condition implements Predicate<T> {

    public static final Priority DEFAULT_PRIORITY = Priority.before(SyntaxInfo.PATTERN_MATCHES_EVERYTHING);

    private Expression<? extends T> expr;

    /** How the property reads: is, can, has or will. */
    public enum PropertyType {
        BE, CAN, HAVE, WILL
    }

    public PropertyCondition() {}

    public static <E extends Condition> SyntaxInfo.Builder<? extends SyntaxInfo.Builder<?, E>, E> infoBuilder(
            Class<E> condition, PropertyType propertyType, String property, String type) {
        return SyntaxInfo.builder(condition).priority(DEFAULT_PRIORITY)
                .addPatterns(getPatterns(propertyType, property, type));
    }

    public static String[] getPatterns(PropertyType propertyType, String property, String type) {
        return switch (propertyType) {
            case BE -> new String[] {"%" + type + "% (is|are) " + property,
                    "%" + type + "% (isn't|is not|aren't|are not) " + property};
            case CAN -> new String[] {"%" + type + "% can " + property,
                    "%" + type + "% (can't|cannot|can not) " + property};
            case HAVE -> new String[] {"%" + type + "% (has|have) " + property,
                    "%" + type + "% (doesn't|does not|do not|don't) have " + property};
            case WILL -> new String[] {"%" + type + "% will " + property,
                    "%" + type + "% (will (not|neither)|won't) " + property};
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        expr = (Expression<? extends T>) expressions[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public final boolean check(Event event) {
        return expr.check(event, this, isNegated());
    }

    public abstract boolean check(T value);

    @Override
    public final boolean test(T value) {
        return check(value);
    }

    public void change(Event event, boolean value, ChangeMode mode) {}

    protected void change(T value, boolean newValue, ChangeMode mode) {}

    protected abstract String getPropertyName();

    protected PropertyType getPropertyType() {
        return PropertyType.BE;
    }

    public final Expression<? extends T> getExpr() {
        return expr;
    }

    protected final void setExpr(Expression<? extends T> expr) {
        this.expr = expr;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return toString(this, getPropertyType(), event, debug, expr, getPropertyName());
    }

    public static String toString(Condition condition, PropertyType propertyType, Event event, boolean debug,
            Expression<?> expr, String property) {
        String owner = expr.toString(event, debug);
        boolean single = expr.isSingle();
        boolean negated = condition.isNegated();
        return switch (propertyType) {
            case BE -> owner + (single ? " is " : " are ") + (negated ? "not " : "") + property;
            case CAN -> owner + (negated ? " can't " : " can ") + property;
            case HAVE -> owner + (negated ? (single ? " doesn't have " : " don't have ") : (single ? " has " : " have "))
                    + property;
            case WILL -> owner + (negated ? " won't " : " will ") + property;
        };
    }
}
