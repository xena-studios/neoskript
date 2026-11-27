/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.SkriptAPIException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.skriptlang.skript.Skript;
import org.skriptlang.skript.addon.SkriptAddon;

/** Skript, as addons register with it: {@code Skript.instance()}. */
public final class CompatSkript extends CompatAddon implements Skript {

    private final List<CompatAddon> addons = new CopyOnWriteArrayList<>();

    CompatSkript(Compat compat) {
        super(compat, ch.njol.skript.Skript.class, "Skript");
    }

    @Override
    public SkriptAddon registerAddon(Class<?> source, String name) {
        if (!compat.accepting()) {
            throw new SkriptAPIException("Registering is disabled after initialisation!");
        }
        for (CompatAddon addon : addons) {
            if (addon.name().equals(name)) {
                throw new SkriptAPIException("An addon called " + name + " is registered already");
            }
        }
        CompatAddon addon = new CompatAddon(compat, source, name);
        addons.add(addon);
        CompatRegistration.toNeoSkript(compat, addon);
        return addon;
    }

    @Override
    public Collection<SkriptAddon> addons() {
        return List.copyOf(addons);
    }

    @Override
    public Skript unmodifiableView() {
        return this;
    }
}
