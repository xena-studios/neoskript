/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.bukkit.lang.eventvalue;

import ch.njol.skript.classes.Changer.ChangeMode;
import java.util.Collection;
import java.util.Optional;
import java.util.SequencedCollection;
import org.bukkit.event.Event;
import org.skriptlang.skript.lang.converter.Converter;

/** A value an event has, as {@code event-player}. */
public interface EventValue<E extends Event, V> {

    static <E extends Event, V> Builder<E, V> builder(Class<E> eventClass, Class<V> valueClass) {
        return new co.xenastudios.neoskript.compat.CompatEventValues.ValueBuilder<>(eventClass, valueClass);
    }

    static <E extends Event, V> EventValue<E, V> simple(Class<E> eventClass, Class<V> valueClass, Converter<E, V> converter) {
        return builder(eventClass, valueClass).getter(converter).build();
    }

    Class<E> eventClass();

    Class<V> valueClass();

    SequencedCollection<String> patterns();

    V get(E event);

    Converter<E, V> converter();

    boolean hasChanger(ChangeMode mode);

    Optional<Changer<E, V>> changer(ChangeMode mode);

    Time time();

    Collection<Class<? extends E>> excludedEvents();

    String excludedErrorMessage();

    default boolean matches(Class<? extends Event> eventClass, Class<?> valueClass) {
        return eventClass().isAssignableFrom(eventClass) && valueClass().isAssignableFrom(valueClass);
    }

    /** Before the event, during it, or after it. */
    enum Time {
        PAST(-1), NOW(0), FUTURE(1);

        private final int value;

        Time(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Time of(int value) {
            return switch (value) {
                case -1 -> PAST;
                case 0 -> NOW;
                case 1 -> FUTURE;
                default -> throw new IllegalArgumentException("Invalid time value: " + value);
            };
        }
    }

    /** Whether an event value can be used in an event. */
    enum Validation {
        VALID, INVALID, ABORT
    }

    /** Changes an event's value. */
    interface Changer<E extends Event, V> {
        void change(E event, V value);
    }

    /** Builds an event value. */
    interface Builder<E extends Event, V> {

        Builder<E, V> patterns(String... patterns);

        Builder<E, V> getter(Converter<E, V> converter);

        Builder<E, V> registerChanger(ChangeMode mode, Changer<E, V> changer);

        Builder<E, V> time(Time time);

        default Builder<E, V> excludes(Class<? extends E> event) {
            return excludes(java.util.List.<Class<? extends E>>of(event).toArray(new Class[0]));
        }

        default Builder<E, V> excludes(Class<? extends E> event1, Class<? extends E> event2) {
            return excludes(java.util.List.<Class<? extends E>>of(event1, event2).toArray(new Class[0]));
        }

        default Builder<E, V> excludes(Class<? extends E> event1, Class<? extends E> event2, Class<? extends E> event3) {
            return excludes(java.util.List.<Class<? extends E>>of(event1, event2, event3).toArray(new Class[0]));
        }

        Builder<E, V> excludes(Class<? extends E>[] events);

        Builder<E, V> excludedErrorMessage(String message);

        Builder<E, V> contextDependent();

        EventValue<E, V> build();
    }
}
