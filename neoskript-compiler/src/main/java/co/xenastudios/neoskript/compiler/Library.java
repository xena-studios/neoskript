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
import java.util.Arrays;
import java.util.HashMap;
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
            Set.of("number", "integer", "long", "string", "boolean", "timespan", "date", "time");

    private record Key(SyntaxKind kind, String id) {}

    /** The binding of the core syntax the compiler implements itself: control flow, variables, arithmetic. */
    record Core(String id) {}

    private final SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
    private final Map<Key, Object> bindings = new HashMap<>();
    private final Map<String, Class<?>> javaClasses = new HashMap<>();
    private final Map<FunctionInfo, Function> functions = new HashMap<>();
    private final Map<String, Object> literalValues = new HashMap<>();
    private final VariableCodecs codecs = new VariableCodecs();
    private SyntaxRegistry registry;

    public Library() {
        type(SyntaxRegistry.OBJECT, "object", Object.class);
        type("timespan", "timespan", Timespan.class);
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
    public void effect(String id, ElementFactory<Effect> factory, String... patterns) {
        add(SyntaxKind.EFFECT, id, null, factory, patterns);
    }

    @Override
    public void condition(String id, ElementFactory<Condition> factory, String... patterns) {
        add(SyntaxKind.CONDITION, id, null, factory, patterns);
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
    public void function(String name, List<Parameter> parameters, String returnType, boolean returnsPlural,
            Function function) {
        checkOpen();
        FunctionInfo info = new FunctionInfo(name, parameters.stream()
                .map(p -> new FunctionInfo.Parameter(p.name(), p.type(), p.plural(), p.optional()))
                .toList(), returnType, returnsPlural);
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

    /** The class values of a type have, or {@code Object} if the type is unknown. */
    Class<?> javaClass(String typeId) {
        return javaClasses.getOrDefault(typeId, Object.class);
    }

    /** The implementation of a built-in function, or {@code null} if {@code function} isn't one. */
    Function builtIn(FunctionInfo function) {
        return functions.get(function);
    }

    /** The syntax registry for the frontend. Nothing can be registered after this is called. */
    public synchronized SyntaxRegistry registry() {
        if (registry == null) {
            registry = builder.build();
        }
        return registry;
    }

    /** What a syntax element is bound to: an {@link ElementFactory} or, for events, a {@link ScriptEvent}. */
    Object binding(SyntaxInfo syntax) {
        return bindings.get(new Key(syntax.kind(), syntax.id()));
    }

    private void checkOpen() {
        if (registry != null) {
            throw new IllegalStateException("The library is frozen: register everything before parsing scripts");
        }
    }
}
