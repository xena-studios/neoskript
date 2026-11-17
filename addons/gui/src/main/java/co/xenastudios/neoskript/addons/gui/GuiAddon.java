/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptGui: inventory menus from scripts, with skript-gui's syntax. Scripts make GUIs with
 * {@code create a gui with virtual chest inventory with 3 rows named "Shop":}, fill slots with
 * {@code make gui slot 0 with diamond:} and code to run when they're clicked, and open them with
 * {@code open the last gui to player}.
 */
public final class GuiAddon extends JavaPlugin {

    private Guis guis;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        guis = new Guis(() -> NeoSkript.get().threads(), getLogger());
        GuiListener listener = new GuiListener(guis);
        getServer().getPluginManager().registerEvents(listener, this);
        Syntax syntax = new Syntax(guis, listener);
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), syntax::register);
    }

    @Override
    public void onDisable() {
        if (guis != null) {
            // Nobody keeps a GUI open whose code can't run any more.
            guis.closeAll();
        }
    }
}
