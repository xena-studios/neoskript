/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A property expression that can be set, like {@code the game mode of %players%}: {@code getter} for
 * each owner, and {@code setter} with the first value given.
 */
final class SettableProperty<T, V> implements Expression, Changeable {

    private final Class<T> ownerClass;
    private final Class<V> valueClass;
    private final Function<? super T, ?> getter;
    private final BiConsumer<? super T, ? super V> setter;

    private SettableProperty(Class<T> ownerClass, Class<V> valueClass, Function<? super T, ?> getter,
            BiConsumer<? super T, ? super V> setter) {
        this.ownerClass = ownerClass;
        this.valueClass = valueClass;
        this.getter = getter;
        this.setter = setter;
    }

    /** Registers {@code [the] <property> of %owners%} and {@code %owners%'[s] <property>}. */
    static <T, V> void register(Registrar registrar, String id, String returnType, String property, String owners,
            Class<T> ownerClass, Class<V> valueClass, Function<? super T, ?> getter,
            BiConsumer<? super T, ? super V> setter) {
        registrar.expression(id, returnType, info -> new SettableProperty<>(ownerClass, valueClass, getter, setter),
                Patterns.property(property, owners));
    }

    /** Registers it with its own patterns, whose first placeholder is the owners. */
    static <T, V> void register(Registrar registrar, String id, String returnType, Class<T> ownerClass,
            Class<V> valueClass, Function<? super T, ?> getter, BiConsumer<? super T, ? super V> setter,
            String... patterns) {
        registrar.expression(id, returnType, info -> new SettableProperty<>(ownerClass, valueClass, getter, setter),
                patterns);
    }

    @Override
    public Object get(TriggerContext context, Object[] arguments) {
        List<Object> values = new ArrayList<>();
        for (Object owner : owners(context, arguments)) {
            if (ownerClass.isInstance(owner)) {
                Object value = getter.apply(ownerClass.cast(owner));
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    @Override
    public Class<?>[] acceptChange(Mode mode) {
        return mode == Mode.SET ? new Class<?>[] {valueClass} : null;
    }

    @Override
    public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
        if (delta == null || delta.length == 0) {
            return;
        }
        V value = valueClass.cast(delta[0]);
        for (Object owner : owners(context, arguments)) {
            if (ownerClass.isInstance(owner)) {
                setter.accept(ownerClass.cast(owner), value);
            }
        }
    }

    private static Object[] owners(TriggerContext context, Object[] arguments) {
        Object owners = arguments.length == 0 ? null : arguments[0];
        return owners instanceof Object[] array ? array : owners == null ? new Object[0] : new Object[] {owners};
    }
}
