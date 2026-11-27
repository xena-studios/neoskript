/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.Changer.ChangeMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.event.Event;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.lang.converter.Converter;

/** Event values addons register, as Skript's event value registry. */
public final class CompatEventValues implements EventValueRegistry {

    private final List<EventValue<?, ?>> values = new CopyOnWriteArrayList<>();

    CompatEventValues() {}

    @Override
    public <E extends Event> void register(EventValue<E, ?> value) {
        values.add(value);
    }

    @Override
    public boolean unregister(EventValue<?, ?> value) {
        return values.remove(value);
    }

    @Override
    public boolean isRegistered(EventValue<?, ?> value) {
        return values.contains(value);
    }

    @Override
    public boolean isRegistered(Class<? extends Event> eventClass, Class<?> valueClass, EventValue.Time time) {
        for (EventValue<?, ?> value : values) {
            if (value.eventClass() == eventClass && value.valueClass() == valueClass && value.time() == time) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String input) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String input, EventValue.Time time) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String input, EventValue.Time time,
            Flags flags) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass,
            EventValue.Time time) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass,
            EventValue.Time time, Flags flags) {
        throw unsupported();
    }

    @Override
    public <E extends Event, V> Resolution<E, V> resolveExact(Class<E> eventClass, Class<V> valueClass,
            EventValue.Time time) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("NeoSkriptCompat doesn't look event values up for addons yet");
    }

    @Override
    public List<EventValue<?, ?>> elements() {
        return List.copyOf(values);
    }

    @Override
    public List<EventValue<?, ?>> elements(EventValue.Time time) {
        return values.stream().filter(value -> value.time() == time).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> List<EventValue<? extends E, ?>> elements(Class<E> eventClass) {
        List<EventValue<? extends E, ?>> matching = new ArrayList<>();
        for (EventValue<?, ?> value : values) {
            if (eventClass.isAssignableFrom(value.eventClass())) {
                matching.add((EventValue<? extends E, ?>) value);
            }
        }
        return matching;
    }

    @Override
    public EventValueRegistry unmodifiableView() {
        return this;
    }

    /** An event value as built. */
    record Value<E extends Event, V>(Class<E> eventClass, Class<V> valueClass, SequencedCollection<String> patterns,
            Converter<E, V> converter, Map<ChangeMode, Changer<E, V>> changers, Time time,
            Collection<Class<? extends E>> excludedEvents, String excludedErrorMessage) implements EventValue<E, V> {

        @Override
        public V get(E event) {
            return converter.convert(event);
        }

        @Override
        public boolean hasChanger(ChangeMode mode) {
            return changers.containsKey(mode);
        }

        @Override
        public Optional<Changer<E, V>> changer(ChangeMode mode) {
            return Optional.ofNullable(changers.get(mode));
        }
    }

    /** Builds an event value. */
    public static final class ValueBuilder<E extends Event, V> implements EventValue.Builder<E, V> {

        private final Class<E> eventClass;
        private final Class<V> valueClass;
        private final List<String> patterns = new ArrayList<>();
        private Converter<E, V> converter;
        private final Map<ChangeMode, EventValue.Changer<E, V>> changers = new EnumMap<>(ChangeMode.class);
        private EventValue.Time time = EventValue.Time.NOW;
        private final List<Class<? extends E>> excluded = new ArrayList<>();
        private String excludedErrorMessage;

        public ValueBuilder(Class<E> eventClass, Class<V> valueClass) {
            this.eventClass = eventClass;
            this.valueClass = valueClass;
        }

        @Override
        public ValueBuilder<E, V> patterns(String... patterns) {
            this.patterns.addAll(Arrays.asList(patterns));
            return this;
        }

        @Override
        public ValueBuilder<E, V> getter(Converter<E, V> converter) {
            this.converter = converter;
            return this;
        }

        @Override
        public ValueBuilder<E, V> registerChanger(ChangeMode mode, EventValue.Changer<E, V> changer) {
            changers.put(mode, changer);
            return this;
        }

        @Override
        public ValueBuilder<E, V> time(EventValue.Time time) {
            this.time = time;
            return this;
        }

        @Override
        public ValueBuilder<E, V> excludes(Class<? extends E>[] events) {
            excluded.addAll(Arrays.asList(events));
            return this;
        }

        @Override
        public ValueBuilder<E, V> excludedErrorMessage(String message) {
            this.excludedErrorMessage = message;
            return this;
        }

        @Override
        public ValueBuilder<E, V> contextDependent() {
            return this;
        }

        @Override
        public EventValue<E, V> build() {
            if (converter == null) {
                throw new IllegalStateException("An event value needs a getter");
            }
            return new Value<>(eventClass, valueClass, List.copyOf(patterns), converter, Map.copyOf(changers), time,
                    List.copyOf(excluded), excludedErrorMessage);
        }
    }
}
