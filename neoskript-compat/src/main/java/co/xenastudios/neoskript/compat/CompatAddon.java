/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.localization.Localizer;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.util.Registry;

/**
 * An addon registered with NeoSkriptCompat. Its syntax goes to NeoSkript as an addon of the same name, registered
 * when NeoSkript takes addons' syntax: see {@link CompatRegistration}.
 */
public class CompatAddon implements SkriptAddon {

    final Compat compat;
    private final Class<?> source;
    private final String name;
    private final CompatSyntaxRegistry syntaxRegistry = new CompatSyntaxRegistry(this);
    private final Localizer localizer = new CompatLocalizer();
    private final Map<Class<?>, Registry<?>> registries = new ConcurrentHashMap<>();

    CompatAddon(Compat compat, Class<?> source, String name) {
        this.compat = compat;
        this.source = source;
        this.name = name;
    }

    @Override
    public Class<?> source() {
        return source;
    }

    @Override
    public String name() {
        return name;
    }

    /** Skript's registries are shared: an addon's event value registry is Skript's. */
    @Override
    public <R extends Registry<?>> void storeRegistry(Class<R> registryClass, R registry) {
        shared().put(registryClass, registry);
    }

    @Override
    public void removeRegistry(Class<? extends Registry<?>> registryClass) {
        shared().remove(registryClass);
    }

    @Override
    public boolean hasRegistry(Class<? extends Registry<?>> registryClass) {
        return registryClass == EventValueRegistry.class || shared().containsKey(registryClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends Registry<?>> R registry(Class<R> registryClass) {
        if (registryClass == EventValueRegistry.class) {
            return (R) compat.eventValues();
        }
        R registry = (R) shared().get(registryClass);
        if (registry == null) {
            throw new NullPointerException("Registry not present for " + registryClass);
        }
        return registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends Registry<?>> R registry(Class<R> registryClass, Supplier<R> putIfAbsent) {
        if (registryClass == EventValueRegistry.class) {
            return (R) compat.eventValues();
        }
        return (R) shared().computeIfAbsent(registryClass, key -> putIfAbsent.get());
    }

    @Override
    public Collection<Registry<?>> registries() {
        List<Registry<?>> all = new java.util.ArrayList<>(shared().values());
        all.add(compat.eventValues());
        return all;
    }

    Map<Class<?>, Registry<?>> shared() {
        return compat.skript() == null || compat.skript() == this ? registries : compat.skript().shared();
    }

    @Override
    public SyntaxRegistry syntaxRegistry() {
        return syntaxRegistry;
    }

    CompatSyntaxRegistry compatSyntaxRegistry() {
        return syntaxRegistry;
    }

    @Override
    public Localizer localizer() {
        return localizer;
    }

    /** Called when the addon registers syntax: too late, once NeoSkript has loaded scripts. */
    void registered() {
        if (!compat.accepting()) {
            throw new ch.njol.skript.SkriptAPIException("Registering is disabled after initialisation!");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
