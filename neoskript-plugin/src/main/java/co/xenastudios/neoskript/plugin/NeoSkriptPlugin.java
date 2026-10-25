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
    private ScriptManager scripts;
    /** What stops the runtime error frames. */
    private Runnable runtimeErrorFrames;
    private EffectCommands effectCommands;

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
            // The variables copied are keyed as Skript's config said, so keep reading them that way.
            if (migration.playerUuids() != null
                    && migration.playerUuids() != getConfig().getBoolean("variables.player-uuids-in-names", true)) {
                getConfig().set("variables.player-uuids-in-names", migration.playerUuids());
                saveConfig();
                getSLF4JLogger().info("Players are their {} in variable names, as in Skript's config.sk.",
                        migration.playerUuids() ? "UUIDs" : "names");
            }
            // Effect commands work as they did in Skript.
            boolean changed = false;
            for (java.util.Map.Entry<String, Object> setting : migration.settings().entrySet()) {
                if (!setting.getValue().equals(getConfig().get(setting.getKey()))) {
                    getConfig().set(setting.getKey(), setting.getValue());
                    changed = true;
                }
            }
            if (changed) {
                saveConfig();
                getSLF4JLogger().info("Effect commands are set up as in Skript's config.sk.");
            }
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't copy Skript's scripts and variables", e);
        }

        Library library = new Library();
        if (TestRunner.enabled()) {
            library.enableTesting();
        }
        // Syntax of the plugins NeoSkript hooks into, as Skript's hooks, only when they're installed.
        boolean hooks = co.xenastudios.neoskript.paper.Hooks.anyInstalled();
        StandardLibrary.register(library, registrar -> {
            if (hooks) {
                co.xenastudios.neoskript.paper.Hooks.register(registrar, getPluginMeta().getVersion(), getSLF4JLogger());
            }
        });
        StandardLibrary.usePlayerUuidsInVariableNames(getConfig().getBoolean("variables.player-uuids-in-names", true));
        if (TestRunner.enabled()) {
            StandardLibrary.registerTesting(library);
            // As Skript's test mode does: the chunks tests use stay loaded and ticking.
            try {
                org.bukkit.World world = getServer().getWorlds().getFirst();
                world.addPluginChunkTicket(0, 0, this);
                world.addPluginChunkTicket(100, 100, this);
            } catch (RuntimeException notHere) {
                getSLF4JLogger().warn("Couldn't keep the test chunks loaded: {}", notHere.toString());
            }
        }
        Scheduler scheduler = platform == Platform.FOLIA ? new FoliaScheduler(this) : new PaperScheduler(this);
        // Runtime errors go to the console as Skript prints them, limited per frame by config.yml.
        runtimeErrorFrames = RuntimeErrorConsole.start(getConfig().getConfigurationSection("runtime-errors"), scheduler);
        Waits.install(scheduler, e -> {
            // A command failed after a wait just stops, as there's no one left to tell.
            if (!(e.getCause() instanceof co.xenastudios.neoskript.runtime.command.CommandEvent.Failed)) {
                getSLF4JLogger().error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(),
                        e.getCause());
            }
        });
        EventDispatcher events = new EventDispatcher(this, getSLF4JLogger(), eventPriority(),
                getConfig().getBoolean("events.listen-to-cancelled", false));
        commands = new ScriptCommands(this, getSLF4JLogger(), scheduler);
        scripts = new ScriptManager(getDataPath().resolve("scripts"), library, scheduler, events,
                commands, getSLF4JLogger(), TestRunner.enabled() ? () -> getServer().shutdown() : null);
        co.xenastudios.neoskript.runtime.ScriptCatalog.install(scripts.catalog());
        effectCommands = new EffectCommands(scripts, scheduler, getSLF4JLogger(), TestRunner.enabled(),
                EffectCommands.Settings.read(getConfig().getConfigurationSection("effect-commands")));
        getServer().getPluginManager().registerEvents(effectCommands, this);
        storage = new VariableStorage(scripts.globals(), library.codecs(),
                getDataPath().resolve(getConfig().getString("variables.file", "variables.csv")));
        loadVariables();
        Runnable start = () -> {
            if (hooks) {
                co.xenastudios.neoskript.paper.Hooks.hookServices(getSLF4JLogger());
            }
            try {
                scripts.loadAll();
            } catch (IOException e) {
                getSLF4JLogger().error("Couldn't read the scripts folder", e);
            }
            // Events NeoSkript fires itself: experience spawning, times of day, starting and stopping.
            co.xenastudios.neoskript.paper.EventBridges.start(this, eventPriority());
            co.xenastudios.neoskript.paper.Hooks.start(this, eventPriority());
        };
        if (hooks) {
            // As Skript: with plugins to hook into, taking Vault's services and then loading scripts wait for the
            // server to start, when every plugin has enabled and registered its services.
            getServer().getGlobalRegionScheduler().run(this, task -> start.run());
        } else {
            start.run();
        }
        // D10: NeoSkript takes over /skript and /sk, with /neoskript as an alias.
        getCommand("skript").setExecutor(new SkriptCommand(this, scripts, this::reloadSettings));
    }

    /**
     * {@code /skript reload config}: reads config.yml again and applies what can change while the server
     * runs. The other settings (metrics, event priority, cancelled events and where variables are saved)
     * take effect when the server restarts.
     */
    private void reloadSettings() {
        saveDefaultConfig();
        reloadConfig();
        StandardLibrary.usePlayerUuidsInVariableNames(getConfig().getBoolean("variables.player-uuids-in-names", true));
        if (effectCommands != null) {
            effectCommands.settings(EffectCommands.Settings.read(getConfig().getConfigurationSection("effect-commands")));
        }
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
        if (runtimeErrorFrames != null) {
            runtimeErrorFrames.run();
        }
        if (scripts != null) {
            co.xenastudios.neoskript.paper.EventBridges.stop();
            scripts.unloadAll();
        }
        getServer().getAsyncScheduler().cancelTasks(this);
        if (commands != null) {
            commands.unregister(getServer().getCommandMap());
        }
        saveVariables(false);
    }
}
