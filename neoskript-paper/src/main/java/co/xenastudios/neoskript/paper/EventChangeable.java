/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.Set;
import java.util.function.Function;

/**
 * A value of the event whose changes are spelled out mode by mode, as Skript's event expressions do:
 * {@code getter} gives it from the event, and {@code changer} changes it, for events of {@code eventClass}.
 */
final class EventChangeable<E> implements Expression, Changeable {

    /** Changes the value in one event. {@code delta} is {@code null} for {@code delete} and {@code reset}. */
    @FunctionalInterface
    interface Changer<E> {
        void change(E event, Object[] delta, Mode mode);
    }

    private final Class<E> eventClass;
    private final Function<? super E, ?> getter;
    private final Class<?> valueClass;
    private final Set<Mode> modes;
    private final Changer<E> changer;

    /**
     * @param valueClass the class of the values the change takes; an array class for several
     * @param modes the modes it can be changed in
     */
    EventChangeable(Class<E> eventClass, Function<? super E, ?> getter, Class<?> valueClass, Set<Mode> modes,
            Changer<E> changer) {
        this.eventClass = eventClass;
        this.getter = getter;
        this.valueClass = valueClass;
        this.modes = modes;
        this.changer = changer;
    }

    /** A value of the event that can't be changed. */
    EventChangeable(Class<E> eventClass, Function<? super E, ?> getter) {
        this(eventClass, getter, null, Property.modes(), null);
    }

    @Override
    public Object get(TriggerContext context, Object[] arguments) {
        return eventClass.isInstance(context.event()) ? getter.apply(eventClass.cast(context.event())) : null;
    }

    @Override
    public Class<?>[] acceptChange(Mode mode) {
        if (!modes.contains(mode)) {
            return null;
        }
        return valueClass == null ? new Class<?>[0] : new Class<?>[] {valueClass};
    }

    @Override
    public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
        if (!eventClass.isInstance(context.event())) {
            return;
        }
        if (delta != null && delta.length == 0) {
            delta = null;
        }
        if (delta == null && mode != Mode.DELETE && mode != Mode.RESET && mode != Mode.SET) {
            return;
        }
        changer.change(eventClass.cast(context.event()), delta, mode);
    }

    /** Ticks as a {@code timespan}'s ticks, kept to what an int holds. */
    static int ticks(Object timespan) {
        return timespan instanceof co.xenastudios.neoskript.runtime.Timespan time
                ? (int) Property.fit(Integer.MIN_VALUE, time.ticks(), Integer.MAX_VALUE) : 0;
    }
}
