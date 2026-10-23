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
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.storage.VariableStorage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoSkriptPlugin extends JavaPlugin {

    private VariableStorage storage;
    private ScriptCommands commands;

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
        if (TestRunner.enabled()) {
            StandardLibrary.registerTesting(library);
        }
        Scheduler scheduler = platform == Platform.FOLIA ? new FoliaScheduler(this) : new PaperScheduler(this);
        // Waiting triggers carry on through the scheduler; their errors can only be logged.
        Waits.install(scheduler, e -> getSLF4JLogger().error("Error in {}, line {}: {}", e.script(), e.line(),
                e.getCause().toString(), e.getCause()));
        EventDispatcher events = new EventDispatcher(this, getSLF4JLogger(), eventPriority(),
                getConfig().getBoolean("events.listen-to-cancelled", false));
        commands = new ScriptCommands(this, getSLF4JLogger(), scheduler);
        ScriptManager scripts = new ScriptManager(getDataPath().resolve("scripts"), library, scheduler, events,
                commands, getSLF4JLogger(), TestRunner.enabled() ? () -> getServer().shutdown() : null);
        storage = new VariableStorage(scripts.globals(), library.codecs(),
                getDataPath().resolve(getConfig().getString("variables.file", "variables.csv")));
        loadVariables();
        try {
            scripts.loadAll();
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't read the scripts folder", e);
        }
        // D10: NeoSkript takes over /skript and /sk, with /neoskript as an alias.
        getCommand("skript").setExecutor(new SkriptCommand(scripts, getPluginMeta().getVersion()));
    }

    /** The priority triggers listen to events at, {@code high} by default as in Skript. */
    private EventPriority eventPriority() {
        String name = getConfig().getString("events.priority", "high");
        try {
            return EventPriority.valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getSLF4JLogger().warn("Unknown event priority '{}' in config.yml; using high", name);
            return EventPriority.HIGH;
        }
    }

    /** Loads saved variables, then saves changed ones every {@code variables.save-interval}. */
    private void loadVariables() {
        try {
            long start = System.nanoTime();
            VariableStorage.Result loaded = storage.load();
            getSLF4JLogger().info("Loaded {} {} in {} ms{}", loaded.variables(), loaded.variables() == 1 ? "variable" : "variables",
                    (System.nanoTime() - start) / 1_000_000,
                    loaded.raw() == 0 ? "" : " (" + loaded.raw() + " kept as saved until NeoSkript supports their types)");
        } catch (IOException e) {
            // Don't run with an empty set of variables that would then overwrite the file.
            getSLF4JLogger().error("Couldn't load variables from {}. Variables won't be saved until it's fixed.",
                    storage.file(), e);
            storage = null;
            return;
        }
        Timespan interval = Timespan.parse(getConfig().getString("variables.save-interval", "5 minutes"));
        long seconds = Math.max(interval == null ? 300 : interval.millis() / 1000, 10);
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> saveVariables(false), seconds, seconds,
                TimeUnit.SECONDS);
    }

    private void saveVariables(boolean always) {
        VariableStorage current = storage;
        if (current == null || (!always && !current.changed())) {
            return;
        }
        try {
            VariableStorage.Result saved = current.save();
            if (saved.unsaved() > 0) {
                getSLF4JLogger().warn("{} variables have values that can't be saved yet, so they weren't", saved.unsaved());
            }
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't save variables to {}", current.file(), e);
        }
    }

    @Override
    public void onDisable() {
        getServer().getAsyncScheduler().cancelTasks(this);
        if (commands != null) {
            commands.unregister(getServer().getCommandMap());
        }
        saveVariables(false);
    }
}
