/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.simplification.Simplifiable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.bukkit.event.Event;

/** An expression: something that gives values. */
public interface Expression<T> extends SyntaxElement, Debuggable, Loopable<T>, Simplifiable<Expression<? extends T>> {

    T getSingle(Event event);

    default Optional<T> getOptionalSingle(Event event) {
        return Optional.ofNullable(getSingle(event));
    }

    T[] getArray(Event event);

    T[] getAll(Event event);

    default Stream<? extends T> stream(Event event) {
        return Arrays.stream(getArray(event));
    }

    default Stream<? extends T> streamAll(Event event) {
        return Arrays.stream(getAll(event));
    }

    boolean isSingle();

    default boolean canBeSingle() {
        return isSingle();
    }

    boolean check(Event event, Predicate<? super T> checker, boolean negated);

    boolean check(Event event, Predicate<? super T> checker);

    @SuppressWarnings("unchecked")
    <R> Expression<? extends R> getConvertedExpression(Class<R>... to);

    Class<? extends T> getReturnType();

    @SuppressWarnings("unchecked")
    default Class<? extends T>[] possibleReturnTypes() {
        return new Class[] {getReturnType()};
    }

    default boolean canReturn(Class<?> returnType) {
        for (Class<?> type : possibleReturnTypes()) {
            if (returnType.isAssignableFrom(type) || type.isAssignableFrom(returnType)) {
                return true;
            }
        }
        return false;
    }

    default boolean canReturnAnyOf(Class<?>... returnTypes) {
        for (Class<?> type : returnTypes) {
            if (canReturn(type)) {
                return true;
            }
        }
        return false;
    }

    boolean getAnd();

    boolean setTime(int time);

    int getTime();

    default boolean returnNestedStructures(boolean nested) {
        return false;
    }

    default boolean returnsNestedStructures() {
        return false;
    }

    boolean isDefault();

    Expression<?> getSource();

    @Override
    default Expression<? extends T> simplify() {
        return this;
    }

    Class<?>[] acceptChange(ChangeMode mode);

    default Map<ChangeMode, Class<?>[]> getAcceptedChangeModes() {
        Map<ChangeMode, Class<?>[]> map = new java.util.EnumMap<>(ChangeMode.class);
        for (ChangeMode mode : ChangeMode.values()) {
            Class<?>[] valid = acceptChange(mode);
            if (valid != null) {
                map.put(mode, valid);
            }
        }
        return map;
    }

    void change(Event event, Object[] delta, ChangeMode mode);

    default <R> void changeInPlace(Event event, java.util.function.Function<T, R> changeFunction) {
        changeInPlace(event, changeFunction, false);
    }

    default <R> void changeInPlace(Event event, java.util.function.Function<T, R> changeFunction, boolean getAll) {
        T[] values = getAll ? getAll(event) : getArray(event);
        java.util.List<R> changed = new java.util.ArrayList<>();
        for (T value : values) {
            R result = changeFunction.apply(value);
            if (result != null) {
                changed.add(result);
            }
        }
        change(event, changed.toArray(), ChangeMode.SET);
    }

    default Object[] beforeChange(Expression<?> changed, Object[] delta) {
        return delta;
    }

    @Override
    default String getSyntaxTypeName() {
        return "expression";
    }

    @Override
    default Iterator<? extends T> iterator(Event event) {
        return Arrays.asList(getArray(event)).iterator();
    }

    @Override
    default boolean isLoopOf(String input) {
        return false;
    }
}
