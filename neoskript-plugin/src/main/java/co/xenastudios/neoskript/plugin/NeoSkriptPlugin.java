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
import co.xenastudios.neoskript.runtime.variables.storage.NativeVariableStorage;
import co.xenastudios.neoskript.runtime.variables.storage.VariableBackend;
import co.xenastudios.neoskript.runtime.variables.storage.VariableBackends;
import co.xenastudios.neoskript.runtime.variables.storage.VariableStorage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoSkriptPlugin extends JavaPlugin {

    private VariableBackend storage;
    /** Whether the saved variables couldn't be opened or loaded, so scripts run without them and nothing is saved. */
    private boolean variablesUnavailable;
    /** Whether {@code variables.recover} was on for this start. */
    private boolean recoverVariables;
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
    private SettingsReloader settingsReloader;
    /** The addons registered through the API. */
    private Addons addons;

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
        addons = new Addons(getSLF4JLogger());
        addons.frozenWhen(library::frozen);
        addons.knownWhen(library::known);
        addons.savedWhen(library::saves);
        // With addons, which enable after NeoSkript, the syntax is registered on the first tick, theirs with it, and
        // variables load after that, so the addons' types load too. Without them, nothing waits.
        boolean deferSyntax = Addons.anyInstalled(this) && !co.xenastudios.neoskript.plugin.check.CheckMode.requested();
        Runnable registerSyntax = () -> {
            StandardLibrary.register(library, registrar -> {
                if (hooks) {
                    co.xenastudios.neoskript.paper.Hooks.register(registrar, getPluginMeta().getVersion(), getSLF4JLogger());
                }
                // Addons' syntax goes with the hooks': before comparisons and what matches almost anything.
                addons.registerAll(registrar);
            });
            StandardLibrary.usePlayerUuidsInVariableNames(settings.variables().playerUuidsInNames());
        };
        if (!deferSyntax) {
            registerSyntax.run();
        }
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
            if (!deferSyntax) {
                StandardLibrary.registerTesting(library);
            }
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
        storage = openVariables(scripts.globals(), library.codecs());
        if (!deferSyntax) {
            loadVariables();
        }
        AddonApi api = new AddonApi(this, addons, scripts.globals(), getSLF4JLogger());
        co.xenastudios.neoskript.api.Installed.install(api);
        // An addon disables before NeoSkript when the server stops, and its classes go with it. Its values in
        // variables become their saved bytes now, so later saves, after unload events, never need its code.
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onAddonDisable(org.bukkit.event.server.PluginDisableEvent event) {
                if (!Addons.isAddon(NeoSkriptPlugin.this, event.getPlugin())) {
                    return;
                }
                addons.remove(event.getPlugin().getName());
                VariableBackend current = storage;
                if (current != null && current.loaded()) {
                    VariableStorage.Detached detached = current.detach(event.getPlugin().getClass().getClassLoader());
                    if (!detached.kept().isEmpty()) {
                        getSLF4JLogger().info("{} was disabled: kept {} {} its codecs save as saved bytes",
                                event.getPlugin().getName(), detached.kept().size(),
                                detached.kept().size() == 1 ? "variable" : "variables");
                    }
                    if (!detached.notSaved().isEmpty()) {
                        getSLF4JLogger().warn("{} was disabled, and {} couldn't be encoded and had nothing saved before:"
                                + " they keep their values until the server stops, but won't be saved",
                                event.getPlugin().getName(), String.join(", ", detached.notSaved()));
                    }
                }
            }
        }, this);
        Runnable start = () -> Startup.run(() -> {
            if (deferSyntax) {
                registerSyntax.run();
                if (TestRunner.enabled()) {
                    StandardLibrary.registerTesting(library);
                }
                loadVariables();
            }
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
            // Scripts and variables are there now (or what of them could load): addons waiting for them carry on.
        }, api::ready, getSLF4JLogger());
        if (hooks || deferSyntax) {
            // As Skript: with plugins to hook into, taking Vault's services and then loading scripts wait for the
            // server to start, when every plugin has enabled and registered its services. With addons, so does
            // registering the syntax, for theirs.
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
        // What /sk reload config compares with: the settings the server started with.
        settingsReloader = new SettingsReloader(configFile(), settings, reloadEffects());
        getCommand("skript").setExecutor(new SkriptCommand(this, scripts));
    }

    /** The addons registered through NeoSkript's API, as {@code name version}, for {@code /sk info}. */
    java.util.List<String> registeredAddons() {
        return addons == null ? java.util.List.of() : addons.names();
    }

    /** The plugins that registered addons through NeoSkript's API, by name. */
    java.util.Set<String> addonPlugins() {
        return addons == null ? java.util.Set.of() : addons.plugins();
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
            Message.theme(NeoSkriptConfig.DEFAULTS.theme());
            settings = NeoSkriptConfig.DEFAULTS;
        } catch (RuntimeException | StackOverflowError e) {
            getSLF4JLogger().error("Couldn't read config.yml. Running on the default settings until it's fixed and reloaded"
                    + " with /sk reload config.", e);
            Message.theme(NeoSkriptConfig.DEFAULTS.theme());
            settings = NeoSkriptConfig.DEFAULTS;
        }
    }

    /** Uses what loading config.yml found, and logs what it changed and what it couldn't use. */
    private void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
        // The theme first: if it fails, the settings stay as they were.
        Message.theme(result.config().theme());
        settings = result.config();
        result.notices().forEach(notice -> getSLF4JLogger().info(notice));
        result.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
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
            ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, configFile(), copied);
            change.notices().forEach(notice -> getSLF4JLogger().info(notice));
            change.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
            if (!change.written()) {
                getSLF4JLogger().warn("Skript's settings weren't copied to config.yml: {}. Set them there by hand.",
                        String.join(", ", copied.keySet()));
                return;
            }
            accept(ConfigLoader.load(NeoSkriptConfig.SPEC, configFile()));
        } catch (ConfigException e) {
            getSLF4JLogger().error("Couldn't copy Skript's settings to config.yml: {}", e.getMessage());
            return;
        } catch (RuntimeException | StackOverflowError e) {
            getSLF4JLogger().error("Couldn't copy Skript's settings to config.yml", e);
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

    /** {@code /sk reload config}. */
    SettingsReloader.Outcome reloadSettings() {
        return settingsReloader.reload();
    }

    /** What applying settings on {@code /sk reload config} does to the running server. */
    private SettingsReloader.Effects reloadEffects() {
        return new SettingsReloader.Effects() {
            @Override
            public void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
                NeoSkriptPlugin.this.accept(result);
            }

            @Override
            public void playerUuids(boolean uuids) {
                StandardLibrary.usePlayerUuidsInVariableNames(uuids);
            }

            @Override
            public void effectCommands(EffectCommands.Settings settings) {
                effectCommands.settings(settings);
            }

            @Override
            public void runtimeErrors(NeoSkriptConfig.RuntimeErrors limits) {
                runtimeErrorFrames.run();
                runtimeErrorFrames = RuntimeErrorConsole.start(limits, scheduler);
            }

            @Override
            public void saveInterval(NeoSkriptConfig.Interval interval) {
                if (saveTask != null) {
                    saveTask.cancel();
                    scheduleSaves();
                }
            }

            @Override
            public void updateChecker(boolean enabled, boolean turnedOn) {
                if (updates == null) {
                    return;
                }
                updates.enabled(enabled);
                if (turnedOn) {
                    getServer().getAsyncScheduler().runNow(NeoSkriptPlugin.this, task -> {
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

            @Override
            public void failed(String message) {
                getSLF4JLogger().error("{} The running settings are kept.", message);
            }
        };
    }

    /** The priority triggers listen to events at, {@code high} by default as in Skript. */
    private EventPriority eventPriority() {
        return settings.events().priority().bukkit();
    }

    /**
     * Where variables are saved, from {@code variables.storage}: {@code csv} (Skript's variables.csv, the default)
     * or {@code native} (NeoSkript's own file). Switching moves the variables over, after a backup. If that fails,
     * {@code null}: the variables stay where they are and aren't loaded or saved.
     */
    private VariableBackend openVariables(co.xenastudios.neoskript.runtime.variables.VariableStore globals,
            co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs codecs) {
        java.nio.file.Path csv = getDataPath().resolve(settings.variables().file());
        String setting = settings.variables().storage();
        VariableBackends.Kind kind = VariableBackends.Kind.parse(setting);
        if (kind == null) {
            // Keep using the file that's in use, rather than moving the variables because of a typo.
            kind = java.nio.file.Files.exists(VariableBackends.nativeFile(csv)) ? VariableBackends.Kind.NATIVE
                    : VariableBackends.Kind.CSV;
            getSLF4JLogger().warn("Unknown variables.storage '{}' in config.yml (csv or native); using {}", setting,
                    kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        try {
            VariableBackends.Opened opened = VariableBackends.open(kind, globals, codecs, csv);
            opened.log().forEach(line -> getSLF4JLogger().info(line));
            opened.warnings().forEach(line -> getSLF4JLogger().warn(line));
            // variables.recover: a one-shot repair of a damaged native file, turned off again once it's run.
            recoverVariables = settings.variables().recover();
            if (opened.backend() instanceof NativeVariableStorage nsv) {
                nsv.recover(recoverVariables);
            }
            return opened.backend();
        } catch (VariableBackends.Conflict e) {
            getSLF4JLogger().error(e.getMessage());
            variablesUnavailable();
            return null;
        } catch (IOException | RuntimeException e) {
            getSLF4JLogger().error("Couldn't read the saved variables ({} storage). The variable files are left as "
                    + "they were.", kind.name().toLowerCase(java.util.Locale.ROOT), e);
            variablesUnavailable();
            return null;
        }
    }

    /** Sets variables.recover back to false in config.yml, once a recovery has run. */
    private void turnOffRecover() {
        try {
            ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, configFile(),
                    java.util.Map.of("variables.recover", false));
            change.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
            if (change.written()) {
                getSLF4JLogger().info("Turned variables.recover off again in config.yml");
            } else {
                getSLF4JLogger().warn("Couldn't turn variables.recover off in config.yml; set it to false by hand, or "
                        + "the next start recovers again.");
            }
        } catch (ConfigException | RuntimeException e) {
            getSLF4JLogger().warn("Couldn't turn variables.recover off in config.yml; set it to false by hand, or the "
                    + "next start recovers again: {}", e.getMessage());
        }
    }

    /** Says plainly that scripts run without their saved variables, and that nothing is saved. */
    private void variablesUnavailable() {
        variablesUnavailable = true;
        getSLF4JLogger().error("Saved variables weren't loaded. Scripts run without them, and nothing they set is "
                + "saved, until this is fixed and the server restarts.");
    }

    /** Loads saved variables, then saves changed ones every {@code variables.save-interval}. */
    private void loadVariables() {
        if (storage == null) {
            // The saved variables couldn't be opened, which was said then.
            return;
        }
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
                if (loaded.backup() != null) {
                    getSLF4JLogger().info("Created a backup of {} as {}", storage.file().getFileName(),
                            getDataPath().toAbsolutePath().relativize(loaded.backup().toAbsolutePath()));
                } else {
                    getSLF4JLogger().info("{} is backed up before the first save writes it again",
                            storage.file().getFileName());
                }
                getSLF4JLogger().warn("Skipped {} {} of {} that couldn't be read; the other variables loaded. "
                        + "The next save leaves those lines out, and the backup keeps them.", count,
                        count == 1 ? "line" : "lines", storage.file().getFileName());
            }
            if (storage instanceof NativeVariableStorage nsv && nsv.recoveredBytes() > 0) {
                getSLF4JLogger().warn("The last {} bytes of {} were a save cut off by a crash; every save before it "
                        + "loaded. The first save drops them, after backing the file up.", nsv.recoveredBytes(),
                        storage.file().getFileName());
            }
            storage.notes().forEach(line -> getSLF4JLogger().warn(line));
            if (recoverVariables) {
                turnOffRecover();
            }
            getSLF4JLogger().info("Loaded {} {} in {} ms{}", loaded.variables(), loaded.variables() == 1 ? "variable" : "variables",
                    (System.nanoTime() - start) / 1_000_000,
                    loaded.raw() == 0 ? "" : " (" + loaded.raw() + " kept as saved until NeoSkript supports their types)");
        } catch (IOException | RuntimeException | LinkageError e) {
            // Don't run with an empty set of variables that would then overwrite the file. The storage never saves
            // until it has loaded, so nothing writes over the file, not even when the server stops.
            getSLF4JLogger().error("Couldn't load variables from {}. Variables won't be saved until it's fixed.",
                    storage.file(), e);
            storage.notes().forEach(line -> getSLF4JLogger().warn(line));
            try {
                storage.close();
            } catch (IOException closing) {
                e.addSuppressed(closing);
            }
            storage = null;
            variablesUnavailable();
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
        VariableBackend current = storage;
        if (current == null || !current.loaded() || (!always && !current.changed())) {
            return;
        }
        try {
            VariableStorage.Result saved = current.save();
            if (saved.unsaved() > 0) {
                getSLF4JLogger().warn("{} variables have values that can't be saved yet, so they weren't", saved.unsaved());
            }
            // Variables whose codec failed, or whose addon's classes are gone: the rest were saved.
            if (!saved.keptPrevious().isEmpty()) {
                getSLF4JLogger().warn("Couldn't save the new values of {}, so they keep what was saved before",
                        String.join(", ", saved.keptPrevious()));
            }
            if (!saved.notSaved().isEmpty()) {
                getSLF4JLogger().warn("Couldn't save {}, which had no value saved before, so they were left out",
                        String.join(", ", saved.notSaved()));
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            getSLF4JLogger().error("Couldn't save variables to {}: {}", current.file(), e.getMessage(), e);
        }
        current.notes().forEach(line -> getSLF4JLogger().warn(line));
    }

    @Override
    public void onDisable() {
        co.xenastudios.neoskript.api.Installed.install(null);
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
        if (variablesUnavailable) {
            getSLF4JLogger().error("Variables weren't saved: they couldn't be loaded when the server started (see the "
                    + "error then), so the variable files are as they were and changes since are lost.");
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (IOException e) {
                getSLF4JLogger().warn("Couldn't close {}", storage.file(), e);
            }
        }
    }
}
