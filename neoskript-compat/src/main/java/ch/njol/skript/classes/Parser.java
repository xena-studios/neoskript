/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.classes;

import ch.njol.skript.lang.ParseContext;

/** How a type's values are read from text and written as text. */
public abstract class Parser<T> {

    public Parser() {}

    /** Reads a value; a parser that can't returns null, as Skript's does. */
    public T parse(String s, ParseContext context) {
        return null;
    }

    public boolean canParse(final ParseContext context) {
        return true;
    }

    public abstract String toString(T o, int flags);

    public final String toString(final T o, final ch.njol.skript.util.StringMode mode) {
        return mode == ch.njol.skript.util.StringMode.VARIABLE_NAME ? toVariableNameString(o) : toString(o, 0);
    }

    public String toCommandString(final T o) {
        return toString(o, 0);
    }

    public abstract String toVariableNameString(final T o);

    public String getDebugMessage(final T o) {
        return toString(o, 0);
    }
}
