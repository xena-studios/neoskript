/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.addon;

/** A part of an addon that loads on its own: types first ({@link #init}), then syntax ({@link #load}). */
public interface AddonModule {

    default boolean canLoad(SkriptAddon addon) {
        return true;
    }

    default void init(SkriptAddon addon) {}

    void load(SkriptAddon addon);

    String name();

    default org.skriptlang.skript.registration.SyntaxRegistry moduleRegistry(SkriptAddon addon) {
        return addon.syntaxRegistry();
    }
}
