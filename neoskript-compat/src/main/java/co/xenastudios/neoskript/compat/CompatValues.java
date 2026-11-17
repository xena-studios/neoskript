/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.ClassInfo;
import co.xenastudios.neoskript.api.NeoSkript;

/** Values as text and copies, as {@code Classes.toString} and {@code Classes.clone} give them. */
public final class CompatValues {

    CompatValues() {}

    public String text(Object value) {
        if (value == null) {
            return "<none>";
        }
        ClassInfo<?> info = Compat.get().types().byExactClass(value.getClass());
        if (info != null && !info.isNeoSkript() && info.getParser() != null) {
            return textOf(info, value);
        }
        return NeoSkript.get().values().text(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> String textOf(ClassInfo<T> info, Object value) {
        return ((ch.njol.skript.classes.Parser<T>) info.getParser()).toString((T) value, 0);
    }

    @SuppressWarnings("unchecked")
    public Object copy(Object value) {
        if (value == null) {
            return null;
        }
        ClassInfo<Object> info = (ClassInfo<Object>) Compat.get().types().byExactClass(value.getClass());
        return info != null && info.getCloner() != null ? info.clone(value) : value;
    }
}
