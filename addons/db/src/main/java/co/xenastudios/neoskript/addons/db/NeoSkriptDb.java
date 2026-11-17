/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import co.xenastudios.neoskript.addon.support.OfficialAddon;
import co.xenastudios.neoskript.api.NeoSkript;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptDb: SQL from scripts, with skript-db's syntax. Statements run on its own threads and the trigger
 * pauses until they're done, so a slow database never holds up the server.
 */
public final class NeoSkriptDb extends JavaPlugin {

    /**
     * How long statements still running when NeoSkriptDb stops get to finish. Longer than NeoSkript's grace for
     * what {@code on unload} triggers pause for (3 seconds), so their last writes are never cut short here.
     */
    private static final Duration GRACE = Duration.ofSeconds(5);

    private Engine engine;

    @Override
    public void onEnable() {
        if (!OfficialAddon.start(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        List<String> warnings = new ArrayList<>();
        Settings settings = Settings.read(key -> getConfig().get(key), warnings);
        warnings.forEach(warning -> getLogger().warning("config.yml: " + warning));
        Engine started = new Engine(settings, getLogger(), getClassLoader(), Bukkit::isPrimaryThread);
        engine = started;
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), registrar -> DbSyntax.register(registrar, started));
        getLogger().info("Ready with " + settings.workerThreads() + " threads for statements and up to "
                + settings.poolSize() + " connections per database.");
    }

    @Override
    public void onDisable() {
        Engine stopping = engine;
        engine = null;
        if (stopping == null) {
            return;
        }
        // NeoSkript disables after this (it's a dependency), and runs its 'on unload' triggers then, whose writes
        // still need the databases: on a server stop, /reload, or a plugin manager disabling both. They close once
        // NeoSkript has stopped running scripts.
        Runnable cancel;
        try {
            cancel = NeoSkript.get().whenStopped(() -> stopping.close(GRACE));
        } catch (RuntimeException | LinkageError gone) {
            stopping.close(GRACE);
            return;
        }
        // Disabled alone, with NeoSkript still running (a plugin manager): scripts using the syntax fail from now
        // on, and the connections close once running statements have had their grace.
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(LONE_DISABLE.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Plugin neoskript = Bukkit.getPluginManager().getPlugin("NeoSkript");
            if (neoskript != null && neoskript.isEnabled()) {
                stopping.close(GRACE);
                // Closed: NeoSkript lets go of the task, and with it this plugin's classes.
                cancel.run();
            }
        }, "NeoSkriptDb-shutdown");
        closer.setDaemon(true);
        closer.start();
    }

    /** How long NeoSkriptDb waits, disabled alone, before it closes its connections anyway. */
    private static final Duration LONE_DISABLE = Duration.ofSeconds(30);
}
