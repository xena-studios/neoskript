/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The library's converters as Skript's {@code Converters} finds them: registered ones, chains of two made once
 * registration is done, and for classes no converter is for exactly, one for a superclass or subclass that checks
 * the value's class when it runs. Looking a converter up only looks at classes, never at the server.
 */
final class Converters {

    /** A converter from {@code from} to {@code to}, with Skript's chaining flags. */
    record Info(Class<?> from, Class<?> to, Function<Object, Object> converter, int flags) {}

    /** Stands for "no converter" in the cache, which can't hold {@code null}. */
    private static final Info NONE = new Info(Object.class, Object.class, value -> null, 0);

    private final List<Info> converters = new ArrayList<>();
    private final Map<List<Class<?>>, Info> cache = new ConcurrentHashMap<>();
    private boolean chained;

    /** Registers a converter, as Skript's {@code registerConverter}; a second one for the same classes is ignored. */
    @SuppressWarnings("unchecked")
    synchronized void register(Class<?> from, Class<?> to, Function<?, ?> converter, int flags) {
        if ((flags & Registrar.PLACEHOLDERS_ONLY) != 0 || exactExists(from, to)) {
            return;
        }
        converters.add(new Info(from, to, (Function<Object, Object>) converter, flags));
    }

    /**
     * How to convert a {@code from} into a {@code to}, or {@code null} if nothing can: Skript's
     * {@code Converters.getConverter}. The converter returns {@code null} for values it can't convert.
     */
    Function<Object, Object> converter(Class<?> from, Class<?> to) {
        Info info = info(from, to);
        return info == null ? null : info.converter();
    }

    private Info info(Class<?> from, Class<?> to) {
        chain();
        Info info = cache.computeIfAbsent(List.of(from, to), key -> {
            Info found = find(from, to);
            return found == null ? NONE : found;
        });
        return info == NONE ? null : info;
    }

    /** Makes the chains of two converters, as Skript's {@code createChainedConverters} once registration is done. */
    private synchronized void chain() {
        if (chained) {
            return;
        }
        chained = true;
        for (int i = 0; i < converters.size(); i++) {
            Info first = converters.get(i);
            // Not from i + 1: chains made in the loop are chained further.
            for (int j = 0; j < converters.size(); j++) {
                Info second = converters.get(j);
                if (second.from() != Object.class && first.from() != second.to()
                        && (first.flags() & Registrar.NO_RIGHT_CHAINING) == 0
                        && (second.flags() & Registrar.NO_LEFT_CHAINING) == 0
                        && second.from().isAssignableFrom(first.to())
                        && !exactExists(first.from(), second.to())) {
                    converters.add(chained(first, second));
                } else if (first.from() != Object.class && second.from() != first.to()
                        && (first.flags() & Registrar.NO_LEFT_CHAINING) == 0
                        && (second.flags() & Registrar.NO_RIGHT_CHAINING) == 0
                        && first.from().isAssignableFrom(second.to())
                        && !exactExists(second.from(), first.to())) {
                    converters.add(chained(second, first));
                }
            }
        }
    }

    private static Info chained(Info first, Info second) {
        Function<Object, Object> one = first.converter();
        Function<Object, Object> two = second.converter();
        return new Info(first.from(), second.to(), value -> {
            Object middle = one.apply(value);
            return middle == null ? null : two.apply(middle);
        }, first.flags() | second.flags());
    }

    private boolean exactExists(Class<?> from, Class<?> to) {
        for (Info info : converters) {
            if (info.from() == from && info.to() == to) {
                return true;
            }
        }
        return false;
    }

    /** Skript's {@code getConverterInfo_i}, in its order. */
    private Info find(Class<?> from, Class<?> to) {
        for (Info info : converters) {
            if (info.from() == from && info.to() == to) {
                return info;
            }
        }
        for (Info info : converters) {
            if (info.from().isAssignableFrom(from) && to.isAssignableFrom(info.to())) {
                return info;
            }
        }
        if (from == Object.class) {
            // Converted when it runs, by the value's own class.
            return new Info(from, to, value -> convert(value, to), Registrar.NO_LEFT_CHAINING);
        }
        for (Info info : converters) {
            int flags = info.flags();
            Function<Object, Object> converter = info.converter();
            if (info.from().isAssignableFrom(from) && info.to().isAssignableFrom(to)) {
                if ((flags & Registrar.ALLOW_UNSAFE_CASTS) == 0 && (flags & Registrar.NO_RIGHT_CHAINING) != 0) {
                    continue;
                }
                // What it gives may only share a superclass with 'to'.
                return new Info(from, to, value -> {
                    Object converted = converter.apply(value);
                    return to.isInstance(converted) ? converted : null;
                }, 0);
            } else if (from.isAssignableFrom(info.from()) && to.isAssignableFrom(info.to())) {
                if ((flags & Registrar.ALLOW_UNSAFE_CASTS) == 0 && (flags & Registrar.NO_LEFT_CHAINING) != 0) {
                    continue;
                }
                // Only some values of 'from' convert.
                Class<?> sub = info.from();
                return new Info(from, to, value -> sub.isInstance(value) ? converter.apply(value) : null, 0);
            }
        }
        for (Info info : converters) {
            if (from.isAssignableFrom(info.from()) && info.to().isAssignableFrom(to)) {
                int flags = info.flags();
                if ((flags & Registrar.ALLOW_UNSAFE_CASTS) == 0 && ((flags & Registrar.NO_LEFT_CHAINING) != 0
                        || (flags & Registrar.NO_RIGHT_CHAINING) != 0)) {
                    continue;
                }
                Class<?> sub = info.from();
                Function<Object, Object> converter = info.converter();
                return new Info(from, to, value -> {
                    if (!sub.isInstance(value)) {
                        return null;
                    }
                    Object converted = converter.apply(value);
                    return to.isInstance(converted) ? converted : null;
                }, 0);
            }
        }
        return null;
    }

    /** Skript's {@code Converters.convert}: {@code value} as a {@code to}, by its own class, or {@code null}. */
    Object convert(Object value, Class<?> to) {
        if (value == null || to.isInstance(value)) {
            return value;
        }
        Function<Object, Object> converter = converter(value.getClass(), to);
        return converter == null ? null : converter.apply(value);
    }
}
