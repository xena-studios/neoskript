/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptPlaceholders: PlaceholderAPI from scripts, with skript-placeholders' syntax. Scripts register placeholders
 * with {@code placeholderapi placeholder with the prefix "..."} and read anyone's with
 * {@code the placeholder "..." from player}.
 *
 * <p>Without PlaceholderAPI, the syntax is still there, so scripts load: a placeholder structure is an error that
 * says PlaceholderAPI isn't installed, and placeholders have no value.
 */
public final class PlaceholdersAddon extends JavaPlugin {

    private Placeholders placeholders;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Plugin api = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        boolean installed = api != null && api.isEnabled() && hasPlaceholderApiClasses();
        if (installed) {
            placeholders = new Placeholders(new PlaceholderApi(this, getLogger()), PlaceholdersAddon::onGlobalThread,
                    () -> !Bukkit.isPrimaryThread(), getLogger());
        } else {
            getLogger().warning("PlaceholderAPI isn't installed, so scripts can't register placeholders or read them."
                    + " Install PlaceholderAPI and restart the server.");
        }
        Placeholders registered = placeholders;
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), registrar -> Syntax.register(registrar,
                registered, installed ? PlaceholderApi::value : null, installed ? PlaceholderApi::relationalValue : null));
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.close();
        }
    }

    private static boolean hasPlaceholderApiClasses() {
        try {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion", false,
                    PlaceholdersAddon.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    /** Runs a task on the global thread: now if this is it (the main thread on Paper), else soon. */
    private static void onGlobalThread(Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            NeoSkript.get().threads().runGlobal(task);
        }
    }
}
