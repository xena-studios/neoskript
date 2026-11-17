/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.lang.function.FunctionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.common.function.DefaultFunction;
import org.skriptlang.skript.common.function.FunctionArguments;
import org.skriptlang.skript.common.function.Parameter;

/** A function an addon wrote in Java, as {@code DefaultFunction.builder} builds it. */
public final class CompatFunction<T> implements DefaultFunction<T> {

    /** A parameter as built. */
    public record Param<T>(String name, Class<T> type, Set<Modifier> modifiers) implements Parameter<T> {}

    private final SkriptAddon source;
    private final String name;
    private final Class<T> returnType;
    private final List<Param<?>> parameters;
    private final java.util.function.Function<FunctionArguments, T> execute;

    private CompatFunction(FunctionBuilder<T> builder, java.util.function.Function<FunctionArguments, T> execute) {
        this.source = builder.source;
        this.name = builder.name;
        this.returnType = builder.returnType;
        this.parameters = List.copyOf(builder.parameters);
        this.execute = execute;
    }

    public Class<T> returnType() {
        return returnType;
    }

    public List<Param<?>> parameters() {
        return parameters;
    }

    /** Runs the function with its arguments by parameter. */
    public T call(Map<String, Object> arguments) {
        return execute.apply(new Arguments(arguments));
    }

    @Override
    public SkriptAddon source() {
        return source;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public T execute(FunctionEvent<?> event, FunctionArguments arguments) {
        return execute.apply(arguments);
    }

    @Override
    public org.skriptlang.skript.common.function.Signature<T> signature() {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't give functions' signatures");
    }

    @Override
    public boolean resetReturnValue() {
        return true;
    }

    @Override
    public String[] returnedKeys() {
        return null;
    }

    @Override
    public List<String> description() {
        return List.of();
    }

    @Override
    public List<String> since() {
        return List.of();
    }

    @Override
    public List<String> examples() {
        return List.of();
    }

    @Override
    public List<String> keywords() {
        return List.of();
    }

    @Override
    public List<String> requires() {
        return List.of();
    }

    /** A call's arguments, by parameter. */
    private record Arguments(Map<String, Object> values) implements FunctionArguments {

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(String name) {
            return (V) values.get(name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V getOrDefault(String name, V value) {
            Object given = values.get(name);
            return given == null ? value : (V) given;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V getOrDefault(String name, Supplier<V> value) {
            Object given = values.get(name);
            return given == null ? value.get() : (V) given;
        }

        @Override
        public Set<String> names() {
            return values.keySet();
        }
    }

    /** Builds a function. */
    public static final class FunctionBuilder<T> implements DefaultFunction.Builder<T> {

        private final SkriptAddon source;
        private final String name;
        private final Class<T> returnType;
        private final List<Param<?>> parameters = new ArrayList<>();

        public FunctionBuilder(SkriptAddon source, String name, Class<T> returnType) {
            this.source = source;
            this.name = name;
            this.returnType = returnType;
        }

        @Override
        public FunctionBuilder<T> contract(ch.njol.skript.util.Contract contract) {
            return this;
        }

        @Override
        public FunctionBuilder<T> description(String... description) {
            return this;
        }

        @Override
        public FunctionBuilder<T> since(String... since) {
            return this;
        }

        @Override
        public FunctionBuilder<T> examples(String... examples) {
            return this;
        }

        @Override
        public FunctionBuilder<T> keywords(String... keywords) {
            return this;
        }

        @Override
        public FunctionBuilder<T> requires(String... requires) {
            return this;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public FunctionBuilder<T> parameter(String name, Class<?> type, Parameter.Modifier... modifiers) {
            parameters.add(new Param(name, type, new LinkedHashSet<>(Arrays.asList(modifiers))));
            return this;
        }

        @Override
        public DefaultFunction<T> build(java.util.function.Function<FunctionArguments, T> execute) {
            return new CompatFunction<>(this, execute);
        }
    }

    static Map<String, Object> byName(List<Param<?>> parameters, Object[] arguments) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < parameters.size() && i < arguments.length; i++) {
            values.put(parameters.get(i).name(), arguments[i]);
        }
        return values;
    }
}
