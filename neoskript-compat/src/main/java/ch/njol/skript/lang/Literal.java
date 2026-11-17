/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

/** An expression written as a value: its values are known before the script runs. */
public interface Literal<T> extends Expression<T> {

    T[] getArray();

    T getSingle();

    @Override
    @SuppressWarnings("unchecked")
    <R> Literal<? extends R> getConvertedExpression(Class<R>... to);

    T[] getAll();
}
