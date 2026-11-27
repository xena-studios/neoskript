/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.util;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.DefaultExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.Kleenean;
import java.lang.reflect.Array;
import java.util.function.Predicate;
import org.bukkit.event.Event;

/** Values known before the script runs, as a literal: also a type's default value. */
public class SimpleLiteral<T> implements Literal<T>, DefaultExpression<T> {

    protected final Class<T> type;
    protected final Expression<?> source;
    protected transient T[] data;
    private final boolean isDefault;
    private final boolean and;

    public SimpleLiteral(T[] data, Class<T> type, boolean and) {
        this(data, type, and, false, null);
    }

    public SimpleLiteral(T[] data, Class<T> type, boolean and, Expression<?> source) {
        this(data, type, and, false, source);
    }

    public SimpleLiteral(T[] data, Class<T> type, boolean and, boolean isDefault, Expression<?> source) {
        this.data = data;
        this.type = type;
        this.and = data.length == 1 || and;
        this.isDefault = isDefault;
        this.source = source;
    }

    public SimpleLiteral(T data, boolean isDefault) {
        this(data, isDefault, null);
    }

    @SuppressWarnings("unchecked")
    public SimpleLiteral(T data, boolean isDefault, Expression<?> source) {
        this.data = (T[]) Array.newInstance(data.getClass(), 1);
        this.data[0] = data;
        this.type = (Class<T>) data.getClass();
        this.and = true;
        this.isDefault = isDefault;
        this.source = source;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public T[] getArray() {
        return data;
    }

    @Override
    public T[] getArray(Event event) {
        return data;
    }

    @Override
    public T[] getAll() {
        return data;
    }

    @Override
    public T[] getAll(Event event) {
        return data;
    }

    @Override
    public T getSingle() {
        return data.length == 0 ? null : data[0];
    }

    @Override
    public T getSingle(Event event) {
        return getSingle();
    }

    @Override
    public Class<T> getReturnType() {
        return type;
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <R> Literal<? extends R> getConvertedExpression(Class<R>... to) {
        for (Class<R> target : to) {
            if (target.isAssignableFrom(type)) {
                return (Literal<? extends R>) this;
            }
        }
        return null;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return Classes.toString(data, and);
    }

    @Override
    public String toString() {
        return toString(null, false);
    }

    @Override
    public boolean isSingle() {
        return !and || data.length <= 1;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean check(Event event, Predicate<? super T> checker, boolean negated) {
        return SimpleExpression.check(data, checker, negated, and);
    }

    @Override
    public boolean check(Event event, Predicate<? super T> checker) {
        return check(event, checker, false);
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return null;
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getAnd() {
        return and;
    }

    @Override
    public boolean setTime(int time) {
        return false;
    }

    @Override
    public int getTime() {
        return 0;
    }

    @Override
    public boolean isLoopOf(String input) {
        return false;
    }

    @Override
    public Expression<?> getSource() {
        return source == null ? this : source;
    }

    @Override
    public Expression<? extends T> simplify() {
        return this;
    }
}
