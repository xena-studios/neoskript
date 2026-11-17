/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.template;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The template for an official addon: it checks for the community addon it replaces, then registers its syntax
 * through NeoSkript's API. Ids start with the addon's name, so they can't clash with others'.
 */
public final class TemplateAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        // First: the community addon it replaces, and NeoSkript's version.
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), TemplateAddon::register);
    }

    static void register(Registrar registrar) {
        // 'the template greeting for "Steve"' is "Hello, Steve!".
        registrar.expression("NeoSkriptTemplateExprGreeting", "string",
                info -> (context, arguments) -> arguments[0] instanceof String name ? "Hello, " + name + "!" : null,
                "[the] template greeting for %text%");
        registrar.affinity("NeoSkriptTemplateExprGreeting", ThreadAffinity.ANY);
    }
}
