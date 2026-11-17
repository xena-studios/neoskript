/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.skriptlang.skript.common.function.DefaultFunction;

/** Functions addons register. */
public final class CompatFunctions {

    private final List<DefaultFunction<?>> functions = new CopyOnWriteArrayList<>();

    CompatFunctions() {}

    public void add(DefaultFunction<?> function) {
        functions.add(function);
    }

    List<DefaultFunction<?>> all() {
        return List.copyOf(functions);
    }
}
