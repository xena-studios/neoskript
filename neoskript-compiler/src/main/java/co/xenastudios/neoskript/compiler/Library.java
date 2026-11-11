/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Function;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.RegexRole;
import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything registered through the {@link Registrar}: the frontend's syntax registry, plus what each
 * element is bound to at run time. Register everything, then call {@link #registry()}, which freezes it.
 */
public final class Library implements Registrar {

    /** Types that only accept themselves and registered subtypes, rather than any unrelated game type. */
    private static final Set<String> STRICT_TYPES =
            Set.of("number", "integer", "long", "string", "boolean", "timespan", "date", "time", "entitytype", "itemtype",
                    "skriptpotioneffect", "hiddenpotioneffect", "damagecause", "direction", "particle", "gameeffect",
                    "entityeffect", "itementity", "world", "weathertype", "enchantment");

    private record Key(SyntaxKind kind, String id) {}

    /** The binding of the core syntax the compiler implements itself: control flow, variables, arithmetic. */
    record Core(String id) {}

    /** The binding of a server event. */
    public record ServerEvent(List<Class<?>> classes, ElementFactory<Condition> filter) {}

    /** One of Skript's events, as it names them in errors: {@code On Break / Mine}, its classes and patterns. */
    private record EventName(String name, List<Class<?>> classes, List<String> patterns) {}

    /**
     * A value of {@code type} events of {@code eventClass} have, at {@code time} (-1 past, 0 now, 1 future). If
     * {@code excluded} isn't {@code null}, the events don't have it, and that's the error for asking. A
     * {@code valueClass} makes it the value of only a narrower kind, like the projectile of a damage event, which
     * {@code event-projectile} gets; plain {@code event-entity} doesn't.
     */
    private record EventValue(Class<?> eventClass, String type, int time, Class<?> valueClass,
            java.util.function.Function<Object, ?> getter, String excluded) {}

    private final SyntaxRegistry.Builder builder = SyntaxRegistry.builder()
            .experimentNames(co.xenastudios.neoskript.runtime.ScriptFeatures::codeName);
    private final Map<Key, Object> bindings = new HashMap<>();
    private final Map<String, Class<?>> javaClasses = new HashMap<>();
    /** Every type, in the order registered. */
    private final List<String> javaClassOrder = new ArrayList<>();
    /** The converters, as Skript looks them up to get event values. */
    private final Converters converters = new Converters();
    private final Map<FunctionInfo, Function> functions = new HashMap<>();
    private final Map<String, Object> literalValues = new HashMap<>();
    /** Each named type's values, in the order registered, without repeats: what 'all colours' gives. */
    private final Map<String, List<Object>> namedValues = new java.util.concurrent.ConcurrentHashMap<>();
    /** Named types in the order they were registered. */
    private final List<String> typeOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Each built-in function's parameters' values when left out, as declared. */
    private final Map<FunctionInfo, List<java.util.function.Supplier<?>>> functionDefaults = new HashMap<>();
    private final VariableCodecs codecs = new VariableCodecs();
    private final List<EventValue> eventValues = new ArrayList<>();
    /** Skript's events, in its order ({@link #eventName(String, List, String...)}). */
    private final List<EventName> eventNames = new ArrayList<>();
    /** The patterns of each server event, by its id. */
    private final Map<String, List<String>> eventPatterns = new HashMap<>();
    private final Map<String, java.util.function.Function<Object, Class<?>>> kindClasses = new HashMap<>();
    private final Set<String> eventDefaults = new HashSet<>();
    private final Map<String, String> sectionTypes = new HashMap<>();
    private final Map<String, String> sectionExpressions = new HashMap<>();
    private final Set<String> eventValueExpressions = new HashSet<>();
    private final Map<String, String> sectionEvents = new HashMap<>();
    /** Event classes and types whose values' time states are only for the type's bare name, as {@code "class type"}. */
    private final Set<String> bareTimeStates = new HashSet<>();
    /** What says where an expression has time states, by its id. */
    private final Map<String, java.util.function.Predicate<co.xenastudios.neoskript.api.element.ElementInfo>> timeStates = new HashMap<>();
    private final java.util.Set<String> deferredSections = new java.util.HashSet<>();
    private final Map<String, java.util.function.Function<String, ?>> parsers = new HashMap<>();
    private final Map<String, java.util.function.Function<String, ?>> literalParsers = new java.util.LinkedHashMap<>();
    private SyntaxRegistry registry;
    private boolean testing;

    public Library() {
        type(SyntaxRegistry.OBJECT, "object", Object.class);
        type("timespan", "time span", Timespan.class);
        parser("timespan", Timespan::parse);
        type("date", "date", co.xenastudios.neoskript.runtime.Date.class);
        type("time", "time", co.xenastudios.neoskript.runtime.TimeOfDay.class);
        parser("time", co.xenastudios.neoskript.runtime.TimeOfDay::parse);
        literalParser("time", co.xenastudios.neoskript.runtime.TimeOfDay::parse);
        type("script", "script", co.xenastudios.neoskript.runtime.ScriptRef.class);
        CoreSyntax.register(this);
        CoreCodecs.register(this);
    }

    @Override
    public void type(String id, String name, Class<?> javaClass) {
        checkOpen();
        builder.type(new TypeInfo(id, name, Set.of(), false));
        putJavaClass(id, javaClass);
        co.xenastudios.neoskript.runtime.TypeNames.register(javaClass, name);
        if (STRICT_TYPES.contains(id)) {
            builder.strict(id);
        }
    }

    private void putJavaClass(String id, Class<?> javaClass) {
        if (javaClasses.put(id, javaClass) == null) {
            javaClassOrder.add(id);
        }
    }

    @Override
    public <T> void namedType(String id, String name, Class<T> javaClass, Map<String, ? extends T> values) {
        checkOpen();
        builder.type(new TypeInfo(id, name, values.keySet(), false));
        if (STRICT_TYPES.contains(id)) {
            builder.strict(id);
        }
        putJavaClass(id, javaClass);
        co.xenastudios.neoskript.runtime.TypeNames.register(javaClass, name);
        // A word several types have is the first's where nothing says which.
        values.forEach(literalValues::putIfAbsent);
        namedValues.put(id, List.copyOf(new java.util.LinkedHashSet<>(values.values())));
        if (!typeOrder.contains(id)) {
            typeOrder.add(id);
        }
        Map<String, T> copy = Map.copyOf(values);
        parsers.put(id, text -> copy.get(text.strip().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public void allValues(String id, java.util.function.Supplier<? extends Iterable<?>> values) {
        checkOpen();
        if (!javaClasses.containsKey(id)) {
            throw new IllegalArgumentException("Values of unknown type '" + id + "'");
        }
        builder.enumerable(id);
        allValues.put(id, () -> {
            List<Object> all = new ArrayList<>();
            values.get().forEach(all::add);
            return all.toArray();
        });
    }

    /** What lists every value of type {@code id} when scripts run, or {@code null}: see {@link #allValues}. */
    java.util.function.Supplier<Object[]> allValues(String id) {
        return allValues.get(id);
    }

    private final Map<String, java.util.function.Supplier<Object[]>> allValues = new java.util.HashMap<>();

    /** Every value of the named type {@code id}, or {@code null} if it isn't a named type. */
    public List<Object> namedValues(String id) {
        return namedValues.get(id);
    }

    @Override
    public void alias(String typeId, String singular, String plural) {
        checkOpen();
        builder.alias(typeId, singular, plural);
    }

    @Override
    public void subtype(String sub, String supertype) {
        checkOpen();
        builder.assignable(sub, supertype);
    }

    @Override
    public void typedExpression(String id, String returnType, ElementFactory<Expression> factory, String... patterns) {
        int groups = 0;
        for (String pattern : patterns) {
            groups = Math.max(groups, (int) pattern.chars().filter(c -> c == '<').count());
        }
        add(SyntaxKind.EXPRESSION, id, returnType, factory, java.util.Collections.nCopies(groups, RegexRole.TYPE), patterns);
    }

    @Override
    public void sectionExpression(String expressionId, String valueType) {
        checkOpen();
        sectionExpressions.put(expressionId, valueType);
    }

    @Override
    public void sectionEvent(String expressionId, String eventName) {
        checkOpen();
        sectionEvents.put(expressionId, eventName);
    }

    /** What the event of expression {@code id}'s section is called, or {@code null} if it has none of its own. */
    String sectionEvent(String id) {
        return sectionEvents.get(id);
    }

    @Override
    public void eventValueExpression(String expressionId) {
        checkOpen();
        eventValueExpressions.add(expressionId);
    }

    /** Whether expression {@code id} is an event value ({@link #eventValueExpression(String)}). */
    boolean isEventValueExpression(String id) {
        return eventValueExpressions.contains(id);
    }

    @Override
    public void experimental(String id, String experiment, boolean silent) {
        checkOpen();
        builder.experimental(id, experiment, silent);
    }

    @Override
    public void effectBefore(String effectId, String otherId) {
        checkOpen();
        builder.before(SyntaxKind.EFFECT, effectId, otherId);
    }

    @Override
    public void deferredSectionExpression(String expressionId) {
        checkOpen();
        sectionExpressions.put(expressionId, SyntaxRegistry.OBJECT);
        deferredSections.add(expressionId);
        builder.sectionOnly(expressionId);
    }

    @Override
    public void lateSyntax() {
        if (!hasTimeStateSyntax()) {
            CoreSyntax.registerTimeStates(this);
        }
    }

    /** Whether the time state syntax ('past X') is registered yet. */
    boolean hasTimeStateSyntax() {
        return bindings.containsKey(new Key(SyntaxKind.EXPRESSION, CoreSyntax.TIME_STATE));
    }

    @Override
    public void timeStates(String expressionId, java.util.function.Predicate<co.xenastudios.neoskript.api.element.ElementInfo> accepts) {
        checkOpen();
        timeStates.put(expressionId, accepts);
        builder.timeStates(expressionId);
    }

    /**
     * Whether expression {@code id}, matched as {@code info} says, has the time state {@code info.time()}, as
     * Skript's {@code setTime}.
     */
    boolean acceptsTime(String id, co.xenastudios.neoskript.api.element.ElementInfo info) {
        java.util.function.Predicate<co.xenastudios.neoskript.api.element.ElementInfo> accepts = timeStates.get(id);
        return accepts != null && accepts.test(info);
    }

    /** Whether expression {@code id}'s section runs later, its value being the section itself. */
    boolean deferredSection(String id) {
        return deferredSections.contains(id);
    }

    /** The type of value expression {@code id} runs a section for, or {@code null} if it doesn't take one. */
    String sectionExpressionType(String id) {
        return sectionExpressions.get(id);
    }

    @Override
    public void effect(String id, ElementFactory<Effect> factory, String... patterns) {
        add(SyntaxKind.EFFECT, id, null, factory, patterns);
    }

    @Override
    public void condition(String id, ElementFactory<Condition> factory, String... patterns) {
        add(SyntaxKind.CONDITION, id, null, factory, patterns);
    }

    @Override
    public void section(String id, String valueType, ElementFactory<co.xenastudios.neoskript.api.element.Section> factory,
            String... patterns) {
        if (!javaClasses.containsKey(valueType)) {
            throw new IllegalArgumentException("Section " + id + " runs for unknown type '" + valueType + "'");
        }
        sectionTypes.put(id, valueType);
        add(SyntaxKind.SECTION, id, null, factory, patterns);
        add(SyntaxKind.EFFECT, id, null, (ElementFactory<Effect>) info -> {
            co.xenastudios.neoskript.api.element.Section section = factory.create(info);
            return (context, arguments) -> section.run(context, arguments);
        }, patterns);
    }

    /** The type of the values a library section's code runs for, or {@code null} if {@code id} isn't one. */
    String sectionType(String id) {
        return sectionTypes.get(id);
    }

    @Override
    public void expression(String id, String returnType, ElementFactory<Expression> factory, String... patterns) {
        if (!javaClasses.containsKey(returnType)) {
            throw new IllegalArgumentException("Expression " + id + " returns unknown type '" + returnType + "'");
        }
        add(SyntaxKind.EXPRESSION, id, returnType, factory, patterns);
    }

    @Override
    public void event(String id, ScriptEvent event, String... patterns) {
        add(SyntaxKind.EVENT, id, null, event, patterns);
    }

    @Override
    public void event(String id, List<Class<?>> eventClasses, ElementFactory<Condition> filter, String... patterns) {
        if (eventClasses.isEmpty()) {
            throw new IllegalArgumentException("Event " + id + " has no event classes");
        }
        add(SyntaxKind.EVENT, id, null, new ServerEvent(List.copyOf(eventClasses), filter), patterns);
        eventPatterns.put(id, List.of(patterns));
    }

    @Override
    public void eventName(String name, List<Class<?>> eventClasses, String... patterns) {
        checkOpen();
        eventNames.add(new EventName(name, List.copyOf(eventClasses), List.of(patterns)));
    }

    /**
     * What Skript calls the event of a trigger of the server event {@code id} in errors, its name in lower case
     * ({@code on break / mine}): that of Skript's event with the same patterns, or {@code null} if none has them.
     */
    public String eventName(String id) {
        EventName event = skriptEvent(id);
        return event == null ? null : event.name().toLowerCase(java.util.Locale.ENGLISH);
    }

    /** Skript's event that the server event {@code id} is, as {@link #eventName(String)} finds it, or {@code null}. */
    private EventName skriptEvent(String id) {
        List<String> patterns = eventPatterns.get(id);
        if (patterns == null) {
            return null;
        }
        patterns = patterns.stream().map(Library::bare).toList();
        for (EventName event : eventNames) {
            if (event.patterns().stream().map(Library::bare).toList().equals(patterns)) {
                return event;
            }
        }
        // Where NeoSkript splits or joins Skript's patterns, any shared pattern says which it is.
        for (EventName event : eventNames) {
            for (String pattern : patterns) {
                if (event.patterns().stream().map(Library::bare).anyMatch(pattern::equals)) {
                    return event;
                }
            }
        }
        // Else, where its patterns are written otherwise, the only event of Skript's with the same classes.
        if (bindings.get(new Key(SyntaxKind.EVENT, id)) instanceof ServerEvent server) {
            Set<Class<?>> classes = Set.copyOf(server.classes());
            EventName found = null;
            for (EventName event : eventNames) {
                if (Set.copyOf(event.classes()).equals(classes)) {
                    if (found != null) {
                        return null;
                    }
                    found = event;
                }
            }
            return found;
        }
        return null;
    }

    /** A pattern without the flags of its placeholders ({@code %-*itemtypes%}), which NeoSkript's can leave out. */
    private static String bare(String pattern) {
        return pattern.replaceAll("%[-*~]+", "%");
    }

    /**
     * Skript's {@code supportedEventsNames} for syntax that only works in events of the classes named
     * {@code supported}: each of Skript's events with a class one of them is, or is a subclass of, as
     * {@code the on command event}, once for each such pair, joined with commas and a last {@code or}. Classes
     * no event of Skript's has are named none.
     */
    String eventNames(String... supported) {
        List<String> names = new ArrayList<>();
        for (EventName event : eventNames) {
            for (String wanted : supported) {
                Class<?> wantedClass = eventClass(wanted);
                if (wantedClass == null) {
                    continue;
                }
                for (Class<?> eventClass : event.classes()) {
                    if (eventClass.isAssignableFrom(wantedClass)) {
                        names.add("the " + event.name().toLowerCase(java.util.Locale.ENGLISH) + " event");
                    }
                }
            }
        }
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
    }

    /** The class of one of Skript's events called {@code name}, or {@code null}. */
    private Class<?> eventClass(String name) {
        for (EventName event : eventNames) {
            for (Class<?> eventClass : event.classes()) {
                if (eventClass.getName().equals(name)) {
                    return eventClass;
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void eventValue(Class<E> eventClass, String typeId, java.util.function.Function<? super E, ?> getter) {
        eventValue(eventClass, typeId, 0, getter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void eventValue(Class<E> eventClass, String typeId, int time,
            java.util.function.Function<? super E, ?> getter) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Event value of unknown type '" + typeId + "'");
        }
        if (time < -1 || time > 1) {
            throw new IllegalArgumentException("Event value time must be -1, 0 or 1, not " + time);
        }
        eventValues.add(new EventValue(eventClass, typeId, time, null, (java.util.function.Function<Object, ?>) getter,
                null));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void eventValue(Class<E> eventClass, String typeId, Class<?> valueClass, int time,
            java.util.function.Function<? super E, ?> getter) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Event value of unknown type '" + typeId + "'");
        }
        eventValues.add(new EventValue(eventClass, typeId, time, valueClass,
                (java.util.function.Function<Object, ?>) getter, null));
    }

    @Override
    public void kindClass(String typeId, java.util.function.Function<Object, Class<?>> classOf) {
        checkOpen();
        kindClasses.put(typeId, classOf);
    }

    /**
     * The class of what {@code words} name as a literal of {@code typeId}, like the entity class of
     * {@code projectile}, or {@code null}.
     */
    Class<?> kindClass(String typeId, String words) {
        java.util.function.Function<Object, Class<?>> classOf = kindClasses.get(typeId);
        java.util.function.Function<String, ?> parser = literalParsers.get(typeId);
        if (classOf == null || parser == null) {
            return null;
        }
        try {
            Object kind = parser.apply(words.strip());
            return kind == null ? null : classOf.apply(kind);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void eventValueExcluded(Class<?> eventClass, String typeId, String message) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Event value of unknown type '" + typeId + "'");
        }
        eventValues.add(new EventValue(eventClass, typeId, 0, null, null, message));
    }

    @Override
    public void parser(String typeId, java.util.function.Function<String, ?> parser) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Parser for unknown type '" + typeId + "'");
        }
        parsers.put(typeId, parser);
        co.xenastudios.neoskript.runtime.Parsers.register(typeId, parser);
        builder.parses(typeId, words -> parser.apply(words) != null);
    }

    private final Map<String, Describer> describers = new HashMap<>();

    @Override
    public void describe(String id, Describer describer) {
        checkOpen();
        describers.put(id, describer);
    }

    /** How Skript writes syntax {@code id} in messages, or {@code null} for as written: see {@link #describe}. */
    Describer describer(String id) {
        return describers.get(id);
    }

    private final List<java.util.function.Function<String, String>> literalErrors = new ArrayList<>();

    @Override
    public void literalError(java.util.function.Function<String, String> problem) {
        checkOpen();
        literalErrors.add(problem);
    }

    /** What's wrong with words no literal parser understands, or {@code null}: see {@link #literalError}. */
    String literalError(String words) {
        for (java.util.function.Function<String, String> problem : literalErrors) {
            String error = problem.apply(words.strip());
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    @Override
    public void literalParser(String typeId, java.util.function.Function<String, ?> parser) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Literals of unknown type '" + typeId + "'");
        }
        literalParsers.put(typeId, parser);
        builder.openLiterals(typeId, words -> parser.apply(words.strip()) != null);
    }

    /**
     * The value of words written where a value was expected, like {@code stone sword}: a named value
     * ({@code creative}), or the first type whose literal parser accepts them. {@code null} if none does.
     */
    Object literal(String words) {
        Object named = literalValue(words);
        if (named != null) {
            return named;
        }
        for (java.util.function.Function<String, ?> parser : literalParsers.values()) {
            Object value = parser.apply(words.strip());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * The named types (like heal reasons) whose parser takes {@code words}, in the order they were registered,
     * for Skript's warning about a word with several types.
     */
    List<String> namedTypesOf(String words) {
        List<String> types = new ArrayList<>();
        for (String id : namedValues.keySet()) {
            java.util.function.Function<String, ?> parser = parsers.get(id);
            try {
                if (parser != null && parser.apply(words.strip()) != null) {
                    types.add(id);
                }
            } catch (RuntimeException ignored) {
                // A parser that needs a server isn't asked.
            }
        }
        types.sort(java.util.Comparator.comparingInt(typeOrder::indexOf));
        return types;
    }

    /** The literal parser of {@code typeId}, or {@code null}. */
    java.util.function.Function<String, ?> literalParser(String typeId) {
        return literalParsers.get(typeId);
    }

    /** How text becomes a value of {@code typeId}, or {@code null} if it can't. */
    java.util.function.Function<String, ?> parser(String typeId) {
        return parsers.get(typeId);
    }

    @Override
    public void eventDefault(String typeId) {
        checkOpen();
        eventDefaults.add(typeId);
    }

    /** The class of the event value a left-out placeholder of a type gets, where it isn't the type's own. */
    private final Map<String, Class<?>> eventDefaultClasses = new HashMap<>();

    @Override
    public void eventDefault(String typeId, Class<?> valueClass) {
        eventDefault(typeId);
        eventDefaultClasses.put(typeId, valueClass);
    }

    /**
     * Skript's default expression of {@code typeId} where its type's default is the event's value: how to get it
     * from events of {@code eventClasses}, or the error, about {@code eventDescription}.
     */
    EventValueLookup eventDefault(List<Class<?>> eventClasses, String typeId, String eventDescription) {
        Class<?> valueClass = eventDefaultClasses.getOrDefault(typeId, javaClass(typeId));
        return eventValue(eventClasses, valueClass, valueClass == javaClass(typeId) ? typeId : null, 0,
                eventDescription);
    }

    private final Map<String, Object> defaultValues = new HashMap<>();

    @Override
    public void defaultValue(String typeId, Object value) {
        checkOpen();
        defaultValues.put(typeId, value);
    }

    /** The value a left-out placeholder of {@code typeId} gets when nothing else gives one, or {@code null}. */
    Object defaultValue(String typeId) {
        return defaultValues.get(typeId);
    }

    /** Whether unused optional placeholders of {@code typeId} get the event's value of that type. */
    boolean hasEventDefault(String typeId) {
        return eventDefaults.contains(typeId);
    }

    /**
     * How to get the value of {@code typeId} from events of {@code eventClasses}, as Skript's event value
     * expression of the type's class does, or {@code null} if they have none (or leave it out on purpose).
     */
    java.util.function.Function<Object, Object> eventValue(List<Class<?>> eventClasses, String typeId) {
        return eventDefault(eventClasses, typeId, null).getter();
    }

    /**
     * What an event value asks for: values of {@code valueClass}, of the type {@code type} where that's the type
     * named. {@code words} is the kind of entity an {@code event-zombie} names, else {@code null}.
     */
    record EventValueRequest(Class<?> valueClass, String type, String words) {}

    /**
     * What an event value of the type {@code ref}, written as {@code text} ({@code event-zombie}, or {@code null}
     * if unknown), asks for: the type's class, an array of it for a plural ({@code event-blocks}), or for a kind
     * of entity, its class, as Skript's {@code ExprEntity}.
     */
    EventValueRequest request(co.xenastudios.neoskript.frontend.registry.TypeRef ref, String text) {
        String typeId = ref.type().id();
        Class<?> single = javaClass(typeId);
        if (ref.plural()) {
            return single.isArray() ? new EventValueRequest(single, typeId, null)
                    : new EventValueRequest(single.arrayType(), null, null);
        }
        if (!typeId.equals("entity") || text == null) {
            return new EventValueRequest(single, typeId, null);
        }
        String words = text.strip().toLowerCase(java.util.Locale.ROOT);
        boolean prefixed = false;
        for (String prefix : new String[] {"the ", "event-"}) {
            if (words.startsWith(prefix)) {
                words = words.substring(prefix.length()).strip();
                prefixed |= prefix.equals("event-");
            }
        }
        if (registry().resolveType(words) != null) {
            return new EventValueRequest(single, typeId, null);
        }
        Class<?> kind = kindClass("entitytype", words);
        return kind == null || kind == single ? new EventValueRequest(single, typeId, null)
                : new EventValueRequest(kind, null, prefixed ? words : null);
    }

    /** What looking for an event value found: how to get it, or else Skript's error for asking for it. */
    record EventValueLookup(java.util.function.Function<Object, Object> getter, String error) {}

    /** What Skript's event values are resolved from: the registered values, by their classes, and converters. */
    private synchronized EventValueResolver resolver() {
        if (resolver == null) {
            List<EventValueResolver.Value> values = new ArrayList<>();
            for (EventValue value : eventValues) {
                Class<?> valueClass = value.valueClass() != null ? value.valueClass() : javaClass(value.type());
                values.add(new EventValueResolver.Value(value.eventClass(), valueClass,
                        value.valueClass() == null ? value.type() : null, value.time(), value.getter(), value.excluded()));
            }
            resolver = new EventValueResolver(List.copyOf(values), converters, this::exactType);
        }
        return resolver;
    }

    private EventValueResolver resolver;

    /** The type whose values are exactly of {@code javaClass}, the first registered of several, or {@code null}. */
    String exactType(Class<?> javaClass) {
        for (String type : typeOrder()) {
            if (javaClasses.get(type) == javaClass) {
                return type;
            }
        }
        return null;
    }

    /**
     * The type of the closest superclass of {@code javaClass} (or of the class itself), as Skript's
     * {@code getSuperClassInfo}: what Skript's errors call a value of the class.
     */
    String superType(Class<?> javaClass) {
        String exact = exactType(javaClass);
        if (exact != null) {
            return exact;
        }
        String best = null;
        for (String type : typeOrder()) {
            Class<?> candidate = javaClasses.get(type);
            if (candidate == null || candidate == Object.class || !candidate.isAssignableFrom(javaClass)) {
                continue;
            }
            if (best == null || javaClasses.get(best).isAssignableFrom(candidate)) {
                best = type;
            }
        }
        return best != null ? best : SyntaxRegistry.OBJECT;
    }

    /** Every type, in the order registered. */
    private List<String> typeOrder() {
        return javaClassOrder;
    }

    /**
     * Skript's {@code EventValueExpression.init}, then its values: the event value of {@code valueClass} (of the
     * type {@code type}, if given) at {@code time} in events of {@code eventClasses}. Each event class gets the value
     * resolved for it, as in Skript; one having it is enough. Else Skript's error for it, about
     * {@code eventDescription} ({@code an on join event}); without a description, just that there's none.
     */
    /** Errors of excluded event values the parser used up, by event classes, class and type: see {@link #parserAsked}. */
    private final Map<String, String> parserAsked = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The parser used up the error of an excluded event value ({@code Use 'attacker' and/or 'victim'...}), as Skript's
     * event value does when it's set up: the compiler asking for it next gets it, once ({@link #takeParserAsked}).
     */
    void parserAsked(List<Class<?>> eventClasses, Class<?> valueClass, String type, String error) {
        parserAsked.put(eventClasses + "/" + valueClass.getName() + "/" + type, error);
    }

    /** The error {@link #parserAsked} kept for this value, now taken, or {@code null}. */
    String takeParserAsked(List<Class<?>> eventClasses, Class<?> valueClass, String type) {
        return parserAsked.remove(eventClasses + "/" + valueClass.getName() + "/" + type);
    }

    EventValueLookup eventValue(List<Class<?>> eventClasses, Class<?> valueClass, String type, int time,
            String eventDescription) {
        EventValueResolver resolver = resolver();
        // Without a description, it's the parser checking: an excluded value's error is left for the code asking.
        boolean consume = eventDescription != null;
        String error = null;
        boolean hasValue = false;
        List<Map.Entry<Class<?>, java.util.function.Function<Object, Object>>> getters = new ArrayList<>();
        for (Class<?> eventClass : eventClasses) {
            EventValueResolver.Resolution exact = resolver.lookUp(consume, eventClass, valueClass, type, time,
                    EventValueResolver.DEFAULT & ~EventValueResolver.CONVERSION);
            error = error != null ? error : exact.error();
            if (exact.multiple()) {
                return new EventValueLookup(null, error != null ? error : eventDescription == null ? "multiple"
                        : "There are multiple " + input(valueClass, type, true) + " in " + eventDescription
                                + ". You must define which " + input(valueClass, type, false) + " to use.");
            }
            EventValueResolver.Resolution resolution = resolver.lookUp(consume, eventClass, valueClass, type, time,
                    EventValueResolver.DEFAULT);
            error = error != null ? error : resolution.error();
            hasValue |= resolution.successful();
            getters.add(Map.entry(eventClass, resolution.successful() ? resolution.all().getFirst().getter()
                    : event -> null));
        }
        if (!hasValue) {
            if (eventDescription == null) {
                return new EventValueLookup(null, error != null ? error : "none");
            }
            // As Skript suggests: 'event-block' where the event has several, 'event-blocks' where it has one.
            Class<?> suggested = valueClass.isArray() ? valueClass.componentType() : valueClass.arrayType();
            String suggestedType = valueClass.isArray() ? exactType(suggested) == null ? null : type(suggested, type)
                    : null;
            boolean suggestedExists = false;
            for (Class<?> eventClass : eventClasses) {
                EventValueResolver.Resolution exact = resolver.lookUp(consume, eventClass, suggested, suggestedType, 0,
                        EventValueResolver.DEFAULT & ~EventValueResolver.CONVERSION);
                error = error != null ? error : exact.error();
                if (exact.multiple()) {
                    continue;
                }
                EventValueResolver.Resolution resolution = resolver.lookUp(consume, eventClass, suggested, suggestedType, 0,
                        EventValueResolver.DEFAULT);
                error = error != null ? error : resolution.error();
                if (resolution.successful()) {
                    suggestedExists = true;
                    break;
                }
            }
            if (error != null) {
                return new EventValueLookup(null, error);
            }
            String message;
            if (suggestedExists) {
                message = (suggested.isArray() ? "There are multiple " + input(suggested, suggestedType, true)
                        : "There's only one " + input(suggested, suggestedType, false)) + " in " + eventDescription
                        + ". Did you mean 'event-" + input(suggested, suggestedType, suggested.isArray()) + "'?";
            } else {
                boolean single = !valueClass.isArray();
                message = "There" + (single ? "'s" : " are") + " no " + input(valueClass, type, !single) + " in "
                        + eventDescription + ".";
            }
            return new EventValueLookup(null, message);
        }
        if (getters.size() == 1) {
            return new EventValueLookup(getters.getFirst().getValue(), null);
        }
        // As Skript: an event gets the value resolved for the first of the classes it's one of.
        return new EventValueLookup(event -> {
            for (Map.Entry<Class<?>, java.util.function.Function<Object, Object>> getter : getters) {
                if (getter.getKey().isInstance(event)) {
                    return getter.getValue().apply(event);
                }
            }
            return null;
        }, null);
    }

    /** {@code type} where it's the type of {@code javaClass}'s values, else that class's own type. */
    private String type(Class<?> javaClass, String type) {
        return type != null && javaClasses.get(type) == javaClass ? type : exactType(javaClass);
    }

    /** What Skript's event value errors call values of {@code valueClass}: {@code block}, {@code blocks}. */
    private String input(Class<?> valueClass, String type, boolean plural) {
        Class<?> component = valueClass.isArray() ? valueClass.componentType() : valueClass;
        String id = type != null && javaClasses.get(type) == component ? type : superType(component);
        co.xenastudios.neoskript.frontend.registry.TypeInfo info = registry().type(id);
        String name = info == null ? id : info.name();
        return plural ? SyntaxRegistry.plural(name) : name;
    }

    /**
     * Whether any of {@code eventClasses} has a past or future value of {@code valueClass}, as Skript's event value
     * expressions need to take a time state.
     */
    @Override
    public void bareTimeStates(Class<?> eventClass, String typeId) {
        checkOpen();
        bareTimeStates.add(eventClass.getName() + " " + typeId);
    }

    /** Whether the time states of values of {@code typeId} in events of {@code eventClasses} are only for its bare name. */
    boolean bareTimeStatesOnly(List<Class<?>> eventClasses, String typeId) {
        for (Class<?> eventClass : eventClasses) {
            if (bareTimeStates.contains(eventClass.getName() + " " + typeId)) {
                return true;
            }
        }
        return false;
    }

    boolean hasTimeStates(List<Class<?>> eventClasses, Class<?> valueClass, String type) {
        EventValueResolver resolver = resolver();
        for (Class<?> eventClass : eventClasses) {
            for (int time : new int[] {-1, 1}) {
                if (resolver.resolve(eventClass, valueClass, type, time, EventValueResolver.CONVERSION).successful()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void function(String name, List<Parameter> parameters, String returnType, boolean returnsPlural,
            Function function) {
        checkOpen();
        FunctionInfo info = new FunctionInfo(name, parameters.stream()
                .map(p -> new FunctionInfo.Parameter(p.name(), p.type(), p.plural(), p.optional()))
                .toList(), returnType, returnsPlural);
        functionDefaults.put(info, parameters.stream().map(Parameter::defaultValue).toList());
        builder.function(info);
        functions.put(info, function);
    }

    /** The types Skript can't save in variables, by id, with their names as its warning writes them. */
    private final Map<String, String> unsavable = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void unsavable(String typeId, String named) {
        unsavable.put(typeId, named);
    }

    /** {@code typeId}'s name with its article, if Skript can't save values of it in variables, else {@code null}. */
    String unsavable(String typeId) {
        return typeId == null ? null : unsavable.get(typeId);
    }

    @Override
    public <T> void storage(String name, Class<T> type, VariableCodec<T> codec) {
        codecs.register(name, type, codec);
    }

    @Override
    public void unsavable(Class<?> type) {
        codecs.unsavable(type);
    }

    @Override
    public void typeProperty(String property, String... typeIds) {
        checkOpen();
        builder.typeProperty(property, typeIds);
    }

    @Override
    public void propertySyntax(String syntaxId, int argument, java.util.function.Function<Set<String>, String> property,
            boolean counts) {
        checkOpen();
        builder.propertySyntax(syntaxId, new co.xenastudios.neoskript.frontend.registry.SyntaxRegistry.PropertySyntax(
                argument, property, counts));
    }

    /** How variable values are saved, as registered with {@link #storage}. */
    public VariableCodecs codecs() {
        return codecs;
    }

    @Override
    public <F, T> void converter(Class<F> from, Class<T> to, java.util.function.Function<? super F, ? extends T> converter) {
        converter(from, to, converter, 0);
    }

    @Override
    public <F, T> void converter(Class<F> from, Class<T> to, java.util.function.Function<? super F, ? extends T> converter,
            int flags) {
        checkOpen();
        converters.register(from, to, converter, flags);
        if ((flags & EVENT_VALUES_ONLY) != 0) {
            return;
        }
        Values.registerConverter(from, to, converter);
        // What converts can be used where the other type is expected, even for strict types.
        for (Map.Entry<String, Class<?>> source : javaClasses.entrySet()) {
            if (source.getValue() != from) {
                continue;
            }
            for (Map.Entry<String, Class<?>> target : javaClasses.entrySet()) {
                if (target.getValue() == to) {
                    builder.convertible(source.getKey(), target.getKey());
                    if ((flags & PLACEHOLDERS_ONLY) != 0) {
                        builder.placeholderConversion(source.getKey(), target.getKey());
                    }
                }
            }
        }
    }

    @Override
    public <T> void changer(Class<T> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<? super T, Object[]> change) {
        checkOpen();
        Values.registerChanger(owner, mode, change);
    }

    /** What values of each class take when changed themselves, by mode: see {@link #changerAccepts}. */
    private final Map<Class<?>, Map<co.xenastudios.neoskript.api.element.Changeable.Mode, Class<?>[]>> changerAccepts =
            new java.util.LinkedHashMap<>();

    @Override
    public void changerAccepts(Class<?> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            Class<?>... accepted) {
        checkOpen();
        changerAccepts.computeIfAbsent(owner, o -> new java.util.EnumMap<>(
                co.xenastudios.neoskript.api.element.Changeable.Mode.class)).put(mode, accepted.clone());
    }

    /**
     * The class whose declared changer applies to values of {@code type}, as Skript's type's changer does: the
     * nearest class above it that declares what its values take. {@code null} if none does.
     */
    Class<?> changerOwner(Class<?> type) {
        Class<?> nearest = null;
        for (Class<?> owner : changerAccepts.keySet()) {
            if (owner.isAssignableFrom(type) && (nearest == null || nearest.isAssignableFrom(owner))) {
                nearest = owner;
            }
        }
        return nearest;
    }

    /** What values of {@code owner} take for {@code mode}, or {@code null} if they can't be changed that way. */
    Class<?>[] changerAccepts(Class<?> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        Map<co.xenastudios.neoskript.api.element.Changeable.Mode, Class<?>[]> modes = changerAccepts.get(owner);
        return modes == null ? null : modes.get(mode);
    }

    @Override
    public <T> void cloner(Class<T> type, java.util.function.UnaryOperator<T> cloner) {
        checkOpen();
        Values.registerCloner(type, cloner);
    }

    @Override
    public <A, B> void comparison(Class<A> left, Class<B> right, java.util.function.BiPredicate<? super A, ? super B> test) {
        checkOpen();
        co.xenastudios.neoskript.runtime.Comparison.registerEquality(left, right, test);
    }

    @Override
    public <A, B> void arithmetic(char operator, Class<A> left, Class<B> right,
            java.util.function.BiFunction<? super A, ? super B, ?> apply) {
        checkOpen();
        co.xenastudios.neoskript.runtime.Arithmetic.register(operator, left, right, apply);
    }

    @Override
    public <T> void arithmeticDefault(Class<T> type, java.util.function.Supplier<? extends T> value) {
        checkOpen();
        co.xenastudios.neoskript.runtime.Arithmetic.registerDefault(type, value);
    }

    @Override
    public <T> void text(Class<T> type, java.util.function.Function<? super T, String> toText) {
        Values.registerText(type, toText);
    }

    @Override
    public <T> void variableNameText(Class<T> type, java.util.function.Function<? super T, String> toText) {
        Values.registerNameText(type, toText);
    }

    /**
     * Turns on Skript's test syntax ({@code test "name":}, {@code assert}, {@code parse:} and
     * {@code last parse logs}), for running Skript's own tests. Call it before registering anything else.
     */
    public void enableTesting() {
        testing = true;
        CoreSyntax.registerTesting(this);
    }

    /**
     * Whether Skript's test syntax is on. Skript runs its tests at debug verbosity, so errors then describe
     * expressions as its debug text does ({@code {_item} (as java.lang.Object)}).
     */
    boolean testing() {
        return testing;
    }

    /** Marks core syntax as making the trigger wait. */
    void delays(SyntaxKind kind, String id) {
        builder.delays(kind, id);
    }

    /** Marks core syntax as always ending the trigger when matched by {@code pattern} (-1 for any). */
    void stops(SyntaxKind kind, String id, int pattern) {
        builder.stops(kind, id, pattern);
    }

    /** Marks core syntax as ending the section it's in, so code after it is unreachable. */
    void ends(SyntaxKind kind, String id) {
        builder.ends(kind, id);
    }

    /** Marks core syntax as suppressing or allowing warnings while parsing, by its tags. */
    void suppressesWarnings(SyntaxKind kind, String id) {
        builder.suppressesWarnings(kind, id);
    }

    /** Adds a parse-time check to core syntax. */
    void sectionCheck(SyntaxKind kind, String id, co.xenastudios.neoskript.frontend.registry.ParseCheck check) {
        builder.sectionCheck(kind, id, check);
    }

    void check(SyntaxKind kind, String id, co.xenastudios.neoskript.frontend.registry.ParseCheck check) {
        builder.check(kind, id, check);
    }

    /**
     * A core type whose values are the words in {@code literals}, which compile to {@code toValue} of the
     * word. Used for Skript's test-only {@code unknown}.
     */
    void literalType(String id, String name, Set<String> literals, java.util.function.Function<String, Object> toValue) {
        checkOpen();
        builder.type(new TypeInfo(id, name, literals, false));
        putJavaClass(id, Object.class);
        for (String literal : literals) {
            literalValues.put(literal, toValue.apply(literal));
        }
    }

    /** The value of a word literal of a {@link #literalType}, or {@code null} if it isn't one. */
    Object literalValue(String word) {
        return literalValues.get(word.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /** Registers core syntax the compiler implements itself. */
    void core(SyntaxKind kind, String id, String returnType, List<RegexRole> regexRoles, String... patterns) {
        add(kind, id, returnType, new Core(id), regexRoles, patterns);
    }

    private void add(SyntaxKind kind, String id, String returnType, Object binding, String... patterns) {
        add(kind, id, returnType, binding, List.of(), patterns);
    }

    private void add(SyntaxKind kind, String id, String returnType, Object binding, List<RegexRole> regexRoles,
            String... patterns) {
        checkOpen();
        if (bindings.putIfAbsent(new Key(kind, id), binding) != null) {
            throw new IllegalArgumentException("Duplicate " + kind.name().toLowerCase() + " id: " + id);
        }
        builder.syntax(new SyntaxInfo(kind, id, Arrays.stream(patterns).map(PatternCompiler::compile).toList(),
                returnType, regexRoles));
    }

    /** The ids of the types whose values are of one of {@code classes}, most specific first: for reading words. */
    public List<String> typesOf(Class<?>[] classes) {
        List<String> ids = new ArrayList<>();
        for (Class<?> wanted : classes) {
            if (wanted == Object.class) {
                continue;
            }
            javaClasses.forEach((id, type) -> {
                if (wanted.isAssignableFrom(type) && !ids.contains(id)) {
                    ids.add(id);
                }
            });
        }
        return ids;
    }

    /** The class values of a type have, or {@code Object} if the type is unknown. */
    public Class<?> javaClass(String typeId) {
        return javaClasses.getOrDefault(typeId, Object.class);
    }

    /** The implementation of a built-in function, or {@code null} if {@code function} isn't one. */
    Function builtIn(FunctionInfo function) {
        return functions.get(function);
    }

    /** What a built-in function's parameter {@code index} is when left out, or {@code null} for nothing. */
    java.util.function.Supplier<?> functionDefault(FunctionInfo function, int index) {
        List<java.util.function.Supplier<?>> defaults = functionDefaults.get(function);
        return defaults == null || index >= defaults.size() ? null : defaults.get(index);
    }

    /** Every built-in function, as registered. */
    public Map<FunctionInfo, Function> builtIns() {
        return java.util.Collections.unmodifiableMap(functions);
    }

    /**
     * What's registered, for the syntax reference and the in-browser parser: every element with its patterns as
     * written, Skript's names for the events and their values, the types and the built-in functions, and what the
     * parser needs besides to check scripts as {@link #registry()} does. Freezes the library, as
     * {@link #registry()} does.
     */
    public SyntaxExport export(String neoskriptVersion, String server) {
        SyntaxRegistry registry = registry();
        LibraryEventValues events = (LibraryEventValues) registry.eventValues();
        List<SyntaxExport.Scope> scopes = new ArrayList<>();
        Map<List<Class<?>>, Integer> scopeIndexes = new HashMap<>();
        java.util.function.Function<List<Class<?>>, Integer> scope = classes -> {
            Integer index = scopeIndexes.get(classes);
            if (index == null) {
                index = scopes.size();
                scopes.add(events.export(classes));
                scopeIndexes.put(List.copyOf(classes), index);
            }
            return index;
        };
        List<SyntaxExport.Element> elements = new ArrayList<>();
        for (SyntaxKind kind : SyntaxKind.values()) {
            for (SyntaxInfo syntax : registry.syntaxes(kind)) {
                List<SyntaxExport.EventValue> values = List.of();
                String name = null;
                int scopeIndex = -1;
                if (kind == SyntaxKind.EVENT) {
                    EventName event = skriptEvent(syntax.id());
                    name = event == null ? null : event.name();
                    if (bindings.get(new Key(kind, syntax.id())) instanceof ServerEvent serverEvent) {
                        values = eventValuesOf(serverEvent.classes());
                    }
                    List<Class<?>> classes = events.eventClasses(syntax);
                    scopeIndex = classes == null ? -1 : scope.apply(classes);
                }
                elements.add(new SyntaxExport.Element(
                        SyntaxExport.kindName(kind), syntax.id(),
                        syntax.patterns().stream().map(CompiledPattern::source)
                                .toList(),
                        kind == SyntaxKind.EXPRESSION ? syntax.returnType() : null, name, values,
                        elementParser(registry, events, syntax, scopeIndex)));
            }
        }
        List<TypeInfo> types = new ArrayList<>();
        registry.types().forEach(types::add);
        types.sort(java.util.Comparator.comparingInt((TypeInfo t) ->
                registry.typeOrder(t.id())).thenComparing(TypeInfo::id));
        Map<String, java.util.TreeSet<String>> names = new HashMap<>();
        Map<String, java.util.TreeSet<String>> pluralNames = new HashMap<>();
        registry.typeNames().forEach((typeName, ref) -> (ref.plural() ? pluralNames : names)
                .computeIfAbsent(ref.type().id(), id -> new java.util.TreeSet<>()).add(typeName));
        // The words tried on each type's parsers: every type's literals, and for an enum, its constants' names.
        java.util.Set<String> literalWords = new java.util.TreeSet<>();
        types.forEach(t -> literalWords.addAll(t.literals()));
        List<SyntaxExport.Type> typeList = types.stream()
                .map(t -> new SyntaxExport.Type(t.id(), t.name(),
                        SyntaxRegistry.plural(t.name()), t.literals().stream().sorted().toList(),
                        new SyntaxExport.TypeParser(
                                List.copyOf(names.getOrDefault(t.id(), new java.util.TreeSet<>())),
                                List.copyOf(pluralNames.getOrDefault(t.id(), new java.util.TreeSet<>())),
                                registry.supertypes(t.id()).stream().sorted().toList(),
                                registry.isStrict(t.id()), registry.isEnumerable(t.id()), t.openLiterals(),
                                hasEventDefault(t.id()), defaultValue(t.id()) != null,
                                takesAnyWords(word -> registry.parses(t, word)) ? List.of()
                                        : acceptedWords(literalWords, t, word -> !t.literals().contains(word)
                                                && registry.parses(t, word)),
                                takesAnyWords(word -> registry.parses(t, word)),
                                !t.openLiterals() || takesAnyWords(word -> registry.isLiteral(t, word)) ? List.of()
                                        : acceptedWords(literalWords, t, word -> registry.isLiteral(t, word)))))
                .toList();
        List<SyntaxExport.Function> functionList = functions.keySet().stream()
                .sorted(java.util.Comparator.comparing(FunctionInfo::name)
                        .thenComparingInt(f -> f.parameters().size()))
                .map(f -> new SyntaxExport.Function(f.name(), f.parameters(),
                        f.returnType(), f.returnsPlural()))
                .toList();
        List<SyntaxExport.Conversion> conversions = new ArrayList<>();
        new java.util.TreeMap<>(registry.conversions()).forEach((from, targets) -> targets.stream().sorted()
                .forEach(to -> conversions.add(new SyntaxExport.Conversion(from, to,
                        registry.isPlaceholderConversion(from, to)))));
        Map<String, List<String>> properties = new java.util.TreeMap<>();
        registry.typeProperties().forEach((property, owners) -> properties.put(property,
                owners.stream().sorted().toList()));
        int commandScope = scope.apply(events.commandClasses());
        int functionScope = scope.apply(List.of());
        return new SyntaxExport(
                SyntaxExport.FORMAT, neoskriptVersion, server, elements,
                typeList, functionList, new SyntaxExport.Parser(conversions, properties,
                        new java.util.TreeMap<>(co.xenastudios.neoskript.runtime.ScriptFeatures.experiments()),
                        scopes, commandScope, functionScope));
    }

    /** Whether {@code accepts} takes a made-up word, as the parsers of text and player names do. */
    private static boolean takesAnyWords(java.util.function.Predicate<String> accepts) {
        try {
            return accepts.test("zqxvjk");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * The words of {@code tried}, and the names of {@code type}'s constants if its values are an enum's, that
     * {@code accepts} takes, for {@link #export}.
     */
    private List<String> acceptedWords(Set<String> tried, TypeInfo type, java.util.function.Predicate<String> accepts) {
        java.util.Set<String> words = new java.util.TreeSet<>(tried);
        Object[] constants = javaClass(type.id()).getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                words.add(((Enum<?>) constant).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
            }
        }
        java.util.Set<String> accepted = new java.util.TreeSet<>();
        for (String word : words) {
            try {
                if (accepts.test(word)) {
                    // As the browser's registry looks words up.
                    accepted.add(word.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " "));
                }
            } catch (RuntimeException | LinkageError e) {
                // A parser that can't run here doesn't take it.
            }
        }
        return List.copyOf(accepted);
    }

    /** What the parser knows about {@code syntax} besides its patterns, for {@link #export}. */
    private SyntaxExport.ElementParser elementParser(SyntaxRegistry registry, LibraryEventValues events,
            SyntaxInfo syntax, int scope) {
        List<Integer> stops = new ArrayList<>();
        for (int i = 0; i < syntax.patterns().size(); i++) {
            if (registry.stops(syntax, i)) {
                stops.add(i);
            }
        }
        SyntaxRegistry.Experimental experimental = registry.experimental(syntax);
        SyntaxRegistry.PropertySyntax property = registry.propertySyntax(syntax);
        SyntaxExport.Property exported = null;
        if (property != null) {
            // The property is a function of the tags matched: write down what each tag the patterns have gives.
            String otherwise = property.property().apply(Set.of());
            Map<String, String> byTag = new java.util.LinkedHashMap<>();
            for (CompiledPattern pattern : syntax.patterns()) {
                for (String tag : tags(pattern.root(), new java.util.LinkedHashSet<>())) {
                    String named = property.property().apply(Set.of(tag));
                    if (!java.util.Objects.equals(named, otherwise)) {
                        byTag.putIfAbsent(tag, named);
                    }
                }
            }
            exported = new SyntaxExport.Property(property.argument(), property.counts(), byTag, otherwise);
        }
        return new SyntaxExport.ElementParser(syntax.regexRoles().stream().map(RegexRole::name).toList(),
                registry.delays(syntax), stops, registry.ends(syntax), registry.suppressesWarnings(syntax),
                registry.sectionOnly(syntax), experimental == null ? null : experimental.experiment(),
                experimental != null && experimental.silent(), exported,
                binding(syntax) instanceof Core, events.isEventValue(syntax), events.keepsEventValues(syntax), scope);
    }

    /** The tags a pattern's alternatives are written with, in order. */
    private static Set<String> tags(co.xenastudios.neoskript.frontend.pattern.PatternElement element, Set<String> tags) {
        switch (element) {
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Sequence sequence ->
                    sequence.elements().forEach(e -> tags(e, tags));
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Choice choice -> {
                for (var alternative : choice.alternatives()) {
                    if (alternative.tag() != null) {
                        tags.add(alternative.tag());
                    }
                    tags(alternative.body(), tags);
                }
            }
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Optional optional -> tags(optional.choice(), tags);
            default -> { }
        }
        return tags;
    }

    /**
     * The event values registered for events of any of {@code eventClasses} or their superclasses, in the order
     * registered, each type and time once, without those the events are registered not to have.
     */
    private List<SyntaxExport.EventValue> eventValuesOf(
            List<Class<?>> eventClasses) {
        Set<String> excluded = new HashSet<>();
        for (EventValue value : eventValues) {
            if (value.excluded() != null && eventClasses.stream().allMatch(value.eventClass()::isAssignableFrom)) {
                excluded.add(value.type());
            }
        }
        java.util.LinkedHashSet<SyntaxExport.EventValue> values =
                new java.util.LinkedHashSet<>();
        for (EventValue value : eventValues) {
            if (value.excluded() != null || eventClasses.stream().noneMatch(value.eventClass()::isAssignableFrom)) {
                continue;
            }
            String type = value.type();
            if (value.valueClass() != null) {
                String exact = exactType(value.valueClass());
                type = exact != null ? exact : type;
            }
            if (!excluded.contains(type)) {
                values.add(new SyntaxExport.EventValue(type, value.time()));
            }
        }
        return List.copyOf(values);
    }

    private final Map<String, java.util.function.Function<co.xenastudios.neoskript.api.element.ElementInfo, String>> returnTypes =
            new java.util.HashMap<>();

    @Override
    public void returnType(String id, java.util.function.Function<co.xenastudios.neoskript.api.element.ElementInfo, String> type) {
        checkOpen();
        returnTypes.put(id, type);
    }

    /** An element as the parser matched it, for what describes it or says what it gives before it's set up. */
    private co.xenastudios.neoskript.api.element.ElementInfo parsedInfo(int pattern,
            co.xenastudios.neoskript.frontend.pattern.MatchResult match) {
        List<String> types = new ArrayList<>();
        List<String> literals = new ArrayList<>();
        for (Object value : match.values()) {
            String type = value instanceof co.xenastudios.neoskript.frontend.parse.Expr expr
                    && !(value instanceof co.xenastudios.neoskript.frontend.parse.Expr.Variable) ? expr.type() : null;
            types.add(type == null || type.equals(SyntaxRegistry.OBJECT) ? null : type);
            literals.add(switch (value) {
                case co.xenastudios.neoskript.frontend.parse.Expr.Literal literal -> literal.text();
                case co.xenastudios.neoskript.frontend.parse.Expr.Unresolved words -> words.text();
                case null, default -> null;
            });
        }
        List<co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument> typeArguments = new ArrayList<>();
        for (Object value : match.regexValues()) {
            if (value instanceof co.xenastudios.neoskript.frontend.registry.TypeRef ref) {
                typeArguments.add(new co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument(ref.type().id(),
                        ref.type().name(), ref.plural(), javaClass(ref.type().id())));
            }
        }
        return new co.xenastudios.neoskript.api.element.ElementInfo(pattern, match.mark(), match.tags(), 0, 0,
                typeArguments, types, singles(match), literals);
    }

    /** Which of {@code match}'s arguments are one value, as {@link co.xenastudios.neoskript.api.element.ElementInfo} has it. */
    private long singles(co.xenastudios.neoskript.frontend.pattern.MatchResult match) {
        long single = 0;
        for (int i = 0; i < match.values().size() && i < 64; i++) {
            if (match.values().get(i) instanceof co.xenastudios.neoskript.frontend.parse.Expr expr
                    && co.xenastudios.neoskript.frontend.parse.Plurality.single(expr, registry)) {
                single |= 1L << i;
            }
        }
        return single;
    }

    /** The syntax registry for the frontend. Nothing can be registered after this is called. */
    public synchronized SyntaxRegistry registry() {
        if (registry == null) {
            CoreSyntax.registerLast(this);
            // A type whose values' class is under another's is that type too, as Skript's class infos are by class:
            // an entity is a command sender, a player a living entity.
            javaClasses.forEach((sub, subClass) -> javaClasses.forEach((sup, supClass) -> {
                if (!sub.equals(sup) && subClass != Object.class && supClass != Object.class && supClass != subClass
                        && supClass.isAssignableFrom(subClass)) {
                    builder.assignable(sub, sup);
                }
            }));
            SkriptOrder.conversions(javaClasses.keySet(), builder);
            builder.describer((syntax, pattern, match, arguments) -> {
                Describer described = describers.get(syntax.id());
                return described == null ? null : described.describe(parsedInfo(pattern, match), arguments);
            });
            builder.typer((syntax, pattern, match) -> {
                var type = returnTypes.get(syntax.id());
                return type == null ? null : type.apply(parsedInfo(pattern, match));
            });
            for (Key key : bindings.keySet()) {
                if (key.kind() == SyntaxKind.EXPRESSION && SkriptOrder.anyType(key.id())) {
                    builder.anyType(key.id());
                }
            }
            for (SyntaxKind kind : SyntaxKind.values()) {
                builder.order(kind, syntaxes -> SkriptOrder.sort(kind, syntaxes));
            }
            registry = builder.eventValues(new LibraryEventValues(this)).build();
        }
        return registry;
    }

    /**
     * What a syntax element is bound to: an {@link ElementFactory}, or for events a {@link ScriptEvent} or
     * {@link ServerEvent}.
     */
    Object binding(SyntaxInfo syntax) {
        return bindings.get(new Key(syntax.kind(), syntax.id()));
    }

    private void checkOpen() {
        if (registry != null) {
            throw new IllegalStateException("The library is frozen: register everything before parsing scripts");
        }
    }
}
