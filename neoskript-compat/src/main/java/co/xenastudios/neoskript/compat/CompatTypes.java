/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.compiler.Library;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Skript's types as addons look them up: NeoSkript's own, as class infos made for them, and the ones addons register.
 * An addon registering a type NeoSkript has already (by code name or class) keeps NeoSkript's.
 */
public final class CompatTypes {

    private final Library library;
    private final List<ClassInfo<?>> registered = new CopyOnWriteArrayList<>();
    private final Map<String, ClassInfo<?>> byCodeName = new ConcurrentHashMap<>();
    private final Map<Class<?>, ClassInfo<?>> byClass = new ConcurrentHashMap<>();
    private final Map<String, ClassInfo<?>> neoskript = new ConcurrentHashMap<>();

    CompatTypes(Library library) {
        this.library = library;
    }

    /** Registers an addon's type, as {@code Classes.registerClass}. */
    public void register(ClassInfo<?> info) {
        if (!Compat.get().accepting()) {
            throw new SkriptAPIException("Registering is disabled after initialisation!");
        }
        String codeName = info.getCodeName();
        ClassInfo<?> existing = byCodeName.get(codeName);
        if (existing != null) {
            throw new IllegalArgumentException("Can't register " + info.getC().getName() + " with the code name "
                    + codeName + " because that code name is already used by " + existing.getC().getName());
        }
        if (byClass.containsKey(info.getC())) {
            throw new IllegalArgumentException("Can't register the class info " + codeName + " because the class "
                    + info.getC().getName() + " is already registered");
        }
        byCodeName.put(codeName, info);
        byClass.put(info.getC(), info);
        registered.add(info);
    }

    /** The types addons registered, in order. */
    List<ClassInfo<?>> registered() {
        return List.copyOf(registered);
    }

    /** Whether NeoSkript has a type for this code name or class already, so an addon's goes unused. */
    boolean neoskriptHas(ClassInfo<?> info) {
        return library.hasType(info.getCodeName())
                || library.exactTypeOf(CompatAdapters.neoskriptClass(info.getC())) != null;
    }

    public List<ClassInfo<?>> all() {
        return new ArrayList<>(registered);
    }

    public ClassInfo<?> byCodeName(String codeName) {
        ClassInfo<?> info = byCodeName.get(codeName);
        if (info != null) {
            return info;
        }
        return library.hasType(codeName) ? neoskript(codeName) : null;
    }

    @SuppressWarnings("unchecked")
    public <T> ClassInfo<T> byExactClass(Class<T> type) {
        ClassInfo<?> info = byClass.get(type);
        if (info != null) {
            return (ClassInfo<T>) info;
        }
        String id = library.exactTypeOf(CompatAdapters.neoskriptClass(type));
        return id == null ? null : (ClassInfo<T>) neoskript(id);
    }

    @SuppressWarnings("unchecked")
    public <T> ClassInfo<? super T> bySuperClass(Class<T> type) {
        ClassInfo<? super T> exact = byExactClass(type);
        if (exact != null) {
            return exact;
        }
        ClassInfo<?> best = null;
        for (ClassInfo<?> info : registered) {
            if (info.getC().isAssignableFrom(type) && (best == null || best.getC().isAssignableFrom(info.getC()))) {
                best = info;
            }
        }
        String id = library.typeOf(CompatAdapters.neoskriptClass(type));
        ClassInfo<?> fromNeoSkript = id == null ? null : neoskript(id);
        if (fromNeoSkript != null && (best == null || best.getC().isAssignableFrom(fromNeoSkript.getC()))) {
            best = fromNeoSkript;
        }
        return (ClassInfo<? super T>) (best != null ? best : neoskript("object"));
    }

    /** A type as a user writes it, as {@code integer} or {@code players}: by code name, user input, or plural. */
    public ClassInfo<?> fromUserInput(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.strip().toLowerCase(Locale.ENGLISH);
        for (ClassInfo<?> info : registered) {
            if (info.matchesUserInput(lower)) {
                return info;
            }
        }
        for (String candidate : singulars(lower)) {
            ClassInfo<?> info = byCodeName(candidate);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    /** {@code word} and what it'd be as a plural of: {@code entities} is {@code entity}'s. */
    static List<String> singulars(String word) {
        List<String> candidates = new ArrayList<>(4);
        candidates.add(word);
        if (word.endsWith("ies") && word.length() > 3) {
            candidates.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.endsWith("es") && word.length() > 2) {
            candidates.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("s") && word.length() > 1) {
            candidates.add(word.substring(0, word.length() - 1));
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    public <T> T parse(String text, Class<T> type, ParseContext context) {
        ClassInfo<?> info = byExactClass(type);
        if (info != null && !info.isNeoSkript()) {
            Parser<?> parser = info.getParser();
            if (parser == null || !parser.canParse(context)) {
                return null;
            }
            Object parsed = parser.parse(text, context);
            return type.isInstance(parsed) ? (T) parsed : null;
        }
        String id = info != null ? info.getCodeName() : library.typeOf(type);
        if (id == null) {
            return null;
        }
        Object parsed = CompatAdapters.toSkript(NeoSkript.get().values().parse(text, id), type);
        return type.isInstance(parsed) ? (T) parsed : null;
    }

    private static <T> ClassInfo<T> facade(Class<T> javaClass, String id) {
        return ClassInfo.ofNeoSkript(javaClass, id);
    }

    private ClassInfo<?> neoskript(String id) {
        return neoskript.computeIfAbsent(id, key -> {
            Class<?> javaClass = library.javaClass(key);
            javaClass = javaClass == null ? Object.class : CompatAdapters.skriptClass(javaClass);
            return facade(javaClass, key);
        });
    }
}
