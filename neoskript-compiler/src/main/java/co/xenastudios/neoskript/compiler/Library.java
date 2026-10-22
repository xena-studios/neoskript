/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
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

    private final SyntaxRegistry.Builder builder = SyntaxRegistry.builder();
    private final Map<Key, Object> bindings = new HashMap<>();
    private final Map<String, Class<?>> javaClasses = new HashMap<>();
    private SyntaxRegistry registry;

    public Library() {
        type(SyntaxRegistry.OBJECT, "object", Object.class);
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

    private void add(SyntaxKind kind, String id, String returnType, Object binding, String... patterns) {
        checkOpen();
        if (bindings.putIfAbsent(new Key(kind, id), binding) != null) {
            throw new IllegalArgumentException("Duplicate " + kind.name().toLowerCase() + " id: " + id);
        }
        builder.syntax(new SyntaxInfo(kind, id, Arrays.stream(patterns).map(PatternCompiler::compile).toList(),
                returnType, List.of()));
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
