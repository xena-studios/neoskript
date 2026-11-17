/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.registration;

import ch.njol.skript.lang.SyntaxElement;
import java.util.Collection;
import java.util.SequencedCollection;
import java.util.function.Supplier;
import org.skriptlang.skript.util.Priority;

/** A syntax element as registered: its class, patterns, how to make one, and its priority. */
public non-sealed interface SyntaxInfo<E extends SyntaxElement> extends DefaultSyntaxInfos {

    Priority SIMPLE = Priority.base();
    Priority COMBINED = Priority.after(SIMPLE);
    Priority PATTERN_MATCHES_EVERYTHING = Priority.after(COMBINED);

    static <E extends SyntaxElement> SyntaxInfo<E> simple(Class<E> type, Supplier<E> instanceSupplier, String... patterns) {
        return builder(type).supplier(instanceSupplier).addPatterns(patterns).build();
    }

    static <E extends SyntaxElement> Builder<? extends Builder<?, E>, E> builder(Class<E> type) {
        return new co.xenastudios.neoskript.compat.CompatSyntaxInfo.Builder<>(type, null);
    }

    Builder<? extends Builder<?, E>, E> toBuilder();

    org.skriptlang.skript.docs.Origin origin();

    Class<E> type();

    E instance();

    SequencedCollection<String> patterns();

    Priority priority();

    /** Builds a syntax info. */
    interface Builder<B extends Builder<B, E>, E extends SyntaxElement> {

        B origin(org.skriptlang.skript.docs.Origin origin);

        B supplier(Supplier<E> supplier);

        B addPattern(String pattern);

        B addPatterns(String... patterns);

        B addPatterns(Collection<String> patterns);

        B clearPatterns();

        B priority(Priority priority);

        SyntaxInfo<E> build();

        void applyTo(Builder<?, ?> builder);
    }
}
