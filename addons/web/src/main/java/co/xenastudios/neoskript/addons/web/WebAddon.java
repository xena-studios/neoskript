/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.addons.web.http.HttpEngine;
import co.xenastudios.neoskript.api.NeoSkript;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptWeb: HTTP requests and JSON for scripts, with the syntax of Reqn 1.2.3 and skJson 6.0.0's requests and
 * JSON, so scripts written for them run unchanged. Requests run on the addon's own threads and pause the trigger
 * until they're answered ({@link co.xenastudios.neoskript.api.registration.Registrar#pausingEffect}).
 */
public final class WebAddon extends JavaPlugin {

    private volatile HttpEngine engine;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        WebConfig config = WebConfig.read(getConfig(), getLogger()::warning);
        engine = new HttpEngine(config);
        Web web = new Web(engine, config, new Console(getLogger()), this);
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), registrar -> WebSyntax.register(registrar, web));
    }

    @Override
    public void onDisable() {
        HttpEngine running = engine;
        engine = null;
        if (running != null) {
            // Requests still running fail, and their threads stop: nothing is left behind after a reload or a stop.
            running.close();
        }
    }

    /**
     * What the syntax needs: the engine that sends requests, the settings, and where warnings go.
     *
     * @param engine sends requests
     * @param config the settings
     * @param console where warnings go
     * @param plugin the addon
     */
    public record Web(HttpEngine engine, WebConfig config, Console console, JavaPlugin plugin) {}
}
