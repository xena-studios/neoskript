/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Finds the event values of a class as Skript 2.17's {@code EventValueRegistry} resolves them, with the same
 * steps and preferences: a value of exactly the class for the closest event class; else one of a subclass, or
 * of the event's subclasses (checked when it runs); else, with conversion allowed, one of a superclass cast down
 * when it runs, and last any value a converter turns into one. Past and future values fall back to the value
 * now. What's found for each event class, class and time is kept, so each is looked into once.
 */
final class EventValueResolver {

    /** Skript's {@code ALLOW_CONVERSION} flag. */
    static final int CONVERSION = 1;
    /** Skript's {@code FALLBACK_TO_DEFAULT_TIME_STATE} flag. */
    static final int FALLBACK = 2;
    /** Skript's default flags: both. */
    static final int DEFAULT = CONVERSION | FALLBACK;

    /**
     * A registered event value: of events of {@code eventClass}, its values {@code valueClass}, of the library's
     * type {@code type} (where several types share a class), at {@code time} (-1 past, 0 now, 1 future). If
     * {@code excluded} isn't {@code null}, the events don't have it, and that's Skript's error for asking.
     */
    record Value(Class<?> eventClass, Class<?> valueClass, String type, int time, Function<Object, ?> getter,
            String excluded) {}

    /** A value found: its class and how to get it from an event. */
    record Candidate(Class<?> valueClass, String type, Function<Object, Object> getter) {}

    /** What resolving found: its candidates, or that an excluded value stopped it, with the error. */
    record Resolution(List<Candidate> all, String error) {

        static final Resolution EMPTY = new Resolution(List.of(), null);

        boolean successful() {
            return !all.isEmpty();
        }

        boolean errored() {
            return error != null;
        }

        boolean multiple() {
            return all.size() > 1;
        }
    }

    private record Key(Class<?> event, Class<?> value, String type, int time, int flags) {}

    private final List<Value> values;
    private final Converters converters;
    /** The library's type whose values are exactly a class, or {@code null}: Skript's exact class info. */
    private final Function<Class<?>, String> exactType;
    private final Map<Key, Resolution> cache = new ConcurrentHashMap<>();

    EventValueResolver(List<Value> values, Converters converters, Function<Class<?>, String> exactType) {
        this.values = values;
        this.converters = converters;
        this.exactType = exactType;
    }

    /**
     * The values of {@code valueClass} (of the type {@code type}, or {@code null} for any with the class) of events
     * of {@code eventClass} at {@code time}, with Skript's {@code flags}.
     */
    Resolution resolve(Class<?> eventClass, Class<?> valueClass, String type, int time, int flags) {
        if (time == 0) {
            flags &= ~FALLBACK;
        }
        Key key = new Key(eventClass, valueClass, type, time, flags);
        Resolution cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Resolution resolution = resolveNew(eventClass, valueClass, type, time, flags);
        cache.put(key, resolution);
        return resolution;
    }

    private Resolution resolveNew(Class<?> event, Class<?> valueClass, String type, int time, int flags) {
        List<Value> atTime = values.stream().filter(value -> value.time() == time).toList();
        // Exactly the class, for the closest superclass of the event.
        Resolution resolution = resolve(atTime, event, valueClass, type,
                value -> value.eventClass().isAssignableFrom(event) && same(value, valueClass, type),
                eventDistance(event), null, true);
        if (resolution.successful() || resolution.errored()) {
            return resolution;
        }
        // A subclass, or for a subclass of the event.
        resolution = resolve(atTime, event, valueClass, type,
                value -> related(value.eventClass(), event) && assignable(valueClass, type, value),
                valueDistance(event, valueClass), value -> converted(event, valueClass, type, value), true);
        if (resolution.successful() || resolution.errored()) {
            return resolution;
        }
        if ((flags & CONVERSION) != 0) {
            // A superclass, cast down when it runs.
            resolution = resolve(atTime, event, valueClass, type,
                    value -> related(value.eventClass(), event) && value.valueClass().isAssignableFrom(valueClass)
                            && !sameClassOtherType(value, valueClass, type),
                    valueDistance(event, valueClass), value -> cast(event, valueClass, type, value), true);
            if (resolution.successful() || resolution.errored()) {
                return resolution;
            }
            // Anything a converter makes one of.
            resolution = resolve(atTime, event, valueClass, type, value -> related(value.eventClass(), event),
                    biEventDistance(event), value -> converted(event, valueClass, type, value), false);
            if (resolution.successful() || resolution.errored()) {
                return resolution;
            }
        }
        if ((flags & FALLBACK) != 0) {
            return resolve(event, valueClass, type, 0, flags);
        }
        return Resolution.EMPTY;
    }

    /** Skript's {@code Resolver.resolve}: the best candidates passing {@code filter}, by {@code comparator}. */
    private Resolution resolve(List<Value> atTime, Class<?> event, Class<?> valueClass, String type,
            Predicate<Value> filter, Comparator<Value> comparator, Function<Value, Candidate> mapper,
            boolean filterMatches) {
        List<Candidate> best = new ArrayList<>();
        Value bestMatch = null;
        for (Value value : atTime) {
            if (!filter.test(value)) {
                continue;
            }
            if (value.excluded() != null) {
                // An excluded value stops the search where the event is one it's excluded from.
                if (value.eventClass().isAssignableFrom(event)) {
                    return new Resolution(List.of(), value.excluded());
                }
                continue;
            }
            int comparison = bestMatch != null ? comparator.compare(value, bestMatch) : -1;
            if (comparison > 0) {
                continue;
            }
            Candidate candidate = mapper != null ? mapper.apply(value)
                    : new Candidate(value.valueClass(), value.type(), getter(value));
            if (candidate == null) {
                continue;
            }
            if (comparison < 0) {
                best.clear();
                bestMatch = value;
            }
            best.add(candidate);
        }
        return new Resolution(filterMatches ? matching(valueClass, type, best) : List.copyOf(best), null);
    }

    /**
     * Skript's {@code filterEventValues}: of several, only those whose class is the type asked for, rather than
     * a type of its own ({@code event-entity} in a trade is the villager, as the player is {@code event-player}).
     */
    private List<Candidate> matching(Class<?> valueClass, String type, List<Candidate> candidates) {
        if (candidates.size() <= 1) {
            return List.copyOf(candidates);
        }
        String requested = type != null ? type : exactType.apply(valueClass);
        List<Candidate> filtered = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String own = candidate.type() != null ? candidate.type() : exactType.apply(candidate.valueClass());
            if (own != null && !own.equals(requested)) {
                continue;
            }
            filtered.add(candidate);
        }
        return List.copyOf(filtered.isEmpty() ? candidates : filtered);
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> getter(Value value) {
        return (Function<Object, Object>) value.getter();
    }

    /**
     * Skript's {@code ConvertedEventValue.newInstance}: the value for {@code event} as a {@code valueClass}: itself
     * if it already is one, else through a converter (and only for events of its class), or {@code null}.
     */
    private Candidate converted(Class<?> event, Class<?> valueClass, String type, Value value) {
        Function<Object, Object> getter = getter(value);
        if (value.eventClass().isAssignableFrom(event) && assignable(valueClass, type, value)) {
            return new Candidate(value.valueClass(), value.type(), getter);
        }
        Function<Object, Object> converter = converter(value.valueClass(), valueClass);
        if (converter == null || sameClassOtherType(value, valueClass, type)) {
            return null;
        }
        return new Candidate(valueClass, type, forEvents(value.eventClass(), event1 -> {
            Object got = getter.apply(event1);
            return got == null ? null : converter.apply(got);
        }));
    }

    /** A value of a superclass as a {@code valueClass}, cast down when it runs: {@code null} if it isn't one. */
    private Candidate cast(Class<?> event, Class<?> valueClass, String type, Value value) {
        Function<Object, Object> getter = getter(value);
        return new Candidate(valueClass, type, forEvents(value.eventClass(), event1 -> {
            Object got = getter.apply(event1);
            if (got instanceof Object[] array && valueClass.isArray()) {
                for (Object element : array) {
                    if (!valueClass.componentType().isInstance(element)) {
                        return null;
                    }
                }
                return got;
            }
            return valueClass.isInstance(got) ? got : null;
        }));
    }

    /** {@code getter} for events of {@code eventClass} only; others have no value. */
    private static Function<Object, Object> forEvents(Class<?> eventClass, Function<Object, Object> getter) {
        return event -> eventClass.isInstance(event) ? getter.apply(event) : null;
    }

    /**
     * Skript's {@code ConvertedEventValue.getConverter}: for arrays, each element's; nothing to do where the value
     * already is one; else the converter's.
     */
    private Function<Object, Object> converter(Class<?> from, Class<?> to) {
        if (from.isArray() && to.isArray()) {
            Function<Object, Object> element = converter(from.componentType(), to.componentType());
            if (element == null) {
                return null;
            }
            return value -> {
                Object[] array = (Object[]) value;
                Object[] converted = (Object[]) java.lang.reflect.Array.newInstance(to.componentType(), array.length);
                for (int i = 0; i < array.length; i++) {
                    Object one = array[i] == null ? null : element.apply(array[i]);
                    if (one == null) {
                        return null;
                    }
                    converted[i] = one;
                }
                return converted;
            };
        }
        if (to.isAssignableFrom(from)) {
            return value -> value;
        }
        return converters.converter(from, to);
    }

    /** Whether the value's class is exactly {@code valueClass}, of the type asked for. */
    private static boolean same(Value value, Class<?> valueClass, String type) {
        return value.valueClass().equals(valueClass) && !sameClassOtherType(value, valueClass, type);
    }

    /** Whether a {@code valueClass} (of {@code type}) can be the value: it's of the class or a subclass. */
    private static boolean assignable(Class<?> valueClass, String type, Value value) {
        return valueClass.isAssignableFrom(value.valueClass()) && !sameClassOtherType(value, valueClass, type);
    }

    /** Whether the value is of another of the library's types that share a class, as villager types do. */
    private static boolean sameClassOtherType(Value value, Class<?> valueClass, String type) {
        return type != null && value.type() != null && value.valueClass().equals(valueClass)
                && !type.equals(value.type());
    }

    private static boolean related(Class<?> a, Class<?> b) {
        return a.isAssignableFrom(b) || b.isAssignableFrom(a);
    }

    /** Skript's {@code ClassUtils.hierarchyDistance}: how many superclasses up {@code a} is from {@code b}, or -1. */
    static int hierarchyDistance(Class<?> a, Class<?> b) {
        if (!a.isAssignableFrom(b)) {
            return -1;
        }
        if (a.equals(b)) {
            return 0;
        }
        int distance = 0;
        Class<?> current = b;
        while (current != null && !a.equals(current)) {
            current = current.getSuperclass();
            distance++;
        }
        return distance;
    }

    /** Skript's {@code EVENT_DISTANCE_COMPARATOR}. */
    private static Comparator<Value> eventDistance(Class<?> event) {
        return Comparator.comparingInt(value -> hierarchyDistance(value.eventClass(), event));
    }

    /** Skript's {@code BI_EVENT_DISTANCE_COMPARATOR}. */
    private static Comparator<Value> biEventDistance(Class<?> event) {
        return eventDistance(event).thenComparingInt(value -> hierarchyDistance(event, value.valueClass()));
    }

    /** Skript's {@code EVENT_VALUE_DISTANCE_COMPARATOR}. */
    private static Comparator<Value> valueDistance(Class<?> event, Class<?> valueClass) {
        return biEventDistance(event).thenComparingInt(value -> hierarchyDistance(valueClass, value.valueClass()));
    }
}
