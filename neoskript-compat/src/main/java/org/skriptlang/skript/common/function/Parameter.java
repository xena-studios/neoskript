/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.common.function;

import java.util.Set;

/** A function's parameter: its name, class (an array class for a plural one) and modifiers. */
public interface Parameter<T> {

    String name();

    Class<T> type();

    Set<Modifier> modifiers();

    default boolean hasModifier(Modifier modifier) {
        return modifiers().contains(modifier);
    }

    default <M extends Modifier> M getModifier(Class<M> modifierClass) {
        for (Modifier modifier : modifiers()) {
            if (modifierClass.isInstance(modifier)) {
                return modifierClass.cast(modifier);
            }
        }
        return null;
    }

    default boolean isSingle() {
        return !type().isArray();
    }

    /** Something about a parameter: optional, keyed or ranged. */
    interface Modifier {

        Modifier OPTIONAL = of();
        Modifier KEYED = of();
        Modifier RANGED = of();

        static Modifier of() {
            return new Modifier() {};
        }
    }
}
