/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import java.util.ArrayDeque;
import java.util.function.Predicate;
import org.bukkit.event.Event;

/**
 * An argument of an addon's element, as Skript gives it one: an expression. NeoSkript works the argument out before
 * the element runs; this gives the element that value, as the class it asked for.
 */
final class CompatArgument<T> implements Expression<T> {

    /** The arguments of the elements running on this thread, innermost last. */
    private static final ThreadLocal<ArrayDeque<Object[]>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private final int index;
    private final Class<T> type;
    private final boolean single;
    private final boolean and;
    private final boolean isDefault;
    private final String written;

    CompatArgument(int index, Class<T> type, boolean single, boolean and, boolean isDefault, String written) {
        this.index = index;
        this.type = type;
        this.single = single;
        this.and = and;
        this.isDefault = isDefault;
        this.written = written;
    }

    /** Runs {@code body} with {@code arguments} as the running element's. */
    static <R> R with(Object[] arguments, java.util.function.Supplier<R> body) {
        ArrayDeque<Object[]> frames = FRAMES.get();
        frames.push(arguments == null ? new Object[0] : arguments);
        try {
            return body.get();
        } finally {
            frames.pop();
            if (frames.isEmpty()) {
                FRAMES.remove();
            }
        }
    }

    private Object argument() {
        Object[] arguments = FRAMES.get().peek();
        if (arguments == null) {
            throw new IllegalStateException("An argument was read outside its element: " + this);
        }
        return index < arguments.length ? arguments[index] : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T[] getAll(Event event) {
        return (T[]) CompatValuesBridge.array(argument(), type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T[] getArray(Event event) {
        T[] all = getAll(event);
        if (and || all.length <= 1) {
            return all;
        }
        T[] one = (T[]) java.lang.reflect.Array.newInstance(all.getClass().getComponentType(), 1);
        one[0] = all[java.util.concurrent.ThreadLocalRandom.current().nextInt(all.length)];
        return one;
    }

    @Override
    public T getSingle(Event event) {
        T[] values = getArray(event);
        return values.length == 0 ? null : values[0];
    }

    @Override
    public boolean isSingle() {
        return single;
    }

    @Override
    public boolean check(Event event, Predicate<? super T> checker, boolean negated) {
        return SimpleExpression.check(getAll(event), checker, negated, and);
    }

    @Override
    public boolean check(Event event, Predicate<? super T> checker) {
        return check(event, checker, false);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <R> Expression<? extends R> getConvertedExpression(Class<R>... to) {
        for (Class<R> target : to) {
            if (target.isAssignableFrom(type)) {
                return (Expression<? extends R>) this;
            }
        }
        return to.length == 0 ? null : new CompatArgument<>(index, to[0], single, and, isDefault, written);
    }

    @Override
    public Class<? extends T> getReturnType() {
        return type;
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
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public Expression<?> getSource() {
        return this;
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return null;
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        throw new UnsupportedOperationException("NeoSkriptCompat can't change an addon's argument");
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        if (event != null && FRAMES.get().peek() != null) {
            return ch.njol.skript.registrations.Classes.toString(getAll(event), and);
        }
        return written != null ? written : type.getSimpleName().toLowerCase(java.util.Locale.ENGLISH);
    }

    @Override
    public String toString() {
        return toString(null, false);
    }
}
