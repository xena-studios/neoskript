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
                    "skriptpotioneffect", "hiddenpotioneffect", "damagecause");

    private record Key(SyntaxKind kind, String id) {}

    /** The binding of the core syntax the compiler implements itself: control flow, variables, arithmetic. */
    record Core(String id) {}

    /** The binding of a server event. */
    public record ServerEvent(List<Class<?>> classes, ElementFactory<Condition> filter) {}

    private record EventValue(Class<?> eventClass, String type, java.util.function.Function<Object, ?> getter) {}

    private final SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
    private final Map<Key, Object> bindings = new HashMap<>();
    private final Map<String, Class<?>> javaClasses = new HashMap<>();
    private final Map<FunctionInfo, Function> functions = new HashMap<>();
    private final Map<String, Object> literalValues = new HashMap<>();
    /** Each named type's values, in the order registered, without repeats: what 'all colours' gives. */
    private final Map<String, List<Object>> namedValues = new java.util.concurrent.ConcurrentHashMap<>();
    /** Each built-in function's parameters' values when left out, as declared. */
    private final Map<FunctionInfo, List<java.util.function.Supplier<?>>> functionDefaults = new HashMap<>();
    private final VariableCodecs codecs = new VariableCodecs();
    private final List<EventValue> eventValues = new ArrayList<>();
    private final Set<String> eventDefaults = new HashSet<>();
    private final Map<String, String> sectionTypes = new HashMap<>();
    private final Map<String, String> sectionExpressions = new HashMap<>();
    private final Map<String, java.util.function.Function<String, ?>> parsers = new HashMap<>();
    private final Map<String, java.util.function.Function<String, ?>> literalParsers = new java.util.LinkedHashMap<>();
    private SyntaxRegistry registry;

    public Library() {
        type(SyntaxRegistry.OBJECT, "object", Object.class);
        type("timespan", "timespan", Timespan.class);
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
        javaClasses.put(id, javaClass);
        if (STRICT_TYPES.contains(id)) {
            builder.strict(id);
        }
    }

    @Override
    public <T> void namedType(String id, String name, Class<T> javaClass, Map<String, ? extends T> values) {
        checkOpen();
        builder.type(new TypeInfo(id, name, values.keySet(), false));
        if (STRICT_TYPES.contains(id)) {
            builder.strict(id);
        }
        javaClasses.put(id, javaClass);
        // A word several types have is the first's where nothing says which.
        values.forEach(literalValues::putIfAbsent);
        namedValues.put(id, List.copyOf(new java.util.LinkedHashSet<>(values.values())));
        Map<String, T> copy = Map.copyOf(values);
        parsers.put(id, text -> copy.get(text.strip().toLowerCase(java.util.Locale.ROOT)));
    }

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
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Event value of unknown type '" + typeId + "'");
        }
        eventValues.add(new EventValue(eventClass, typeId, (java.util.function.Function<Object, ?>) getter));
    }

    @Override
    public void parser(String typeId, java.util.function.Function<String, ?> parser) {
        checkOpen();
        if (!javaClasses.containsKey(typeId)) {
            throw new IllegalArgumentException("Parser for unknown type '" + typeId + "'");
        }
        parsers.put(typeId, parser);
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

    /** Whether unused optional placeholders of {@code typeId} get the event's value of that type. */
    boolean hasEventDefault(String typeId) {
        return eventDefaults.contains(typeId);
    }

    /**
     * How to get a value of {@code typeId}, or a subtype, from events of {@code eventClasses}, or
     * {@code null} if none of them has one. As in Skript, it's enough for one of the classes to have it:
     * for the others the value is {@code null}. For each class, the value registered for the closest
     * superclass wins, and among those, one of exactly {@code typeId}.
     */
    java.util.function.Function<Object, Object> eventValue(List<Class<?>> eventClasses, String typeId) {
        SyntaxRegistry registry = registry();
        List<Map.Entry<Class<?>, java.util.function.Function<Object, ?>>> getters = new ArrayList<>();
        for (Class<?> eventClass : eventClasses) {
            EventValue best = null;
            for (EventValue value : eventValues) {
                if (!value.eventClass().isAssignableFrom(eventClass)
                        || !registry.isSubtype(value.type(), typeId)) {
                    continue;
                }
                if (best == null || isBetter(value, best, typeId)) {
                    best = value;
                }
            }
            if (best != null) {
                getters.add(Map.entry(eventClass, best.getter()));
            }
        }
        if (getters.isEmpty()) {
            return null;
        }
        if (getters.size() == 1 && eventClasses.size() == 1) {
            java.util.function.Function<Object, ?> getter = getters.getFirst().getValue();
            return event -> getter.apply(event);
        }
        return event -> {
            for (Map.Entry<Class<?>, java.util.function.Function<Object, ?>> getter : getters) {
                if (getter.getKey().isInstance(event)) {
                    return getter.getValue().apply(event);
                }
            }
            return null;
        };
    }

    private static boolean isBetter(EventValue value, EventValue than, String typeId) {
        if (value.eventClass() != than.eventClass()) {
            return than.eventClass().isAssignableFrom(value.eventClass());
        }
        return value.type().equals(typeId) && !than.type().equals(typeId);
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
        checkOpen();
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

    /**
     * Turns on Skript's test syntax ({@code test "name":}, {@code assert}, {@code parse:} and
     * {@code last parse logs}), for running Skript's own tests. Call it before registering anything else.
     */
    public void enableTesting() {
        CoreSyntax.registerTesting(this);
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
        javaClasses.put(id, Object.class);
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
            registry = builder.build();
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
