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
import co.xenastudios.neoskript.runtime.Timespan;

/**
 * A property expression that can be set, like {@code the game mode of %players%}: {@code getter} for
 * each owner, and {@code setter} with the first value given. A number can be added to and taken from too.
 */
final class SettableProperty<T, V> implements Expression, Changeable {

    private final Class<T> ownerClass;
    private final Class<V> valueClass;
    private final Function<? super T, ?> getter;
    private final BiConsumer<? super T, ? super V> setter;
    private Object reset;
    private java.util.function.Consumer<? super T> clear;

    SettableProperty(Class<T> ownerClass, Class<V> valueClass, Function<? super T, ?> getter,
            BiConsumer<? super T, ? super V> setter) {
        this.ownerClass = ownerClass;
        this.valueClass = valueClass;
        this.getter = getter;
        this.setter = setter;
    }

    /**
     * This property, which {@code reset} sets back to {@code value}; or, if {@code value} is a
     * {@code Consumer}, resets each owner by passing it to that.
     */
    SettableProperty<T, V> resettingTo(Object value) {
        this.reset = value;
        return this;
    }

    /** This property, which {@code delete} clears by passing each owner to {@code clear}. */
    SettableProperty<T, V> deleting(java.util.function.Consumer<? super T> clear) {
        this.clear = clear;
        return this;
    }

    /** Registers {@code [the] <property> of %owners%} and {@code %owners%'[s] <property>}. */
    static <T, V> void register(Registrar registrar, String id, String returnType, String property, String owners,
            Class<T> ownerClass, Class<V> valueClass, Function<? super T, ?> getter,
            BiConsumer<? super T, ? super V> setter) {
        registrar.expression(id, returnType, info -> new SettableProperty<>(ownerClass, valueClass, getter, setter),
                Patterns.property(property, owners));
    }

    /** {@link #register(Registrar, String, String, String, String, Class, Class, Function, BiConsumer)}, resettable. */
    static <T, V> void register(Registrar registrar, String id, String returnType, String property, String owners,
            Class<T> ownerClass, Class<V> valueClass, Function<? super T, ?> getter, BiConsumer<? super T, ? super V> setter,
            Object reset) {
        registrar.expression(id, returnType, info -> new SettableProperty<>(ownerClass, valueClass, getter, setter)
                .resettingTo(reset), Patterns.property(property, owners));
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
        boolean amount = Number.class.isAssignableFrom(valueClass) || valueClass == Timespan.class;
        if (mode == Mode.SET || amount && (mode == Mode.ADD || mode == Mode.REMOVE)) {
            // Any number will do for a number property; it's converted to the property's kind.
            return new Class<?>[] {Number.class.isAssignableFrom(valueClass) ? Number.class : valueClass};
        }
        if (mode == Mode.RESET && reset != null || mode == Mode.DELETE && clear != null) {
            return new Class<?>[0];
        }
        // Deleting an amount makes it nothing: 0, or no time.
        return amount && mode == Mode.DELETE ? new Class<?>[0] : null;
    }

    @Override
    public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
        if (mode == Mode.RESET) {
            for (Object owner : owners(context, arguments)) {
                if (!ownerClass.isInstance(owner)) {
                    continue;
                }
                if (reset instanceof java.util.function.Consumer<?> resetter) {
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<Object> each = (java.util.function.Consumer<Object>) resetter;
                    each.accept(owner);
                } else {
                    setter.accept(ownerClass.cast(owner), valueClass.cast(reset));
                }
            }
            return;
        }
        if (mode == Mode.DELETE && clear != null) {
            for (Object owner : owners(context, arguments)) {
                if (ownerClass.isInstance(owner)) {
                    clear.accept(ownerClass.cast(owner));
                }
            }
            return;
        }
        if (mode == Mode.DELETE) {
            V zero = valueClass.cast(valueClass == Timespan.class ? new Timespan(0) : amount(0));
            for (Object owner : owners(context, arguments)) {
                if (ownerClass.isInstance(owner)) {
                    setter.accept(ownerClass.cast(owner), zero);
                }
            }
            return;
        }
        if (delta == null || delta.length == 0) {
            // Set to nothing, a property that can be cleared is: 'set item model of X to {_none}'.
            if (mode == Mode.SET && clear != null) {
                change(context, arguments, null, Mode.DELETE);
            }
            return;
        }
        if (mode != Mode.SET) {
            double by = 0;
            for (Object value : delta) {
                by += value instanceof Number number ? number.doubleValue()
                        : value instanceof Timespan time ? time.millis() : 0;
            }
            if (mode == Mode.REMOVE) {
                by = -by;
            }
            for (Object owner : owners(context, arguments)) {
                if (!ownerClass.isInstance(owner)) {
                    continue;
                }
                Object current = getter.apply(ownerClass.cast(owner));
                if (current instanceof Timespan time) {
                    setter.accept(ownerClass.cast(owner), valueClass.cast(new Timespan(Math.max(0, time.millis() + (long) by))));
                } else if (current instanceof Number number) {
                    setter.accept(ownerClass.cast(owner), valueClass.cast(amount(number.doubleValue() + by)));
                }
            }
            return;
        }
        V value = valueClass.cast(valueClass == Number.class || valueClass == Timespan.class ? delta[0] : amountOrSelf(delta[0]));
        for (Object owner : owners(context, arguments)) {
            if (ownerClass.isInstance(owner)) {
                setter.accept(ownerClass.cast(owner), value);
            }
        }
    }

    /** {@code value} as the property's kind of number. */
    private Object amount(double value) {
        if (valueClass == Integer.class) {
            return (int) value;
        }
        if (valueClass == Long.class) {
            return (long) value;
        }
        if (valueClass == Float.class) {
            return (float) value;
        }
        return value;
    }

    private Object amountOrSelf(Object value) {
        return value instanceof Number number && !valueClass.isInstance(value) ? amount(number.doubleValue()) : value;
    }

        private static Object[] owners(TriggerContext context, Object[] arguments) {
        Object owners = arguments.length == 0 ? null : arguments[0];
        return owners instanceof Object[] array ? array : owners == null ? new Object[0] : new Object[] {owners};
    }
}
