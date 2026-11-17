/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.util;

import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.event.Event;

/** The usual base of an addon's expression: it gives {@link #get(Event)}, this does the rest as Skript does. */
public abstract class SimpleExpression<T> implements Expression<T>,
        org.skriptlang.skript.log.runtime.SyntaxRuntimeErrorProducer {

    private int time = 0;

    protected SimpleExpression() {}

    @Override
    public boolean preInit() {
        return true;
    }

    protected abstract T[] get(Event event);

    @Override
    public final T getSingle(Event event) {
        T[] all = get(event);
        if (all == null || all.length == 0) {
            return null;
        }
        if (all.length > 1) {
            throw new SkriptAPIException("Call to getSingle() on a non-single expression");
        }
        return all[0];
    }

    @Override
    public T[] getAll(Event event) {
        return withoutNulls(get(event));
    }

    @Override
    public final T[] getArray(Event event) {
        T[] all = withoutNulls(get(event));
        if (getAnd() || all.length <= 1) {
            return all;
        }
        T[] one = newArray(all, 1);
        one[0] = all[java.util.concurrent.ThreadLocalRandom.current().nextInt(all.length)];
        return one;
    }

    @SuppressWarnings("unchecked")
    private T[] withoutNulls(T[] all) {
        if (all == null) {
            return (T[]) Array.newInstance(getReturnType(), 0);
        }
        int nulls = 0;
        for (T value : all) {
            if (value == null) {
                nulls++;
            }
        }
        if (nulls == 0) {
            return all;
        }
        T[] kept = newArray(all, all.length - nulls);
        int i = 0;
        for (T value : all) {
            if (value != null) {
                kept[i++] = value;
            }
        }
        return kept;
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] newArray(T[] like, int length) {
        return (T[]) Array.newInstance(like.getClass().getComponentType(), length);
    }

    @Override
    public final boolean check(Event event, Predicate<? super T> checker) {
        return check(event, checker, false);
    }

    @Override
    public final boolean check(Event event, Predicate<? super T> checker, boolean negated) {
        return check(get(event), checker, negated, getAnd());
    }

    public static <T> boolean check(T[] values, Predicate<? super T> checker, boolean invert, boolean and) {
        if (values == null) {
            return invert;
        }
        boolean any = false;
        for (T value : values) {
            if (value == null) {
                continue;
            }
            any = true;
            boolean result = checker.test(value);
            if (and && !result) {
                return invert;
            }
            if (!and && result) {
                return !invert;
            }
        }
        if (!any) {
            and = false;
        }
        return invert != and;
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <R> Expression<? extends R> getConvertedExpression(Class<R>... to) {
        for (Class<R> type : to) {
            if (type.isAssignableFrom(getReturnType())) {
                return (Expression<? extends R>) this;
            }
        }
        return null;
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return null;
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        throw new UnsupportedOperationException("Can't change " + this);
    }

    @Override
    public boolean setTime(int time) {
        return false;
    }

    protected final boolean setTime(int time, Class<? extends Event> applicableEvent) {
        if (getParser().isCurrentEvent(applicableEvent)) {
            this.time = time;
            return true;
        }
        return false;
    }

    @Override
    public int getTime() {
        return time;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean isLoopOf(String input) {
        return false;
    }

    @Override
    public Iterator<? extends T> iterator(Event event) {
        return Arrays.asList(getArray(event)).iterator();
    }

    @Override
    public String toString() {
        return toString(null, false);
    }

    @Override
    public Expression<?> getSource() {
        return this;
    }

    @Override
    public ch.njol.skript.config.Node getNode() {
        return null;
    }

    @Override
    public Expression<? extends T> simplify() {
        return this;
    }

    @Override
    public boolean getAnd() {
        return true;
    }

    public boolean supportsLoopPeeking() {
        return false;
    }

    static <T> List<T> list(T[] values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(values));
    }
}
