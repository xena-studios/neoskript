/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.entry.util;

import ch.njol.skript.lang.Expression;
import org.skriptlang.skript.lang.entry.KeyValueEntryData;

/** An entry whose value is an expression. NeoSkriptCompat doesn't read sections' entries yet. */
public class ExpressionEntryData<T> extends KeyValueEntryData<Expression<? extends T>> {

    private final Class<?>[] returnTypes;

    public ExpressionEntryData(String key, Expression<T> defaultValue, boolean optional, Class<T> returnType) {
        this(key, defaultValue, optional, 0, returnType);
    }

    public ExpressionEntryData(String key, Expression<T> defaultValue, boolean optional, Class<T> returnType,
            int flags) {
        this(key, defaultValue, optional, flags, returnType);
    }

    @SafeVarargs
    public ExpressionEntryData(String key, Expression<T> defaultValue, boolean optional,
            Class<? extends T>... returnTypes) {
        this(key, defaultValue, optional, 0, returnTypes);
    }

    @SafeVarargs
    public ExpressionEntryData(String key, Expression<T> defaultValue, boolean optional, int flags,
            Class<? extends T>... returnTypes) {
        super(key, defaultValue, optional);
        this.returnTypes = returnTypes.clone();
    }

    @Override
    protected Expression<? extends T> getValue(String value) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't read sections' entries yet");
    }
}
