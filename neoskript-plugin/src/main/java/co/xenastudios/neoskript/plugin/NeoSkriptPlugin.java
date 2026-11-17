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

    private co.xenastudios.neoskript.runtime.variables.storage.Databases storage;
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
    /** bStats, whose thread must stop with the plugin. */
    private Metrics metrics;
    private AliasManager aliases;

    /** https://bstats.org/plugin/bukkit/NeoSkript/34868 */
    private static final int BSTATS_PLUGIN_ID = 34868;

    @Override
    public void onEnable() {
        co.xenastudios.neoskript.runtime.PausingSections.install(message -> getSLF4JLogger().warn(message));
        // D10: NeoSkript replaces Skript, so running both would fight over scripts and commands. By the plugin's own
        // name: NeoSkriptCompat provides "Skript" for Skript addons (D22), and isn't Skript.
        if (skriptRunning(getServer().getPluginManager().getPlugin("Skript"))) {
            getSLF4JLogger().error(
                    "Skript is installed. NeoSkript replaces Skript, so they can't run together. "
                            + "Remove Skript.jar from the plugins folder to use NeoSkript. NeoSkript is now disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Carrying an old config.yml forward with an unknown variables.storage keeps the file that's in use.
        NeoSkriptConfig.nativeInUse = file -> java.nio.file.Files.exists(
                VariableBackends.nativeFile(getDataPath().resolve(file)));
        loadSettings();
        Platform platform = Platform.detect();
        // D15: bStats, opt-out with `metrics: false` (or globally in plugins/bStats/config.yml).
        if (settings.metrics()) {
            metrics = new Metrics(this, BSTATS_PLUGIN_ID);
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
                java.util.List<String> copied = migration.copied();
                String what = copied.size() == 1 ? copied.getFirst()
                        : String.join(", ", copied.subList(0, copied.size() - 1)) + " and " + copied.getLast();
                if (copied.contains("scripts")) {
                    getSLF4JLogger().info("Copied {} from plugins/Skript ({} {}). Skript's folder was left as it was.",
                            what, migration.scriptCount(), migration.scriptCount() == 1 ? "script" : "scripts");
                } else {
                    getSLF4JLogger().info("Copied {} from plugins/Skript. Skript's folder was left as it was.", what);
                }
            }
            migration.notes().forEach(note -> getSLF4JLogger().warn(note));
            if (migration.variablesBackup() != null) {
                getSLF4JLogger().info("Backed up Skript's variables as {} before loading them",
                        getDataPath().toAbsolutePath().relativize(migration.variablesBackup().toAbsolutePath()));
            }
            java.nio.file.Path skriptDir = getDataPath().resolveSibling("Skript");
            if (copySkriptSettings(migration)) {
                // Only now: if config.yml couldn't take Skript's settings, the next start copies them again.
                SkriptMigration.finish(getDataPath(), skriptDir, migration.copied(), migration.aliasesDone());
            }
            String notice = SkriptMigration.databasesNotice(skriptDir, getDataPath(), settings.databases().all()
                    .stream().anyMatch(d -> d.type() == DatabaseSettings.Type.SQLITE
                            || d.type() == DatabaseSettings.Type.MYSQL));
            if (notice != null) {
                getSLF4JLogger().warn(notice);
            }
        } catch (IOException e) {
            getSLF4JLogger().error("Couldn't copy Skript's scripts and variables", e);
        }

        Library library = new Library();
        this.library = library;
        if (TestRunner.enabled()) {
            library.enableTesting();
        }
        // Syntax of the plugins NeoSkript hooks into, as Skript's hooks, only when they're installed.
        // config.yml's hooks, which apply on restart, and verbosity, which logs each plugin hooked into from high.
        java.util.Set<String> unhooked = new java.util.HashSet<>();
        if (!settings.hooks().vault()) {
            unhooked.add("Vault");
        }
        if (!settings.hooks().worldguard()) {
            unhooked.add("WorldGuard");
        }
        if (!settings.hooks().griefPrevention()) {
            unhooked.add("GriefPrevention");
        }
        co.xenastudios.neoskript.paper.Hooks.configure(unhooked,
                settings.verbosity().compareTo(NeoSkriptConfig.Verbosity.HIGH) >= 0);
        boolean hooks = co.xenastudios.neoskript.paper.Hooks.anyInstalled();
        addons = new Addons(getSLF4JLogger());
        addons.frozenWhen(library::frozen);
        addons.knownWhen(library::known);
        addons.savedWhen(library::saves);
        addons.registeringWith(library::registeringAddon);
        // With addons, which enable after NeoSkript, the syntax is registered on the first tick, theirs with it, and
        // variables load after that, so the addons' types load too. Without them, nothing waits.
        boolean deferSyntax = Addons.anyInstalled(this) && !co.xenastudios.neoskript.plugin.check.CheckMode.requested();
        // The standard library and the hooks register now, with or without addons, so addons enabling after
        // NeoSkript (and Skript addons, through NeoSkriptCompat) can ask what types there are. Addons' syntax goes
        // with the hooks': before comparisons and what matches almost anything. With addons, it's registered on the
        // first tick, at that place.
        StandardLibrary.register(library, registrar -> {
            if (hooks) {
                co.xenastudios.neoskript.paper.Hooks.register(registrar, getPluginMeta().getVersion(), getSLF4JLogger());
            }
            if (deferSyntax) {
                library.markAddonPoint();
            } else {
                addons.registerAll(registrar);
            }
        });
        StandardLibrary.usePlayerUuidsInVariableNames(settings.variables().playerUuidsInNames());
        Runnable registerSyntax = () -> library.insertingAtMark(() -> addons.registerAll(library));
        aliases = new AliasManager(getDataPath(), getSLF4JLogger());
        // The settings scripts read, before any script loads.
        applyScriptSettings(settings);
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
        // Skript's default aliases and the alias files, before any script is compiled.
        aliases.load(settings.aliases().loadDefaults());
        scheduler = platform == Platform.FOLIA ? new FoliaScheduler(this) : new PaperScheduler(this);
        // Runtime errors go to the console as Skript prints them, limited per frame by config.yml.
        runtimeErrorFrames = RuntimeErrorConsole.start(settings.runtimeErrors(), scheduler);
        java.util.function.Consumer<co.xenastudios.neoskript.runtime.ScriptRuntimeException> laterErrors = e -> {
            // A command failed after a wait just stops, as there's no one left to tell.
            if (!(e.getCause() instanceof co.xenastudios.neoskript.runtime.command.CommandEvent.Failed)) {
                TriggerErrors.report(getSLF4JLogger(), e);
            }
        };
        Waits.install(scheduler, laterErrors);
        // Recursion that runs out of stack is told to admins as Skript tells them.
        co.xenastudios.neoskript.runtime.Overflows.install(TriggerErrors::broadcastRepeated);
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
        // Skript's legacy tab completion compatibility, when config.yml turns it off.
        getServer().getPluginManager().registerEvent(org.bukkit.event.server.TabCompleteEvent.class,
                new org.bukkit.event.Listener() {}, org.bukkit.event.EventPriority.HIGHEST,
                (listener, event) -> ScriptCommand.keepOwnCompletions((org.bukkit.event.server.TabCompleteEvent) event),
                this, false);
        storage = openVariables(scripts.globals(), library.codecs());
        if (!deferSyntax) {
            loadVariables();
        }
        AddonApi api = new AddonApi(this, addons, scripts.globals(), library, getSLF4JLogger());
        addonApi = api;
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
            } catch (IOException | RuntimeException e) {
                // NeoSkript stays enabled, so /sk reload can load them once they're fixed.
                getSLF4JLogger().error("Couldn't load the scripts", e);
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

    private Library library;

    /**
     * The library scripts are compiled against: for NeoSkriptCompat, which registers Skript addons' syntax into it
     * (D22). Not part of the addon API.
     */
    public Library library() {
        return library;
    }

    /**
     * Whether {@code plugin}, what the server has under the name "Skript", is Skript itself rather than a plugin that
     * only provides that name, as NeoSkriptCompat does (D22).
     */
    static boolean skriptRunning(org.bukkit.plugin.Plugin plugin) {
        return plugin != null && plugin.getName().equals("Skript");
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
    /** Whether config.yml has them now, or there were none to copy. */
    private boolean copySkriptSettings(SkriptMigration.Result migration) {
        if (migration.caseClashes() != null) {
            caseClashes(migration);
        }
        migration.notCopied().forEach(note -> getSLF4JLogger().info(note));
        if (migration.settingsProblem() != null) {
            getSLF4JLogger().warn("Couldn't read Skript's config.sk ({}), so its settings weren't copied. NeoSkript runs"
                    + " on its defaults, which are Skript's; set any you changed in config.yml.", migration.settingsProblem());
        }
        java.util.Map<String, Object> copied = new java.util.LinkedHashMap<>(migration.settings());
        org.bukkit.configuration.file.YamlConfiguration current =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile().toFile());
        copied.entrySet().removeIf(setting -> setting.getValue().equals(current.get(setting.getKey())));
        if (copied.isEmpty()) {
            return true;
        }
        try {
            ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, configFile(), copied);
            change.notices().forEach(notice -> getSLF4JLogger().info(notice));
            change.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
            if (!change.written()) {
                getSLF4JLogger().warn("Skript's settings weren't copied to config.yml: {}. NeoSkript tries again at "
                        + "the next start, or set them there by hand.", String.join(", ", copied.keySet()));
                return false;
            }
            accept(ConfigLoader.load(NeoSkriptConfig.SPEC, configFile()));
        } catch (ConfigException e) {
            getSLF4JLogger().error("Couldn't copy Skript's settings to config.yml: {}", e.getMessage());
            return false;
        } catch (RuntimeException | StackOverflowError e) {
            getSLF4JLogger().error("Couldn't copy Skript's settings to config.yml", e);
            return false;
        }
        getSLF4JLogger().info("Copied {} from Skript's config.sk to config.yml: {}", copied.size() == 1 ? "a setting"
                : copied.size() + " settings", String.join(", ", copied.keySet()));
        if (copied.containsKey("variables.player-uuids-in-names")) {
            getSLF4JLogger().info("Players are their {} in variable names, as in Skript's config.sk.",
                    settings.variables().playerUuidsInNames() ? "UUIDs" : "names");
        }
        if (copied.containsKey("aliases.load-defaults")) {
            getSLF4JLogger().info("Skript's default aliases are {}, as in Skript's config.sk.",
                    settings.aliases().loadDefaults() ? "loaded" : "not loaded");
        }
        if (copied.keySet().stream().anyMatch(path -> path.startsWith("effect-commands."))) {
            getSLF4JLogger().info("Effect commands are set up as in Skript's config.sk.");
        }
        if (copied.containsKey("databases")) {
            getSLF4JLogger().info("Variables are saved in the databases Skript's config.sk has: {}.",
                    settings.databases().all().stream().map(DatabaseSettings.Database::toString)
                            .collect(java.util.stream.Collectors.joining(", ")));
        }
        return true;
    }

    /**
     * Skript's {@code case-insensitive variables} was off, which NeoSkript doesn't have: its variable names always
     * ignore case. Says so, and names the variables that now read as one, loudly, as only one of each is kept.
     */
    private void caseClashes(SkriptMigration.Result migration) {
        if (migration.caseClashes().isEmpty()) {
            getSLF4JLogger().warn("Skript's config.sk has 'case-insensitive variables: false'. NeoSkript's variable names"
                    + " always ignore case, as Skript's do by default: {coins::Bob} and {coins::bob} are the same"
                    + " variable. No saved variables differ only by case, so none are affected.");
            return;
        }
        int count = migration.caseClashes().size();
        getSLF4JLogger().error("Skript's config.sk has 'case-insensitive variables: false', but NeoSkript's variable"
                + " names always ignore case. {} {} of saved variables differ only by case, and NeoSkript keeps only one"
                + " of each (the last in {}); the next save drops the others. Skript's own file is left as it was{}. Rename"
                + " them in your scripts and variables before relying on them:", count, count == 1 ? "group" : "groups",
                migration.caseClashFile() == null ? "the file" : shown(migration.caseClashFile()),
                migration.variablesBackup() == null ? ""
                        : ", and the copy NeoSkript read is backed up as " + shown(migration.variablesBackup()));
        int shown = 0;
        for (java.util.List<String> names : migration.caseClashes()) {
            if (shown++ == 50) {
                getSLF4JLogger().error("  ... and {} more", count - 50);
                break;
            }
            getSLF4JLogger().error("  {}", names.stream().map(name -> "{" + name + "}")
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    /** A path as the console shows it: relative to NeoSkript's folder when it's in it. */
    private String shown(java.nio.file.Path path) {
        java.nio.file.Path absolute = path.toAbsolutePath().normalize();
        java.nio.file.Path folder = getDataPath().toAbsolutePath().normalize();
        return absolute.startsWith(folder) ? "plugins/NeoSkript/" + folder.relativize(absolute).toString().replace('\\', '/')
                : absolute.toString();
    }

    /** Applies the settings scripts read, logging what couldn't be used. */
    private void applyScriptSettings(NeoSkriptConfig settings) {
        ScriptSettings.apply(settings, getDataPath().resolve("lang"))
                .forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
    }

    /** The settings from config.yml. */
    NeoSkriptConfig settings() {
        return settings;
    }

    /**
     * {@code aliases.load-defaults} as config.yml has it now, for {@code /sk reload aliases}, which reads it again
     * as Skript reads its setting. If the file can't be read or the value isn't true or false, the running setting.
     */
    boolean loadDefaultAliases() {
        boolean running = settings.aliases().loadDefaults();
        try {
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            config.load(configFile().toFile());
            if (config.isBoolean("aliases.load-defaults")) {
                return config.getBoolean("aliases.load-defaults");
            }
            if (config.contains("aliases.load-defaults")) {
                getSLF4JLogger().warn("config.yml: aliases.load-defaults isn't true or false, so it stays {}", running);
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException e) {
            getSLF4JLogger().warn("Couldn't read aliases.load-defaults from config.yml, so it stays {}: {}", running,
                    e.getMessage());
        }
        return running;
    }

    /** The alias files, for {@code /sk reload aliases}. */
    AliasManager aliases() {
        return aliases;
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
            public void scripts(NeoSkriptConfig settings) {
                applyScriptSettings(settings);
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
     * Where variables are saved, from the {@code databases} section: each database, in order, with Skript's routing by
     * pattern. A database that can't be opened (a switch between csv and native that can't be finished, a setting
     * that's wrong) keeps its pattern, so its variables aren't saved anywhere else, and the console hears why.
     */
    private co.xenastudios.neoskript.runtime.variables.storage.Databases openVariables(
            co.xenastudios.neoskript.runtime.variables.VariableStore globals,
            co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs codecs) {
        // variables.recover: a one-shot recovery of a damaged native file. It's turned off in config.yml before it's
        // used, so it can never stay on and recover later damage nobody chose to; if it can't be, it isn't used.
        recoverVariables = settings.variables().recover() && turnOffRecover();
        co.xenastudios.neoskript.runtime.variables.storage.Console console = console();
        java.util.List<co.xenastudios.neoskript.runtime.variables.storage.Databases.Definition> definitions =
                new java.util.ArrayList<>();
        for (DatabaseSettings.Database database : settings.databases().enabled()) {
            definitions.add(new co.xenastudios.neoskript.runtime.variables.storage.Databases.Definition(
                    database.name(), database.typeText(), database.pattern(),
                    routing -> openDatabase(database, globals, codecs, routing, console)));
        }
        return co.xenastudios.neoskript.runtime.variables.storage.Databases.open(globals, codecs, definitions, console);
    }

    /** One database's storage. */
    private VariableBackend openDatabase(DatabaseSettings.Database database,
            co.xenastudios.neoskript.runtime.variables.VariableStore globals,
            co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs codecs,
            co.xenastudios.neoskript.runtime.variables.storage.Routing routing,
            co.xenastudios.neoskript.runtime.variables.storage.Console console) throws IOException {
        if (database.problem() != null) {
            throw new IOException(database.problem());
        }
        java.nio.file.Path data = getDataPath();
        switch (database.type()) {
            case CSV, NATIVE -> {
                VariableBackends.Kind kind = database.type() == DatabaseSettings.Type.NATIVE
                        ? VariableBackends.Kind.NATIVE : VariableBackends.Kind.CSV;
                VariableBackends.Opened opened = VariableBackends.open(kind, globals, codecs,
                        data.resolve(database.file()), recoverVariables, routing);
                opened.log().forEach(line -> getSLF4JLogger().info(line));
                opened.warnings().forEach(line -> getSLF4JLogger().warn(line));
                if (opened.backend() instanceof NativeVariableStorage nsv) {
                    nsv.recover(recoverVariables);
                }
                return opened.backend();
            }
            case SQLITE, MYSQL -> {
                co.xenastudios.neoskript.runtime.variables.storage.SqlVariableStorage.Settings sql =
                        database.type() == DatabaseSettings.Type.SQLITE
                                ? co.xenastudios.neoskript.runtime.variables.storage.SqlVariableStorage.Settings.sqlite(
                                        database.name(), data.resolve(database.file()), database.table(),
                                        database.monitorChanges(), database.monitorInterval().millis())
                                : co.xenastudios.neoskript.runtime.variables.storage.SqlVariableStorage.Settings.mysql(
                                        database.name(), database.host(), database.port(), database.database(),
                                        database.user(), database.password(), database.table(),
                                        database.monitorChanges(), database.monitorInterval().millis());
                // Kept per database, by a hash of its type, where it is and its table.
                return new co.xenastudios.neoskript.runtime.variables.storage.SqlVariableStorage(globals, codecs, sql,
                        routing, data.resolve("pending"), console);
            }
            default -> throw new IOException("type " + database.typeText() + " can't hold variables");
        }
    }

    /** Where background storage tells the console what happened. */
    private co.xenastudios.neoskript.runtime.variables.storage.Console console() {
        org.slf4j.Logger logger = getSLF4JLogger();
        return new co.xenastudios.neoskript.runtime.variables.storage.Console() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }

            @Override
            public void error(String message) {
                logger.error(message);
            }
        };
    }

    /** The databases variables are saved in, for {@code /sk info}; empty if they couldn't be opened. */
    java.util.List<co.xenastudios.neoskript.runtime.variables.storage.Databases.Database> databases() {
        return storage == null ? java.util.List.of() : storage.databases();
    }

    /** Sets variables.recover back to false in config.yml, once a recovery has run. */
    /**
     * Sets variables.recover back to false in config.yml before a recovery may run, and whether that worked. If it
     * didn't, the switch isn't used: left on, it would recover damage found later without anyone choosing to.
     */
    private boolean turnOffRecover() {
        String why;
        try {
            ConfigLoader.Change change = NeoSkriptConfig.turnOffRecover(configFile());
            change.warnings().forEach(warning -> getSLF4JLogger().warn("config.yml: {}", warning));
            if (change.written()) {
                getSLF4JLogger().info("variables.recover is on for this start, and is turned off again in config.yml");
                accept(ConfigLoader.load(NeoSkriptConfig.SPEC, configFile()));
                return true;
            }
            why = "it couldn't be written (see above)";
        } catch (ConfigException | RuntimeException e) {
            why = e.getMessage();
        }
        getSLF4JLogger().error("variables.recover is on, but it couldn't be turned off again in config.yml ({}), so it "
                + "isn't used: left on, it would recover damage found later without you choosing to. Make config.yml "
                + "writable (or fix it), then restart with it on again.", why);
        return false;
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
                    getSLF4JLogger().info("Created a backup of {} as {}", loaded.backup().getFileName(),
                            getDataPath().toAbsolutePath().relativize(loaded.backup().toAbsolutePath()));
                } else {
                    getSLF4JLogger().info("{} is backed up before the first save writes it again",
                            storage.describe());
                }
                getSLF4JLogger().warn("Skipped {} {} of {} that couldn't be read; the other variables loaded. "
                        + "The next save leaves those lines out, and the backup keeps them.", count,
                        count == 1 ? "line" : "lines", storage.describe());
            }
            for (var database : storage.databases()) {
                if (database.backend() instanceof NativeVariableStorage nsv && nsv.recoveredBytes() > 0) {
                    getSLF4JLogger().warn("The last {} bytes of {} were a save cut off by a crash; every save before "
                            + "it loaded. The first save drops them, after backing the file up.", nsv.recoveredBytes(),
                            nsv.file().getFileName());
                }
            }
            storage.notes().forEach(line -> getSLF4JLogger().warn(line));
            // Skript doesn't say at config.yml's 'verbosity: low'.
            if (Logging.logs(NeoSkriptConfig.Verbosity.NORMAL)) {
                getSLF4JLogger().info("Loaded {} {} in {} ms{}", loaded.variables(),
                        loaded.variables() == 1 ? "variable" : "variables", (System.nanoTime() - start) / 1_000_000,
                        loaded.raw() == 0 ? "" : " (" + loaded.raw() + " kept as saved until NeoSkript supports their types)");
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            // Don't run with an empty set of variables that would then overwrite the file. The storage never saves
            // until it has loaded, so nothing writes over the file, not even when the server stops.
            getSLF4JLogger().error("Couldn't load variables from {}. Variables won't be saved until it's fixed.",
                    storage.describe(), e);
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
        scheduleBackups();
    }

    /**
     * Backs up each file database every {@code backup-interval}, keeping the newest {@code backups-to-keep}, as Skript
     * does (VariablesStorage): an interval of {@code 0} is no backups, and nothing is removed; with
     * {@code backups-to-keep: 0} there are no backups and that file's scheduled ones are removed; an interval shorter
     * than a tick makes none. Intervals are used as they're written, however short, as Skript's are.
     */
    private void scheduleBackups() {
        java.util.Map<String, co.xenastudios.neoskript.runtime.variables.storage.Databases.Database> open =
                new java.util.HashMap<>();
        for (var database : storage.databases()) {
            open.put(database.name(), database);
        }
        for (DatabaseSettings.Database settings : this.settings.databases().enabled()) {
            var database = open.get(settings.name());
            if (!settings.type().file() || database == null || database.backend() == null
                    || database.failure() != null) {
                continue;
            }
            VariableBackend backend = database.backend();
            BackupPlan plan = BackupPlan.of(settings.backupInterval(), settings.backupsToKeep());
            if (plan.purgeAll()) {
                purgeBackups(backend, 0);
            }
            if (plan.periodMillis() <= 0) {
                continue;
            }
            long period = plan.periodMillis();
            getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
                try {
                    java.nio.file.Path backup = backend.backupNow();
                    if (backup != null) {
                        getSLF4JLogger().info("Backed up {} as {}", backend.describe(),
                                getDataPath().toAbsolutePath().relativize(backup.toAbsolutePath()));
                    }
                } catch (IOException | RuntimeException e) {
                    // Skript's message.
                    getSLF4JLogger().error("Automatic variables backup failed: {}", e.getMessage());
                    return;
                }
                purgeBackups(backend, settings.backupsToKeep());
            }, period, period, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * What a database's backup settings do, as Skript's {@code VariablesStorage} decides: {@code purgeAll} removes the
     * scheduled backups once, and a backup is made every {@code periodMillis} if it's above 0.
     */
    record BackupPlan(boolean purgeAll, long periodMillis) {

        static BackupPlan of(NeoSkriptConfig.Interval interval, int keep) {
            if (interval == null) {
                // 'backup interval: 0': Skript does nothing at all, and removes nothing.
                return new BackupPlan(false, 0);
            }
            if (keep == 0) {
                return new BackupPlan(true, 0);
            }
            // Skript's task runs every so many ticks: under one, it doesn't start. Otherwise exactly as written.
            long ticks = interval.millis() / 50;
            return new BackupPlan(false, ticks * 50);
        }
    }

    private void purgeBackups(VariableBackend backend, int keep) {
        if (keep < 0 || backend.file() == null) {
            return;
        }
        try {
            VariableStorage.purgeBackups(backend.file(), keep);
        } catch (IOException | RuntimeException e) {
            // Skript's message.
            getSLF4JLogger().error("Automatic variables backup purge failed: {}", e.getMessage());
        }
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
            getSLF4JLogger().error("Couldn't save variables to {}: {}", current.describe(), e.getMessage(), e);
        }
        current.notes().forEach(line -> getSLF4JLogger().warn(line));
    }

    /** The API addons reach, once it's installed. */
    private AddonApi addonApi;

    /** How long the server's stop waits, at most, for what its unload triggers paused for. */
    private static final java.time.Duration PAUSE_GRACE = java.time.Duration.ofSeconds(3);

    @Override
    public void onDisable() {
        co.xenastudios.neoskript.api.Installed.install(null);
        if (metrics != null) {
            // bStats' thread would otherwise outlive the plugin, and keep it in memory, through /reload.
            metrics.shutdown();
        }
        if (runtimeErrorFrames != null) {
            runtimeErrorFrames.run();
        }
        // What the unload triggers pause for gets a moment to finish, below.
        co.xenastudios.neoskript.runtime.Pauses.stopping();
        if (scripts != null) {
            co.xenastudios.neoskript.paper.EventBridges.stop();
            scripts.unloadAll();
        }
        // After the unload triggers, which may have started work an addon pauses for (a last database write): it
        // gets a moment to finish before the server exits. Then triggers still waiting are dropped, as after a
        // wait, and the pauses' timer stops.
        co.xenastudios.neoskript.runtime.Pauses.close(PAUSE_GRACE);
        // Addons that kept what scripts use until now (a database's connections) let go of it.
        AddonApi api = addonApi;
        if (api != null) {
            api.stopped();
        }
        getServer().getAsyncScheduler().cancelTasks(this);
        if (storage != null) {
            // A backup still copying a big file is cut off, so the last save and the close don't wait for it.
            storage.cancelBackup();
        }
        getServer().getGlobalRegionScheduler().cancelTasks(this);
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
                getSLF4JLogger().warn("Couldn't close {}", storage.describe(), e);
            }
        }
        // Last, once nothing scheduled can run any more: a task cancelled after this could have opened a log again.
        StandardLibrary.close();
    }
}
