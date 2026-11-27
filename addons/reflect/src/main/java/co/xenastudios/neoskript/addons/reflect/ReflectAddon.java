/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkript Reflect: Java from scripts, with skript-reflect's syntax. Scripts get full access to Java, as with
 * skript-reflect: anything the server can do, a script can do.
 */
public final class ReflectAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Imports imports = new Imports(getClassLoader());
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), registrar -> Syntax.register(registrar, imports));
    }

    @Override
    public void onDisable() {
        // The members looked up are kept with each class, the JDK's too: let go of them, and of this addon's classes.
        CallSite.forgetAll();
    }
}
