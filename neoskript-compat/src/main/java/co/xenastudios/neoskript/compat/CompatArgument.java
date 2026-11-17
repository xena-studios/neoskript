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
class CompatArgument<T> implements Expression<T> {

    /** The arguments of the elements running on this thread, innermost last. */
    private static final ThreadLocal<ArrayDeque<Object[]>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    final int index;
    final Class<T> type;
    final boolean single;
    final boolean and;
    final boolean isDefault;
    final String written;

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

    /** Whether an element is running on this thread, so its arguments have values. */
    static boolean running() {
        ArrayDeque<Object[]> frames = FRAMES.get();
        if (frames.isEmpty()) {
            FRAMES.remove();
            return false;
        }
        return true;
    }

    Object argument() {
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
    @SuppressWarnings({"unchecked", "varargs"})
    public <R> Expression<? extends R> getConvertedExpression(Class<R>... to) {
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
        if (event != null && running()) {
            return ch.njol.skript.registrations.Classes.toString(getAll(event), and);
        }
        return written != null ? written : type.getSimpleName().toLowerCase(java.util.Locale.ENGLISH);
    }

    @Override
    public String toString() {
        return toString(null, false);
    }

    /**
     * An argument written as one value ({@code 5}, {@code "text"}, {@code zombie}), which Skript gives an addon as a
     * literal: its value is known when the line is set up, read from what's written as the class the addon asks for.
     */
    static final class Written<T> extends CompatArgument<T> implements ch.njol.skript.lang.Literal<T> {

        private final T[] value;

        @SuppressWarnings("unchecked")
        Written(int index, Class<T> type, boolean single, boolean and, boolean isDefault, String written) {
            super(index, type, single, and, isDefault, written);
            Object parsed;
            if (written == null) {
                // No one written value (a list, in an event): its values are the trigger's, when it's checked.
                parsed = null;
            } else try {
                parsed = Compat.get().types().parse(written, CompatValuesBridge.boxed(type),
                        ch.njol.skript.lang.ParseContext.DEFAULT);
                if (parsed == null) {
                    parsed = CompatValuesBridge.as(written, type);
                }
            } catch (RuntimeException unparsed) {
                // An addon's parser that throws: the literal has no value, as one that doesn't parse.
                parsed = null;
            }
            value = (T[]) java.lang.reflect.Array.newInstance(CompatValuesBridge.boxed(type), parsed == null ? 0 : 1);
            if (parsed != null) {
                value[0] = (T) parsed;
            }
        }

        /** The values: the running element's, while it runs, else those written. */
        @SuppressWarnings("unchecked")
        private T[] values() {
            return running() ? (T[]) CompatValuesBridge.array(argument(), type) : value.clone();
        }

        @Override
        public T[] getArray() {
            return values();
        }

        @Override
        public T getSingle() {
            T[] values = values();
            return values.length == 0 ? null : values[0];
        }

        @Override
        public T[] getAll() {
            return values();
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("unchecked")
        public final <R> ch.njol.skript.lang.Literal<? extends R> getConvertedExpression(Class<R>... to) {
            for (Class<R> target : to) {
                if (target.isAssignableFrom(type)) {
                    return (ch.njol.skript.lang.Literal<? extends R>) this;
                }
            }
            return to.length == 0 ? null : new Written<>(index, to[0], single, and, isDefault, written);
        }
    }
}
