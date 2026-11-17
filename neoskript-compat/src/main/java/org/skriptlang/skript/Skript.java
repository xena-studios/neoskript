/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript;

import java.util.Collection;
import org.skriptlang.skript.addon.SkriptAddon;

/** Skript itself, as an addon that addons register with. */
public interface Skript extends SkriptAddon {

    SkriptAddon registerAddon(Class<?> source, String name);

    Collection<SkriptAddon> addons();

    default SkriptAddon addon(String name) {
        for (SkriptAddon addon : addons()) {
            if (addon.name().equals(name)) {
                return addon;
            }
        }
        return null;
    }

    @Override
    default Skript unmodifiableView() {
        return this;
    }
}
