/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.platform.Platform;
import co.xenastudios.neoskript.runtime.scheduler.FoliaScheduler;
import co.xenastudios.neoskript.runtime.scheduler.PaperScheduler;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.io.IOException;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoSkriptPlugin extends JavaPlugin {

    /** https://bstats.org/plugin/bukkit/NeoSkript/34868 */
    private static final int BSTATS_PLUGIN_ID = 34868;

    @Override
    public void onEnable() {
        // D10: NeoSkript replaces Skript, so running both would fight over scripts and commands.
        if (getServer().getPluginManager().getPlugin("Skript") != null) {
            getSLF4JLogger().error(
                    "Skript is installed. NeoSkript replaces Skript, so they can't run together. "
                            + "Remove Skript.jar from the plugins folder to use NeoSkript. NeoSkript is now disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        Platform platform = Platform.detect();
        // D15: bStats, opt-out with `metrics: false` (or globally in plugins/bStats/config.yml).
        if (getConfig().getBoolean("metrics", true)) {
            Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("platform", platform::displayName));
        }

        getSLF4JLogger().info(
                "NeoSkript {} enabled on {} {}",
                getPluginMeta().getVersion(),
                platform.displayName(),
                getServer().getMinecraftVersion());

        try {
            // Drop-in replacement: the first time, bring over Skript's scripts and variables.
            SkriptMigration.Result migration = SkriptMigration.migrate(
                    getDataPath().resolveSibling("Skript"), getDataPath());
            if (!migration.copied().isEmpty()) {
                getSLF4JLogger().info("Copied {} from plugins/Skript ({} {}). Skript's folder was left as it was.",
                        String.join(" and ", migration.copied()), migration.scriptCount(),
                        migration.scriptCount() == 1 ? "script" : "scripts");
            }
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't copy Skript's scripts and variables", e);
        }

        Library library = new Library();
        if (TestRunner.enabled()) {
            library.enableTesting();
        }
        StandardLibrary.register(library);
        Scheduler scheduler = platform == Platform.FOLIA ? new FoliaScheduler(this) : new PaperScheduler(this);
        // Waiting triggers carry on through the scheduler; their errors can only be logged.
        Waits.install(scheduler, e -> getSLF4JLogger().error("Error in {}, line {}: {}", e.script(), e.line(),
                e.getCause().toString(), e.getCause()));
        ScriptManager scripts = new ScriptManager(getDataPath().resolve("scripts"), library, scheduler,
                getSLF4JLogger(), TestRunner.enabled() ? () -> getServer().shutdown() : null);
        try {
            scripts.loadAll();
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't read the scripts folder", e);
        }
        // D10: NeoSkript takes over /skript and /sk, with /neoskript as an alias.
        getCommand("skript").setExecutor(new SkriptCommand(scripts, getPluginMeta().getVersion()));
    }
}
