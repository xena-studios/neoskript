/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.RegexRole;
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
                    "entityeffect");

    private record Key(SyntaxKind kind, String id) {}

    /** The binding of the core syntax the compiler implements itself: control flow, variables, arithmetic. */
    record Core(String id) {}

    /** The binding of a server event. */
    public record ServerEvent(List<Class<?>> classes, ElementFactory<Condition> filter) {}

    /**
     * A value of {@code type} events of {@code eventClass} have, at {@code time} (-1 past, 0 now, 1 future). If
     * {@code excluded} isn't {@code null}, the events don't have it, and that's the error for asking. A
     * {@code valueClass} makes it the value of only a narrower kind, like the projectile of a damage event, which
     * {@code event-projectile} gets; plain {@code event-entity} doesn't.
     */
    private record EventValue(Class<?> eventClass, String type, int time, Class<?> valueClass,
            java.util.function.Function<Object, ?> getter, String excluded) {}

    private final SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
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
    private final Map<String, java.util.function.Function<Object, Class<?>>> kindClasses = new HashMap<>();
    private final Set<String> eventDefaults = new HashSet<>();
    private final Map<String, String> sectionTypes = new HashMap<>();
    private final Map<String, String> sectionExpressions = new HashMap<>();
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
    EventValueLookup eventValue(List<Class<?>> eventClasses, Class<?> valueClass, String type, int time,
            String eventDescription) {
        EventValueResolver resolver = resolver();
        String error = null;
        boolean hasValue = false;
        List<Map.Entry<Class<?>, java.util.function.Function<Object, Object>>> getters = new ArrayList<>();
        for (Class<?> eventClass : eventClasses) {
            EventValueResolver.Resolution exact = resolver.resolve(eventClass, valueClass, type, time,
                    EventValueResolver.DEFAULT & ~EventValueResolver.CONVERSION);
            error = error != null ? error : exact.error();
            if (exact.multiple()) {
                return new EventValueLookup(null, error != null ? error : eventDescription == null ? "multiple"
                        : "There are multiple " + input(valueClass, type, true) + " in " + eventDescription
                                + ". You must define which " + input(valueClass, type, false) + " to use.");
            }
            EventValueResolver.Resolution resolution = resolver.resolve(eventClass, valueClass, type, time,
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
                EventValueResolver.Resolution exact = resolver.resolve(eventClass, suggested, suggestedType, 0,
                        EventValueResolver.DEFAULT & ~EventValueResolver.CONVERSION);
                error = error != null ? error : exact.error();
                if (exact.multiple()) {
                    continue;
                }
                EventValueResolver.Resolution resolution = resolver.resolve(eventClass, suggested, suggestedType, 0,
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

    /** The syntax registry for the frontend. Nothing can be registered after this is called. */
    public synchronized SyntaxRegistry registry() {
        if (registry == null) {
            CoreSyntax.registerLast(this);
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
