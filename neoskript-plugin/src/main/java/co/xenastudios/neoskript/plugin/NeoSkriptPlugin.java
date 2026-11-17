/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.plugin.config.ConfigException;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import co.xenastudios.neoskript.plugin.text.Message;
import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.platform.Platform;
import co.xenastudios.neoskript.runtime.scheduler.FoliaScheduler;
import co.xenastudios.neoskript.runtime.scheduler.PaperScheduler;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
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
    /** The settings from config.yml, swapped whole on {@code /sk reload config}. */
    private volatile NeoSkriptConfig settings = NeoSkriptConfig.DEFAULTS;
    private UpdateNotices updates;
    private Scheduler scheduler;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask saveTask;

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

        loadSettings();
        Platform platform = Platform.detect();
        // D15: bStats, opt-out with `metrics: false` (or globally in plugins/bStats/config.yml).
        if (settings.metrics()) {
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
            if (migration.variablesBackup() != null) {
                getSLF4JLogger().info("Backed up Skript's variables as {} before loading them",
                        getDataPath().toAbsolutePath().relativize(migration.variablesBackup().toAbsolutePath()));
            }
            copySkriptSettings(migration);
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
        StandardLibrary.usePlayerUuidsInVariableNames(settings.variables().playerUuidsInNames());
        if (co.xenastudios.neoskript.plugin.check.CheckMode.requested()) {
            // Started by NeoSkript's CLI to check scripts: check them, write the report, and stop.
            try {
                co.xenastudios.neoskript.plugin.check.CheckMode.run(library);
                getSLF4JLogger().info("Checked the scripts; stopping the server");
            } catch (IOException | RuntimeException e) {
                getSLF4JLogger().error("Couldn't check the scripts", e);
            }
            getServer().getGlobalRegionScheduler().run(this, task -> getServer().shutdown());
            return;
        }
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
        scheduler = platform == Platform.FOLIA ? new FoliaScheduler(this) : new PaperScheduler(this);
        // Runtime errors go to the console as Skript prints them, limited per frame by config.yml.
        runtimeErrorFrames = RuntimeErrorConsole.start(settings.runtimeErrors(), scheduler);
        java.util.function.Consumer<co.xenastudios.neoskript.runtime.ScriptRuntimeException> laterErrors = e -> {
            // A command failed after a wait just stops, as there's no one left to tell.
            if (!(e.getCause() instanceof co.xenastudios.neoskript.runtime.command.CommandEvent.Failed)) {
                getSLF4JLogger().error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(),
                        e.getCause());
            }
        };
        Waits.install(scheduler, laterErrors);
        if (platform == Platform.FOLIA) {
            // Code compiled from now on checks region ownership (plan 3.3); on Paper it never does.
            co.xenastudios.neoskript.runtime.region.Regions.install(
                    new co.xenastudios.neoskript.runtime.region.FoliaRegionAccess(this), laterErrors);
        }
        EventDispatcher events = new EventDispatcher(this, getSLF4JLogger(), eventPriority(),
                settings.events().listenToCancelled());
        commands = new ScriptCommands(this, getSLF4JLogger(), scheduler);
        scripts = new ScriptManager(getDataPath().resolve("scripts"), library, scheduler, events,
                commands, getSLF4JLogger(), TestRunner.enabled() ? () -> getServer().shutdown() : null);
        co.xenastudios.neoskript.runtime.ScriptCatalog.install(scripts.catalog());
        effectCommands = new EffectCommands(scripts, scheduler, getSLF4JLogger(), TestRunner.enabled(),
                EffectCommands.Settings.of(settings.effectCommands()));
        getServer().getPluginManager().registerEvents(effectCommands, this);
        storage = new VariableStorage(scripts.globals(), library.codecs(),
                getDataPath().resolve(settings.variables().file()));
        loadVariables();
        Runnable start = () -> {
            if (hooks) {
                co.xenastudios.neoskript.paper.Hooks.hookServices(getSLF4JLogger());
            }
            exportSyntax(library);
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
        // D15: tell admins about newer versions on Modrinth, opt-out with `update-checker: false`. Test servers
        // never check.
        if (!TestRunner.enabled()) {
            updates = UpdateNotices.start(this, getSLF4JLogger(), settings.updateChecker());
        }
        // D10: NeoSkript takes over /skript and /sk, with /neoskript and /ns as aliases.
        getCommand("skript").setExecutor(new SkriptCommand(this, scripts));
    }

    /**
     * With {@code -Dneoskript.syntaxExport=<file>}, writes every registered syntax element, type and function to
     * that file as JSON, for neoskript.dev's syntax reference and in-browser parser. The harness's {@code syntax-export}
     * scenario sets it.
     */
    private void exportSyntax(Library library) {
        String file = System.getProperty("neoskript.syntaxExport");
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path path = java.nio.file.Path.of(file);
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            String server = Platform.detect().displayName() + " " + getServer().getMinecraftVersion();
            java.nio.file.Files.writeString(path, library.export(getPluginMeta().getVersion(), server).toJson());
            getSLF4JLogger().info("Wrote the syntax export to {}", path.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            getSLF4JLogger().error("Couldn't write the syntax export", e);
        }
    }

    private java.nio.file.Path configFile() {
        return getDataPath().resolve("config.yml");
    }

    /**
     * Reads config.yml at startup, writing the defaults if there's none and carrying an old one forward. A file
     * that isn't valid YAML is left as it is, and NeoSkript runs on the defaults until it's fixed.
     */
    private void loadSettings() {
        try {
            accept(ConfigLoader.load(NeoSkriptConfig.SPEC, configFile()));
        } catch (ConfigException e) {
            getSLF4JLogger().error("{} Running on the default settings until it's fixed and reloaded with /sk reload config.",
                    e.getMessage());
            settings = NeoSkriptConfig.DEFAULTS;
            Message.theme(settings.theme());
        }
    }

    /** Uses what loading config.yml found, and logs what it changed and what it couldn't use. */
    private void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
        result.notices().forEach(notice -> getSLF4JLogger().info(notice));
        result.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
        settings = result.config();
        Message.theme(settings.theme());
    }

    /**
     * Copies the settings Skript's config.sk had to config.yml, so variables are found and effect commands work
     * as they did in Skript.
     */
    private void copySkriptSettings(SkriptMigration.Result migration) {
        java.util.Map<String, Object> copied = new java.util.LinkedHashMap<>();
        if (migration.playerUuids() != null) {
            copied.put("variables.player-uuids-in-names", migration.playerUuids());
        }
        copied.putAll(migration.settings());
        org.bukkit.configuration.file.YamlConfiguration current =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile().toFile());
        copied.entrySet().removeIf(setting -> setting.getValue().equals(current.get(setting.getKey())));
        if (copied.isEmpty()) {
            return;
        }
        try {
            ConfigLoader.set(NeoSkriptConfig.SPEC, configFile(), copied);
            accept(ConfigLoader.load(NeoSkriptConfig.SPEC, configFile()));
        } catch (ConfigException e) {
            getSLF4JLogger().error("Couldn't copy Skript's settings to config.yml: {}", e.getMessage());
            return;
        }
        if (copied.containsKey("variables.player-uuids-in-names")) {
            getSLF4JLogger().info("Players are their {} in variable names, as in Skript's config.sk.",
                    settings.variables().playerUuidsInNames() ? "UUIDs" : "names");
        }
        if (copied.keySet().stream().anyMatch(path -> path.startsWith("effect-commands."))) {
            getSLF4JLogger().info("Effect commands are set up as in Skript's config.sk.");
        }
    }

    /** The settings from config.yml. */
    NeoSkriptConfig settings() {
        return settings;
    }

    /** The update checker, or {@code null} on test servers and development builds, which never check. */
    UpdateNotices updates() {
        return updates;
    }

    /**
     * What {@code /sk reload config} found: why config.yml couldn't be read ({@code null} if it was), how many
     * settings fell back to their defaults, and the changed settings that only apply when the server restarts.
     */
    record SettingsReload(String failure, int warnings, java.util.List<String> restartOnly) {}

    /**
     * {@code /skript reload config}: reads config.yml again and applies what can change while the server runs:
     * the theme, the update checker, how players are named in variables, how often variables are saved, the
     * runtime error limits and effect commands. If the file can't be read, the running settings stay.
     */
    SettingsReload reloadSettings() {
        NeoSkriptConfig before = settings;
        ConfigLoader.Result<NeoSkriptConfig> result;
        try {
            result = ConfigLoader.load(NeoSkriptConfig.SPEC, configFile());
        } catch (ConfigException e) {
            getSLF4JLogger().error("{} The running settings are kept.", e.getMessage());
            return new SettingsReload(e.getMessage(), 0, java.util.List.of());
        }
        accept(result);
        NeoSkriptConfig now = settings;
        StandardLibrary.usePlayerUuidsInVariableNames(now.variables().playerUuidsInNames());
        if (effectCommands != null) {
            effectCommands.settings(EffectCommands.Settings.of(now.effectCommands()));
        }
        if (runtimeErrorFrames != null && !now.runtimeErrors().equals(before.runtimeErrors())) {
            runtimeErrorFrames.run();
            runtimeErrorFrames = RuntimeErrorConsole.start(now.runtimeErrors(), scheduler);
        }
        if (saveTask != null && !now.variables().saveInterval().equals(before.variables().saveInterval())) {
            saveTask.cancel();
            scheduleSaves();
        }
        if (updates != null) {
            updates.enabled(now.updateChecker());
            if (now.updateChecker() && !before.updateChecker()) {
                getServer().getAsyncScheduler().runNow(this, task -> {
                    try {
                        updates.checkNow();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        getSLF4JLogger().debug("Couldn't check Modrinth for a newer NeoSkript: {}", e.toString());
                    }
                });
            }
        }
        return new SettingsReload(null, result.warnings().size(), now.restartOnlyChanges(before));
    }

    /** The priority triggers listen to events at, {@code high} by default as in Skript. */
    private EventPriority eventPriority() {
        return settings.events().priority().bukkit();
    }

    /** Loads saved variables, then saves changed ones every {@code variables.save-interval}. */
    private void loadVariables() {
        try {
            long start = System.nanoTime();
            VariableStorage.Result loaded = storage.load();
            if (loaded.leftovers() > 0) {
                getSLF4JLogger().info("Removed {} temporary {} left by a save that was interrupted", loaded.leftovers(),
                        loaded.leftovers() == 1 ? "file" : "files");
            }
            if (!loaded.skipped().isEmpty()) {
                // As Skript reports them.
                for (var problem : loaded.skipped()) {
                    getSLF4JLogger().error(problem.message());
                }
                int count = loaded.skipped().size();
                getSLF4JLogger().error("{} variable{} could not be loaded!", count, count == 1 ? "" : "s");
                getSLF4JLogger().error("Affected variables: {}", loaded.skipped().stream()
                        .map(co.xenastudios.neoskript.runtime.variables.storage.VariableFile.Problem::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
                getSLF4JLogger().info("Created a backup of {} as {}", storage.file().getFileName(),
                        getDataPath().toAbsolutePath().relativize(loaded.backup().toAbsolutePath()));
                getSLF4JLogger().warn("Skipped {} {} of {} that couldn't be read; the other variables loaded. "
                        + "The next save leaves those lines out, and the backup keeps them.", count,
                        count == 1 ? "line" : "lines", storage.file().getFileName());
            }
            getSLF4JLogger().info("Loaded {} {} in {} ms{}", loaded.variables(), loaded.variables() == 1 ? "variable" : "variables",
                    (System.nanoTime() - start) / 1_000_000,
                    loaded.raw() == 0 ? "" : " (" + loaded.raw() + " kept as saved until NeoSkript supports their types)");
        } catch (IOException | RuntimeException e) {
            // Don't run with an empty set of variables that would then overwrite the file. The storage never saves
            // until it has loaded, so nothing writes over the file, not even when the server stops.
            getSLF4JLogger().error("Couldn't load variables from {}. Variables won't be saved until it's fixed.",
                    storage.file(), e);
            storage = null;
            return;
        }
        scheduleSaves();
    }

    /** Saves changed variables every {@code variables.save-interval}, at least every 10 seconds. */
    private void scheduleSaves() {
        long seconds = Math.max(settings.variables().saveInterval().millis() / 1000, 10);
        saveTask = getServer().getAsyncScheduler().runAtFixedRate(this, task -> saveVariables(false), seconds, seconds,
                TimeUnit.SECONDS);
    }

    private void saveVariables(boolean always) {
        VariableStorage current = storage;
        if (current == null || !current.loaded() || (!always && !current.changed())) {
            return;
        }
        try {
            VariableStorage.Result saved = current.save();
            if (saved.unsaved() > 0) {
                getSLF4JLogger().warn("{} variables have values that can't be saved yet, so they weren't", saved.unsaved());
            }
        } catch (IOException | RuntimeException e) {
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
        co.xenastudios.neoskript.runtime.region.Regions.uninstall();
        if (commands != null) {
            commands.unregister(getServer().getCommandMap());
        }
        saveVariables(false);
    }
}
