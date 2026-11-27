/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.function;

import co.xenastudios.neoskript.compat.Compat;
import org.skriptlang.skript.common.function.DefaultFunction;

/** Functions addons give scripts. */
public abstract class Functions {

    public static boolean callFunctionEvents = false;
    public static final String functionNamePattern = "[\\p{IsAlphabetic}_][\\p{IsAlphabetic}\\d_]*";

    private Functions() {}

    public static DefaultFunction<?> register(DefaultFunction<?> function) {
        Compat.get().functions().add(function);
        return function;
    }
}
