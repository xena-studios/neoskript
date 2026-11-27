/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.addon;

import java.util.Collection;
import java.util.function.Supplier;
import org.skriptlang.skript.localization.Localizer;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.util.Registry;
import org.skriptlang.skript.util.ViewProvider;

/** An addon: what it registers, in its registries. */
public interface SkriptAddon extends ViewProvider<SkriptAddon> {

    Class<?> source();

    String name();

    <R extends Registry<?>> void storeRegistry(Class<R> registryClass, R registry);

    void removeRegistry(Class<? extends Registry<?>> registryClass);

    boolean hasRegistry(Class<? extends Registry<?>> registryClass);

    <R extends Registry<?>> R registry(Class<R> registryClass);

    <R extends Registry<?>> R registry(Class<R> registryClass, Supplier<R> putIfAbsent);

    Collection<Registry<?>> registries();

    SyntaxRegistry syntaxRegistry();

    Localizer localizer();

    /** Loads modules as Skript does: each that can load is set up ({@code init}), then each registers ({@code load}). */
    default void loadModules(AddonModule... modules) {
        java.util.List<AddonModule> loading = new java.util.ArrayList<>();
        for (AddonModule module : modules) {
            if (module.canLoad(this)) {
                loading.add(module);
            }
        }
        for (AddonModule module : loading) {
            module.init(this);
        }
        for (AddonModule module : loading) {
            module.load(this);
        }
    }

    @Override
    default SkriptAddon unmodifiableView() {
        return this;
    }
}
