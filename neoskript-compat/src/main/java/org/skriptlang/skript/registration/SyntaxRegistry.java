/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.registration;

import java.util.Collection;
import org.skriptlang.skript.util.Registry;
import org.skriptlang.skript.util.ViewProvider;

/** Where an addon's syntax is registered, by kind (its key). */
public interface SyntaxRegistry extends ViewProvider<SyntaxRegistry>, Registry<SyntaxInfo<?>> {

    Key<DefaultSyntaxInfos.Structure<?>> STRUCTURE = Key.of("structure");
    Key<SyntaxInfo<? extends ch.njol.skript.lang.Section>> SECTION = Key.of("section");
    Key<SyntaxInfo<? extends ch.njol.skript.lang.Statement>> STATEMENT = Key.of("statement");
    Key<SyntaxInfo<? extends ch.njol.skript.lang.Effect>> EFFECT = ChildKey.of(STATEMENT, "effect");
    Key<SyntaxInfo<? extends ch.njol.skript.lang.Condition>> CONDITION = ChildKey.of(STATEMENT, "condition");
    Key<DefaultSyntaxInfos.Expression<?, ?>> EXPRESSION = Key.of("expression");

    static SyntaxRegistry empty() {
        return new co.xenastudios.neoskript.compat.CompatSyntaxRegistry(null);
    }

    <I extends SyntaxInfo<?>> Collection<I> syntaxes(Key<I> key);

    <I extends SyntaxInfo<?>> void register(Key<I> key, I info);

    void unregister(SyntaxInfo<?> info);

    <I extends SyntaxInfo<?>> void unregister(Key<I> key, I info);

    @Override
    default SyntaxRegistry unmodifiableView() {
        return this;
    }

    @Override
    Collection<SyntaxInfo<?>> elements();

    /** A kind of syntax. */
    interface Key<I extends SyntaxInfo<?>> {

        static <I extends SyntaxInfo<?>> Key<I> of(String name) {
            return new co.xenastudios.neoskript.compat.CompatSyntaxRegistry.Named<>(name, null);
        }

        String name();
    }

    /** A kind of syntax that's also of another kind, as effects are statements. */
    interface ChildKey<I extends P, P extends SyntaxInfo<?>> extends Key<I> {

        @SuppressWarnings({"unchecked", "rawtypes"})
        static <I extends P, P extends SyntaxInfo<?>> ChildKey<I, P> of(Key<P> parent, String name) {
            return (ChildKey<I, P>) new co.xenastudios.neoskript.compat.CompatSyntaxRegistry.Named(name, (Key) parent);
        }

        Key<P> parent();
    }
}
