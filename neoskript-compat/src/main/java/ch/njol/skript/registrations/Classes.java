/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.registrations;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import co.xenastudios.neoskript.compat.Compat;
import java.util.List;

/**
 * Skript's types, by code name and by class: NeoSkript's own, and those addons register through NeoSkriptCompat.
 */
public abstract class Classes {

    private Classes() {}

    public static <T> void registerClass(final ClassInfo<T> info) {
        Compat.get().types().register(info);
    }

    public static List<ClassInfo<?>> getClassInfos() {
        return Compat.get().types().all();
    }

    public static ClassInfo<?> getClassInfo(final String codeName) {
        ClassInfo<?> info = getClassInfoNoError(codeName);
        if (info == null) {
            throw new ch.njol.skript.SkriptAPIException("No class info found for " + codeName);
        }
        return info;
    }

    public static ClassInfo<?> getClassInfoNoError(final String codeName) {
        return codeName == null ? null : Compat.get().types().byCodeName(codeName);
    }

    public static <T> ClassInfo<T> getExactClassInfo(final Class<T> c) {
        return c == null ? null : Compat.get().types().byExactClass(c);
    }

    public static <T> ClassInfo<? super T> getSuperClassInfo(final Class<T> c) {
        return Compat.get().types().bySuperClass(c);
    }

    public static ClassInfo<?> getSuperClassInfo(Class<?>... classes) {
        ClassInfo<?> best = null;
        for (Class<?> c : classes) {
            ClassInfo<?> info = getSuperClassInfo(c);
            if (best == null || best.getC().isAssignableFrom(info.getC())) {
                best = info;
            }
        }
        return best;
    }

    public static Class<?> getClass(final String codeName) {
        return getClassInfo(codeName).getC();
    }

    public static ClassInfo<?> getClassInfoFromUserInput(String name) {
        return Compat.get().types().fromUserInput(name);
    }

    public static Class<?> getClassFromUserInput(final String name) {
        ClassInfo<?> info = getClassInfoFromUserInput(name);
        return info == null ? null : info.getC();
    }

    public static String getExactClassName(final Class<?> c) {
        ClassInfo<?> info = getExactClassInfo(c);
        return info == null ? null : info.getCodeName();
    }

    public static <T> T parseSimple(final String s, final Class<T> c, final ParseContext context) {
        return parse(s, c, context);
    }

    public static <T> T parse(final String s, final Class<T> c, final ParseContext context) {
        return Compat.get().types().parse(s, c, context);
    }

    @SuppressWarnings("unchecked")
    public static <T> Parser<? extends T> getParser(final Class<T> to) {
        ClassInfo<? super T> info = getSuperClassInfo(to);
        return info == null ? null : (Parser<? extends T>) info.getParser();
    }

    public static <T> Parser<? extends T> getExactParser(final Class<T> c) {
        ClassInfo<T> info = getExactClassInfo(c);
        return info == null ? null : info.getParser();
    }

    public static Object clone(Object obj) {
        return Compat.get().values().copy(obj);
    }

    public static String toString(final Object o) {
        return Compat.get().values().text(o);
    }

    public static String getDebugMessage(final Object o) {
        return Compat.get().values().text(o);
    }

    public static String toString(final Object[] os, final int flags, final boolean and) {
        return toString(os, and);
    }

    public static String toString(final Object[] os, final boolean and) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < os.length; i++) {
            if (i > 0) {
                text.append(i == os.length - 1 ? (and ? " and " : " or ") : ", ");
            }
            text.append(toString(os[i]));
        }
        return text.toString();
    }
}
