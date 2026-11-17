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
        Plugin neoskript = getServer().getPluginManager().getPlugin("NeoSkript");
        if (!getServer().isStopping() || neoskript == null || !neoskript.isEnabled()) {
            stopping.close(GRACE);
            return;
        }
        // The server is stopping, and NeoSkript, which this depends on, disables after this: its 'on unload'
        // triggers run then, and what they write still needs the databases. They close once NeoSkript is done.
        Thread closer = new Thread(() -> {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (neoskript.isEnabled() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            stopping.close(GRACE);
        }, "NeoSkriptDb-shutdown");
        closer.setDaemon(true);
        closer.start();
    }
}
