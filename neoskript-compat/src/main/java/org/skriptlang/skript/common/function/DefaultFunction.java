/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.common.function;

import org.skriptlang.skript.addon.SkriptAddon;

/** A function written in Java, as addons give scripts. */
public interface DefaultFunction<T> extends Function<T>, ch.njol.skript.doc.Documentable {

    static <T> Builder<T> builder(SkriptAddon source, String name, Class<T> returnType) {
        return new co.xenastudios.neoskript.compat.CompatFunction.FunctionBuilder<>(source, name, returnType);
    }

    SkriptAddon source();

    /** Builds a function. */
    interface Builder<T> {

        Builder<T> contract(ch.njol.skript.util.Contract contract);

        Builder<T> description(String... description);

        Builder<T> since(String... since);

        Builder<T> examples(String... examples);

        Builder<T> keywords(String... keywords);

        Builder<T> requires(String... requires);

        Builder<T> parameter(String name, Class<?> type, Parameter.Modifier... modifiers);

        DefaultFunction<T> build(java.util.function.Function<FunctionArguments, T> execute);
    }
}
