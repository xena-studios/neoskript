/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.compiler.CompiledCommand;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.PeriodicalEvent;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import org.bukkit.Bukkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * Loads and reloads the scripts in {@code plugins/NeoSkript/scripts}: parses and compiles them, reports
 * problems the way Skript does, and runs their triggers. Files whose names start with {@code -} are
 * disabled, as in Skript.
 *
 * <p>A reload compiles everything before anything changes, then every script switches to its new version
 * together. As in Skript, a script with errors loads without what has them (D12): a line that fails is left
 * out, and a structure whose header fails. A script NeoSkript itself fails to compile part of (its bug, not the
 * script's mistake) keeps the version that was running, and the error says so; if it can't compile at all, nothing
 * changes.
 */
final class ScriptManager {

    /** The start of the error for what NeoSkript itself failed to compile, and what's added when that's kept. */
    private static final String COULDNT_COMPILE = "NeoSkript couldn't compile this (";
    private static final String KEPT = " The version that was running keeps running.";

    /** What a load or reload did. */
    record Outcome(int scripts, int errors, long millis) {}

    private final Path scriptsDir;
    private final Library library;
    private final Scheduler scheduler;
    private final EventDispatcher events;
    private final ScriptCommands commands;
    private final Logger logger;
    /** Global variables. */
    private final VariableStore globals = new VariableStore();
    /** Function handles, kept across reloads so callers always reach the current version. */
    private final Map<FunctionInfo, FunctionHandle> functions = new ConcurrentHashMap<>();
    /** The triggers of each loaded script. */
    private final Map<String, List<CompiledScripts.CompiledTrigger>> loaded = new LinkedHashMap<>();
    /** What stops each loaded script's periodic triggers ({@code every 5 minutes}). */
    private final Map<String, List<Runnable>> periodic = new HashMap<>();
    /** The source of each loaded script, as it loaded. */
    private final Map<String, String> sources = new HashMap<>();
    /** The commands of each loaded script. */
    private final Map<String, List<CompiledCommand>> loadedCommands = new LinkedHashMap<>();

    /** Each loaded script's {@code auto reload}, if it has one. */
    private final Map<String, AutoReload> autoReloads = new HashMap<>();
    /** The item names each script looked up the last time the scripts were compiled. */
    private final Map<String, Set<String>> itemNames = new HashMap<>();

    /** The scripts that had errors the last time they were compiled, which new aliases may fix. */
    private final Set<String> withErrors = new HashSet<>();

    /** When each script's file was last modified, as of the last time the scripts were read. */
    private final Map<String, Long> modified = new HashMap<>();

    /** In test mode, what to do after the tests ran (stop the server); otherwise {@code null}. */
    private Runnable afterTests;

    ScriptManager(Path scriptsDir, Library library, Scheduler scheduler, EventDispatcher events,
            ScriptCommands commands, Logger logger, Runnable afterTests) {
        this.scriptsDir = scriptsDir;
        this.library = library;
        this.scheduler = scheduler;
        this.events = events;
        this.commands = commands;
        this.logger = logger;
        this.afterTests = afterTests;
        co.xenastudios.neoskript.runtime.FunctionLookup.install(new FunctionSource());
    }

    /** Finds the loaded scripts' functions, and the built-in ones, for {@code function "name"}. */
    private final class FunctionSource implements co.xenastudios.neoskript.runtime.FunctionLookup.Source {
        @Override
        public co.xenastudios.neoskript.runtime.FunctionRef find(String name, String script) {
            co.xenastudios.neoskript.runtime.FunctionRef global = null;
            for (Map.Entry<FunctionInfo, FunctionHandle> entry : functions.entrySet()) {
                FunctionInfo info = entry.getKey();
                if (!info.name().equals(name) || !entry.getValue().loaded()) {
                    continue;
                }
                if (info.localTo() == null) {
                    global = ref(info, entry.getValue());
                } else if (info.localTo().equals(script)) {
                    return ref(info, entry.getValue());
                }
            }
            if (global != null) {
                return global;
            }
            for (Map.Entry<FunctionInfo, co.xenastudios.neoskript.api.element.Function> entry : library.builtIns().entrySet()) {
                if (entry.getKey().name().equals(name)) {
                    co.xenastudios.neoskript.api.element.Function function = entry.getValue();
                    return new co.xenastudios.neoskript.runtime.FunctionRef(name, null, plurals(entry.getKey()), function::call);
                }
            }
            return null;
        }

        @Override
        public List<co.xenastudios.neoskript.runtime.FunctionRef> definedIn(String script) {
            List<co.xenastudios.neoskript.runtime.FunctionRef> defined = new ArrayList<>();
            for (Map.Entry<FunctionInfo, FunctionHandle> entry : functions.entrySet()) {
                if (script.equals(entry.getValue().script()) && entry.getValue().loaded()) {
                    defined.add(ref(entry.getKey(), entry.getValue()));
                }
            }
            return defined;
        }

        private co.xenastudios.neoskript.runtime.FunctionRef ref(FunctionInfo info, FunctionHandle handle) {
            return new co.xenastudios.neoskript.runtime.FunctionRef(info.name(), info.localTo(), plurals(info),
                    arguments -> handle.callKeyed(null, arguments));
        }

        private List<Boolean> plurals(FunctionInfo info) {
            return info.parameters().stream().map(FunctionInfo.Parameter::plural).toList();
        }
    }

    /**
     * As Skript at config.yml's {@code verbosity: high}, how many structures each script has, and at {@code very high}
     * each structure as it loads.
     */
    private void logStructures(List<ParsedScript> parsed, Predicate<String> scope) {
        if (!Logging.logs(NeoSkriptConfig.Verbosity.HIGH)) {
            return;
        }
        for (ParsedScript script : parsed) {
            String name = script.file().name();
            if (scope != null && !scope.test(name)) {
                continue;
            }
            if (Logging.logs(NeoSkriptConfig.Verbosity.VERY_HIGH)) {
                for (ParsedStructure structure : script.structures()) {
                    logger.info("loading trigger '{}'", structure.structure().node().line().key().strip());
                }
            }
            int count = script.structures().size();
            logger.info("loaded {} structure{} from '{}'", count, count == 1 ? "" : "s", name);
        }
    }

    /**
     * Loads every script when the server starts, on its first tick, as Skript does. Load events run straight away,
     * before the scripts are said to be loaded and before anything else that tick (a console command) runs.
     */
    synchronized void loadAll() throws IOException {
        Outcome outcome = load(null, problem -> {}, true, ScriptManager::toConsole);
        int triggers = loaded.values().stream().mapToInt(List::size).sum();
        // Skript doesn't say at config.yml's 'verbosity: low'.
        if (!Logging.logs(NeoSkriptConfig.Verbosity.NORMAL)) {
            return;
        }
        logger.info("Loaded {} {} with {} {} in {} ms{}", outcome.scripts(), outcome.scripts() == 1 ? "script" : "scripts",
                triggers, triggers == 1 ? "trigger" : "triggers", outcome.millis(),
                outcome.errors() == 0 ? "" : " (" + outcome.errors() + (outcome.errors() == 1 ? " error" : " errors") + ")");
    }

    /**
     * Reloads the scripts whose names {@code scope} accepts, or every script if it's {@code null}, for
     * {@code /skript}. Every problem (warnings too) goes to {@code problems} rather than the log, as
     * Skript's command redirects them to whoever reloaded. Called on the global region's thread, so load
     * events run straight away, before the command says it's done, as in Skript.
     */
    synchronized Outcome reload(Predicate<String> scope, BiConsumer<Diagnostic, SourceFile> problems) throws IOException {
        return load(scope, problem -> {}, true, problems);
    }

    /**
     * The scripts that a change to the aliases may change, for {@code /sk reload aliases}: those whose item names,
     * as they looked them up when they were compiled (options put in), include one of the {@code changed} names (in
     * lower case), and those that had errors, which the new aliases may fix. With {@code changed} {@code null},
     * every script ({@code null}).
     */
    synchronized Predicate<String> affectedBy(Set<String> changed) {
        if (changed == null) {
            return null;
        }
        Set<String> affected = new HashSet<>(withErrors);
        itemNames.forEach((script, names) -> {
            for (String name : names) {
                if (changed.contains(name)) {
                    affected.add(script);
                    return;
                }
            }
        });
        return affected::contains;
    }

    /**
     * Unloads the loaded scripts whose names {@code scope} accepts, running their unload events, as Skript
     * unloads scripts it disables.
     */
    synchronized void unload(Predicate<String> scope) {
        for (String name : List.copyOf(loaded.keySet())) {
            if (scope.test(name)) {
                stop(name);
            }
        }
    }

    /** The names of the loaded scripts, in the order they loaded. */
    synchronized List<String> loadedScripts() {
        return List.copyOf(loaded.keySet());
    }

    /** The source of each loaded script as it was when it loaded, for {@code /skript restore}. */
    synchronized Map<String, String> loadedSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : loaded.keySet()) {
            String source = this.sources.get(name);
            if (source != null) {
                sources.put(name, source);
            }
        }
        return sources;
    }

    /** The scripts folder. */
    Path scriptsFolder() {
        return scriptsDir;
    }

    /** Global variables, for storage to load and save. */
    VariableStore globals() {
        return globals;
    }

    /**
     * Compiles one effect on its own, for an effect command: it can call the loaded scripts' global functions
     * and use the global variables, and its code runs in events of {@code eventClass}. The loaded scripts aren't
     * touched, so it needn't wait for a reload.
     */
    ScriptCompiler.CompiledLine compileEffect(String effect, Class<?> eventClass) {
        co.xenastudios.neoskript.frontend.registry.SyntaxRegistry syntax = declaredSyntax;
        return new ScriptCompiler(library, globals, functions).compileLine(EFFECT_COMMAND, effect, eventClass,
                EFFECT_COMMAND, syntax == null ? library.registry() : syntax);
    }

    /** The syntax the loaded scripts declared, with the rest, for effect commands; {@code null} before any load. */
    private volatile co.xenastudios.neoskript.frontend.registry.SyntaxRegistry declaredSyntax;

    /** What effect commands' code is called, as the script it runs in and its event. */
    private static final String EFFECT_COMMAND = "effect command";

    /** Loads the scripts {@code scope} accepts, or every script if it's {@code null}. */
    private Outcome load(Predicate<String> scope, Consumer<String> problems) throws IOException {
        return load(scope, problems, false);
    }

    /** With {@code now}, load events run straight away rather than on the next tick: for loads on the global thread. */
    private Outcome load(Predicate<String> scope, Consumer<String> problems, boolean now) throws IOException {
        return load(scope, problems, now, null);
    }

    /** The scripts being loaded whose triggers don't all listen yet, or {@code null}: see {@link LoadWalk}. */
    private volatile LoadWalk walk;

    /**
     * With {@code redirect}, every problem (warnings too) goes there instead of the log and
     * {@code problems}: for {@code auto reload}, which sends them to its recipients.
     */
    private Outcome load(Predicate<String> scope, Consumer<String> problems, boolean now,
            BiConsumer<Diagnostic, SourceFile> redirect) throws IOException {
        long start = System.nanoTime();
        Files.createDirectories(scriptsDir);
        // Everything is parsed and compiled, even for one script, so calls between scripts resolve.
        List<SourceFile> files = read();
        List<ParsedScript> parsed;
        CompiledScripts compiled;
        ScriptCompiler compiler = new ScriptCompiler(library, globals, functions);
        try {
            ScriptLoader loader = new ScriptLoader(library.registry());
            parsed = loader.load(files);
            declaredSyntax = loader.declaredRegistry();
            logStructures(parsed, scope);
            // Which item names each script looks up, so /sk reload aliases reloads the scripts a change affects.
            co.xenastudios.neoskript.paper.GlobalAliases.recordUses();
            try {
                compiled = compiler.compile(parsed);
            } finally {
                Map<String, Set<String>> uses = co.xenastudios.neoskript.paper.GlobalAliases.recordedUses();
                itemNames.clear();
                uses.forEach((script, names) -> itemNames.put(script, Set.copyOf(names)));
            }
        } catch (StackOverflowError | LinkageError e) {
            // NeoSkript's failure, not the scripts': nothing has changed yet, so every script keeps running the
            // version it had (D12).
            throw new IllegalStateException("Couldn't compile the scripts: " + e, e);
        }

        Map<String, SourceFile> byName = new HashMap<>();
        files.forEach(file -> byName.put(file.name(), file));
        List<Diagnostic> diagnostics = new ArrayList<>();
        parsed.forEach(script -> script.diagnostics().stream().filter(d -> !compiled.unparsed(d)).forEach(diagnostics::add));
        // NeoSkript's failure, not the script's: a script it failed to compile something in keeps the version that
        // was running, if one was (D12), and the error says so.
        Set<String> kept = new HashSet<>();
        for (String name : compiled.crashed()) {
            if (loaded.containsKey(name) && (scope == null || scope.test(name))) {
                kept.add(name);
            }
        }
        for (Diagnostic diagnostic : compiled.diagnostics()) {
            if (kept.contains(diagnostic.file()) && diagnostic.message().startsWith(COULDNT_COMPILE)) {
                diagnostic = new Diagnostic(diagnostic.severity(), diagnostic.file(), diagnostic.line(),
                        diagnostic.column(), diagnostic.length(), diagnostic.message() + KEPT);
            }
            diagnostics.add(diagnostic);
        }
        // The problems in the order Skript gives them while it loads the scripts together, though NeoSkript finds some
        // while parsing and others while compiling: see MessageOrder. Problems with the files' layout come first, and
        // then the scripts' come interleaved, by stage and priority, as Skript runs each stage across all of them.
        List<Diagnostic> ordered = co.xenastudios.neoskript.frontend.source.MessageOrder.sortAll(diagnostics, files,
                d -> layoutProblem(d.message()));
        diagnostics.clear();
        diagnostics.addAll(ordered);
        Set<String> failed = new HashSet<>();
        int errors = 0;
        // A report lists each script's problems together, the scripts in the order they're loaded.
        List<Diagnostic> listed = redirect == null ? diagnostics
                : co.xenastudios.neoskript.frontend.source.MessageOrder.byScript(diagnostics, files);
        for (Diagnostic diagnostic : listed) {
            if (scope != null && !scope.test(diagnostic.file())) {
                continue;
            }
            if (redirect != null) {
                redirect.accept(diagnostic, byName.get(diagnostic.file()));
                if (diagnostic.severity() == Severity.ERROR) {
                    failed.add(diagnostic.file());
                    errors++;
                }
                continue;
            }
            if (diagnostic.severity() != Severity.ERROR) {
                // Warnings are logged as warnings, as Skript does, and don't stop a script loading.
                logger.warn(describe(diagnostic, byName.get(diagnostic.file())));
                continue;
            }
            failed.add(diagnostic.file());
            errors++;
            problems.accept(describe(diagnostic, byName.get(diagnostic.file())));
        }

        for (SourceFile file : files) {
            if (scope == null || scope.test(file.name())) {
                if (failed.contains(file.name())) {
                    withErrors.add(file.name());
                } else {
                    withErrors.remove(file.name());
                }
            }
        }
        withErrors.retainAll(byName.keySet());
        // Every script switches to its new version, as in Skript: one with errors, without what has them (D12).
        // In the files' order, which Skript loads them in: their load events run in it.
        Set<String> switching = new java.util.LinkedHashSet<>();
        // Scripts whose code stops: reloaded or gone.
        Set<String> stopped = new java.util.LinkedHashSet<>();
        int inScope = 0;
        for (SourceFile file : files) {
            String name = file.name();
            if (scope == null || scope.test(name)) {
                inScope++;
                if (!kept.contains(name)) {
                    switching.add(name);
                }
            }
        }
        if (scope == null) {
            // Scripts that are gone run their unload events first.
            loaded.forEach((name, old) -> {
                if (!byName.containsKey(name)) {
                    runUnload(old);
                    stopped.add(name);
                }
            });
            loaded.keySet().removeIf(name -> !byName.containsKey(name));
            sources.keySet().removeIf(name -> !byName.containsKey(name));
            loadedCommands.keySet().removeIf(name -> !byName.containsKey(name));
            for (String name : List.copyOf(periodic.keySet())) {
                if (!byName.containsKey(name)) {
                    periodic.remove(name).forEach(Runnable::run);
                }
            }
            for (String name : List.copyOf(autoReloads.keySet())) {
                if (!byName.containsKey(name)) {
                    autoReloads.remove(name).stop();
                }
            }
        }
        compiled.linkFunctions(switching);
        // Syntax a switching script no longer declares goes, and loaded scripts still using it are named.
        for (String warning : compiled.switchSyntax(switching, functions, byName.keySet())) {
            logger.warn(warning);
        }
        Map<String, List<CompiledScripts.CompiledTrigger>> triggers = new LinkedHashMap<>();
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            if (switching.contains(trigger.script())) {
                triggers.computeIfAbsent(trigger.script(), k -> new ArrayList<>()).add(trigger);
            }
        }
        Map<String, List<CompiledCommand>> scriptCommands = new LinkedHashMap<>();
        for (CompiledCommand command : compiled.commands()) {
            if (switching.contains(command.script())) {
                scriptCommands.computeIfAbsent(command.script(), k -> new ArrayList<>()).add(command);
            }
        }
        for (String name : switching) {
            if (loaded.containsKey(name)) {
                stopped.add(name);
            }
        }
        for (String name : stopped) {
            if (!switching.contains(name)) {
                co.xenastudios.neoskript.runtime.ScriptVersions.unloaded(name);
            }
        }
        for (String name : switching) {
            // This compile is now the script's loaded version: code from the one before knows it isn't.
            co.xenastudios.neoskript.runtime.ScriptVersions.load(name, compiler.version(name));
        }
        for (String name : switching) {
            // The previous version of a script unloads before the new one loads.
            runUnload(loaded.getOrDefault(name, List.of()));
            loaded.put(name, triggers.getOrDefault(name, List.of()));
            sources.put(name, byName.get(name).text());
            loadedCommands.put(name, scriptCommands.getOrDefault(name, List.of()));
        }
        // In the files' order, though scripts were added or enabled since: addons told of their triggers get them so.
        reorder(loaded, files);
        reorder(loadedCommands, files);
        // Skript's load order: each script in turn, each of its triggers in the order of its lines. Skript registers
        // each event's trigger when it gets to it and runs each 'on load' then, so what an 'on load' does reaches
        // only the triggers before it, and those of the scripts that weren't loaded again.
        List<CompiledScripts.CompiledTrigger> order = new ArrayList<>();
        for (String name : switching) {
            List<CompiledScripts.CompiledTrigger> own = new ArrayList<>(loaded.get(name));
            own.sort(java.util.Comparator.comparingInt(CompiledScripts.CompiledTrigger::line));
            order.addAll(own);
        }
        int firstLoad = 0;
        while (firstLoad < order.size() && order.get(firstLoad).event() != ScriptEvent.LOAD) {
            firstLoad++;
        }
        if (walk != null) {
            // An earlier load's walk that hasn't run yet: its triggers listen now, as these scripts load after it.
            walk.finish();
        }
        for (CompiledScripts.CompiledTrigger trigger : order) {
            registeredAt.put(trigger.trigger(), registrations++);
        }
        LoadWalk loadWalk = firstLoad == order.size() ? null
                : new LoadWalk(order.stream().map(CompiledScripts.CompiledTrigger::trigger).toList(), firstLoad);
        walk = loadWalk;
        events.walk(loadWalk);
        updateListenersAndCommands();
        tellUnloaded(stopped);
        long millis = (System.nanoTime() - start) / 1_000_000;

        if (loadWalk != null) {
            Runnable walking = () -> walk(order, loadWalk);
            if (now) {
                walking.run();
            } else {
                scheduler.runGlobal(walking);
            }
        }
        for (String name : switching) {
            periodic.getOrDefault(name, List.of()).forEach(Runnable::run);
            List<Runnable> stops = new ArrayList<>();
            for (CompiledScripts.CompiledTrigger trigger : loaded.get(name)) {
                if (trigger.event() == ScriptEvent.LOAD) {
                    continue;
                } else if (trigger.period() != null && trigger.worlds() != null) {
                    // As in Skript, each world's runs come when its full time is a multiple of the period.
                    long ticks = Math.max(trigger.period().ticks(), 1);
                    for (String worldName : trigger.worlds()) {
                        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                        if (world != null) {
                            PeriodicalEvent event = new PeriodicalEvent(world);
                            stops.add(scheduler.runGlobalRepeating(ticks - Math.floorMod(world.getFullTime(), ticks),
                                    ticks, () -> run(trigger, event)));
                        }
                    }
                } else if (trigger.period() != null) {
                    PeriodicalEvent event = new PeriodicalEvent(null);
                    stops.add(scheduler.runGlobalRepeating(trigger.period().ticks(), () -> run(trigger, event)));
                }
            }
            periodic.put(name, stops);
        }
        watch(switching, parsed);
        if (afterTests != null) {
            Runnable done = afterTests;
            afterTests = null;
            // As in Skript's test mode, tests start once the world has had time to load and tick its chunks. They
            // run where test-location is, at the first world's spawn: on Folia, on the thread of the region that
            // owns it, as Skript's tests act on blocks and entities there (on Paper, the main thread).
            org.bukkit.Location testLocation = org.bukkit.Bukkit.getWorlds().getFirst().getSpawnLocation();
            scheduler.runLater(testLocation, 100, () -> TestRunner.run(compiled, logger, scheduler, testLocation, done));
        }
        return new Outcome(inScope, errors, millis);
    }

    /**
     * Starts watching the scripts in {@code switching} that have Skript's {@code auto reload}, and stops
     * watching their previous versions.
     */
    private void watch(Set<String> switching, List<ParsedScript> parsed) {
        for (String name : switching) {
            AutoReload old = autoReloads.remove(name);
            if (old != null) {
                old.stop();
            }
        }
        for (ParsedScript script : parsed) {
            String name = script.file().name();
            if (!switching.contains(name)) {
                continue;
            }
            ParsedStructure.AutoReload structure = null;
            for (ParsedStructure s : script.structures()) {
                if (s instanceof ParsedStructure.AutoReload autoReload) {
                    structure = autoReload;
                }
            }
            if (structure == null) {
                continue;
            }
            AutoReload autoReload = new AutoReload(name, structure, modified.getOrDefault(name, 0L));
            autoReloads.put(name, autoReload);
            autoReload.started(scheduler.runGlobalRepeating(AutoReload.PERIOD, () -> checkAutoReload(name, autoReload)));
        }
    }

    /** Reloads a script with {@code auto reload} if its file changed since it was last loaded. */
    private synchronized void checkAutoReload(String name, AutoReload autoReload) {
        if (autoReloads.get(name) != autoReload) {
            return;
        }
        Path path = path(new co.xenastudios.neoskript.runtime.ScriptRef(name));
        long lastModified;
        try {
            lastModified = path == null ? 0 : Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return;
        }
        if (path == null || lastModified <= autoReload.lastReload()) {
            return;
        }
        autoReload.lastReload(lastModified);
        long start = System.nanoTime();
        List<org.bukkit.command.CommandSender> audience = autoReload.audience();
        net.kyori.adventure.text.Component what = Messages.highlight(name);
        Messages.send(audience, Messages.RELOADING, Messages.text("doing", "Automatically reloading"),
                Messages.component("what", what));
        ScriptReport report = new ScriptReport("sk");
        try {
            // On the global region's thread, so load events run straight away, as for /sk reload.
            load(name::equals, problem -> {}, true, report);
            report.send(audience, "Automatically reloaded", what, (System.nanoTime() - start) / 1_000_000);
        } catch (IOException | RuntimeException e) {
            logger.error("Exception occurred while automatically reloading a script ({})", name, e);
        }
    }

    private List<SourceFile> read() throws IOException {
        List<SourceFile> files = new ArrayList<>();
        modified.clear();
        for (Path path : skriptOrder(scriptsDir)) {
            if (isLoadable(path)) {
                modified.put(name(path), Files.getLastModifiedTime(path).toMillis());
                files.add(new SourceFile(name(path), Files.readString(path)));
            }
        }
        return files;
    }

    /**
     * The files in {@code folder} in the order Skript loads scripts ({@code ScriptLoader.loadStructures}): each
     * folder's entries sorted, its subfolders first, each walked the same way, then its own files. So
     * {@code b/x.sk} comes before {@code a.sk}. Load events and compile errors follow this order.
     */
    static List<Path> skriptOrder(Path folder) throws IOException {
        List<Path> entries;
        try (Stream<Path> list = Files.list(folder)) {
            entries = list.sorted().toList();
        }
        List<Path> ordered = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                ordered.addAll(skriptOrder(entry));
            } else {
                files.add(entry);
            }
        }
        ordered.addAll(files);
        return ordered;
    }

    /**
     * A script's name: its path relative to the scripts folder. Skript's test runner loads its tests from
     * outside the scripts folder, so in test mode names can start with what that path would be
     * ({@code neoskript.testing.scriptsRoot}), as Skript's tests expect.
     */
    String name(Path path) {
        String relative = scriptsDir.relativize(path).toString().replace('\\', '/');
        return NAME_ROOT.isEmpty() ? relative : NAME_ROOT + "/" + relative;
    }

    private static final String NAME_ROOT = System.getProperty("neoskript.testing.scriptsRoot", "");

    /** The scripts folder's files, for {@link co.xenastudios.neoskript.runtime.ScriptCatalog}. */
    co.xenastudios.neoskript.runtime.ScriptCatalog catalog() {
        return new co.xenastudios.neoskript.runtime.ScriptCatalog() {
            @Override
            public co.xenastudios.neoskript.runtime.ScriptRef find(String name) {
                String relative = name.replace('\\', '/');
                if (!NAME_ROOT.isEmpty() && relative.startsWith(NAME_ROOT + "/")) {
                    relative = relative.substring(NAME_ROOT.length() + 1);
                }
                for (String candidate : List.of(relative, relative + ".sk")) {
                    Path path = scriptsDir.resolve(candidate).normalize();
                    if (path.startsWith(scriptsDir) && Files.isRegularFile(path) && candidate.endsWith(".sk")) {
                        return new co.xenastudios.neoskript.runtime.ScriptRef(name(path));
                    }
                }
                return null;
            }

            @Override
            public String source(co.xenastudios.neoskript.runtime.ScriptRef script) {
                Path path = path(script);
                try {
                    return path == null ? null : Files.readString(path);
                } catch (java.io.IOException e) {
                    return null;
                }
            }

            @Override
            public boolean loaded(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    return loaded.containsKey(script.name());
                }
            }

            @Override
            public co.xenastudios.neoskript.runtime.ScriptRef load(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    Path path = path(script);
                    if (path == null) {
                        return null;
                    }
                    if (path.getFileName().toString().startsWith("-")) {
                        path = rename(path, path.getFileName().toString().substring(1));
                        if (path == null) {
                            return null;
                        }
                    }
                    String name = name(path);
                    if (loaded.containsKey(name)) {
                        return null;
                    }
                    reloadNow(name);
                    return new co.xenastudios.neoskript.runtime.ScriptRef(name);
                }
            }

            @Override
            public void reload(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    Path path = path(script);
                    if (path != null && isEnabledScript(path)) {
                        reloadNow(name(path));
                    }
                }
            }

            @Override
            public void unload(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    stop(script.name());
                }
            }

            @Override
            public co.xenastudios.neoskript.runtime.ScriptRef disable(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    Path path = path(script);
                    if (path == null || path.getFileName().toString().startsWith("-")) {
                        return null;
                    }
                    stop(script.name());
                    Path disabled = rename(path, "-" + path.getFileName());
                    return disabled == null ? null : new co.xenastudios.neoskript.runtime.ScriptRef(name(disabled));
                }
            }

            @Override
            public List<co.xenastudios.neoskript.runtime.ScriptRef> scripts(boolean wantLoaded, boolean disabled) {
                List<co.xenastudios.neoskript.runtime.ScriptRef> scripts = new ArrayList<>();
                try (Stream<Path> paths = Files.walk(scriptsDir)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".sk") && Files.isRegularFile(p)).sorted().toList()) {
                        boolean enabled = isEnabledScript(path);
                        if ((enabled && wantLoaded) || (!enabled && disabled)) {
                            scripts.add(new co.xenastudios.neoskript.runtime.ScriptRef(name(path)));
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Couldn't list the scripts folder", e);
                }
                return scripts;
            }
        };
    }

    /** The file of {@code script} in the scripts folder, or {@code null} if there isn't one. */
    private Path path(co.xenastudios.neoskript.runtime.ScriptRef script) {
        String relative = script.name();
        if (!NAME_ROOT.isEmpty() && relative.startsWith(NAME_ROOT + "/")) {
            relative = relative.substring(NAME_ROOT.length() + 1);
        }
        Path path = scriptsDir.resolve(relative).normalize();
        return path.startsWith(scriptsDir) && Files.isRegularFile(path) ? path : null;
    }

    /** Renames a script file in its folder, returning its new path, or {@code null} if it couldn't be. */
    private Path rename(Path path, String newName) {
        try {
            return Files.move(path, path.resolveSibling(newName));
        } catch (IOException e) {
            logger.warn("Couldn't rename {}", path, e);
            return null;
        }
    }

    /** Loads (or reloads) one script, running its load events straight away. */
    private void reloadNow(String name) {
        try {
            load(name::equals, problem -> {}, true, ScriptManager::toConsole);
        } catch (IOException e) {
            logger.error("Couldn't load {}", name, e);
        }
    }

    /** Stops a loaded script, running its unload events, and stops its triggers, commands and periodic events. */
    private void stop(String name) {
        List<CompiledScripts.CompiledTrigger> old = loaded.remove(name);
        if (old != null) {
            runUnload(old);
        }
        sources.remove(name);
        loadedCommands.remove(name);
        List<Runnable> stops = periodic.remove(name);
        if (stops != null) {
            stops.forEach(Runnable::run);
        }
        AutoReload autoReload = autoReloads.remove(name);
        if (autoReload != null) {
            autoReload.stop();
        }
        updateListenersAndCommands();
        if (old != null) {
            co.xenastudios.neoskript.runtime.ScriptVersions.unloaded(name);
            tellUnloaded(Set.of(name));
        }
    }

    /** Tells addons ({@code Registrar.scriptsUnloaded}) which scripts' code stopped: reloaded or gone. */
    private void tellUnloaded(Set<String> stopped) {
        if (stopped.isEmpty()) {
            return;
        }
        Set<String> names = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(stopped));
        for (var watcher : library.unloadWatchers()) {
            try {
                watcher.accept(names);
            } catch (RuntimeException | LinkageError e) {
                logger.error("An addon failed when told which scripts' code stopped", e);
            }
        }
    }

    /**
     * When each loaded trigger was registered, by identity: as in Skript, an event's triggers run in that order, so
     * those of a script reloaded on its own run after the others'.
     */
    private final Map<co.xenastudios.neoskript.runtime.Trigger, Long> registeredAt = new java.util.IdentityHashMap<>();
    private long registrations;

    /** The loaded triggers, by identity, as last listened for: one not among them has unloaded or reloaded. */
    private volatile Set<CompiledScripts.CompiledTrigger> current = Set.of();

    /**
     * Listens for the loaded scripts' events and registers their commands. Each goes ahead even if the other
     * fails, so a server that refuses one doesn't leave the scripts half switched over.
     */
    private void updateListenersAndCommands() {
        List<CompiledScripts.CompiledTrigger> all = new ArrayList<>(loaded.values().stream().flatMap(List::stream).toList());
        Set<CompiledScripts.CompiledTrigger> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        now.addAll(all);
        current = now;
        Set<co.xenastudios.neoskript.runtime.Trigger> listening = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        all.forEach(trigger -> listening.add(trigger.trigger()));
        registeredAt.keySet().retainAll(listening);
        all.sort(java.util.Comparator.comparingLong(trigger -> registeredAt.getOrDefault(trigger.trigger(), Long.MAX_VALUE)));
        try {
            events.update(all);
        } catch (RuntimeException e) {
            logger.error("Couldn't listen for the scripts' events", e);
        }
        try {
            commands.update(loadedCommands.values().stream().flatMap(List::stream).toList());
        } catch (RuntimeException e) {
            logger.error("Couldn't register the scripts' commands", e);
        }
        tellWatchers();
    }

    /**
     * Whether a file is a script to load: an enabled script not inside a disabled ({@code -}) or hidden
     * folder, which Skript doesn't load from either.
     */
    private boolean isLoadable(Path path) {
        if (!isEnabledScript(path)) {
            return false;
        }
        Path folders = scriptsDir.relativize(path).getParent();
        if (folders != null) {
            for (Path folder : folders) {
                String folderName = folder.toString();
                if (folderName.startsWith("-") || folderName.startsWith(".")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEnabledScript(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".sk") && !name.startsWith("-") && Files.isRegularFile(path);
    }

    /** Runs the unload events ({@code on script unload}) of a script's triggers. */
    private void runUnload(List<CompiledScripts.CompiledTrigger> triggers) {
        for (CompiledScripts.CompiledTrigger trigger : triggers) {
            if (trigger.event() == ScriptEvent.UNLOAD) {
                run(trigger);
            }
        }
    }

    /**
     * Tells the addons that run their events themselves ({@code Registrar.eventTriggers}) which of those events'
     * triggers are loaded now.
     */
    private void tellWatchers() {
        var watchers = library.triggerWatchers();
        if (watchers.isEmpty()) {
            return;
        }
        List<CompiledScripts.CompiledTrigger> all = loaded.values().stream().flatMap(List::stream).toList();
        // What's loaded now: a trigger an addon still holds from before runs nothing (LoadedTrigger.run).
        Set<CompiledScripts.CompiledTrigger> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        now.addAll(all);
        live = now;
        for (var entry : watchers.entrySet()) {
            List<co.xenastudios.neoskript.api.registration.LoadedTrigger> triggers = all.stream()
                    .filter(trigger -> entry.getKey().equals(trigger.eventId()))
                    .map(trigger -> (co.xenastudios.neoskript.api.registration.LoadedTrigger) new Loaded(trigger, this, logger))
                    .toList();
            for (var watcher : entry.getValue()) {
                try {
                    watcher.accept(triggers);
                } catch (RuntimeException | LinkageError e) {
                    logger.error("An addon failed when told which triggers of its event {} are loaded", entry.getKey(), e);
                }
            }
        }
    }

    /**
     * Goes through the triggers of the scripts loaded, in Skript's load order: each {@code on load} runs when it's
     * reached, and each other trigger listens from then on.
     */
    private void walk(List<CompiledScripts.CompiledTrigger> order, LoadWalk loadWalk) {
        try {
            walk(order, loadWalk, trigger -> current.contains(trigger), this::run);
        } finally {
            loadWalk.finish();
            synchronized (this) {
                if (walk == loadWalk) {
                    walk = null;
                    events.walk(null);
                }
            }
        }
    }

    /**
     * {@link #walk(List, LoadWalk)}'s steps: each {@code on load} that's still {@code loaded} runs when it's reached,
     * and each trigger listens from then on. One whose script has unloaded or reloaded since, as an {@code on load}
     * before it may have had done, doesn't run.
     */
    static void walk(List<CompiledScripts.CompiledTrigger> order, LoadWalk loadWalk,
            Predicate<CompiledScripts.CompiledTrigger> loaded, Consumer<CompiledScripts.CompiledTrigger> run) {
        for (int i = 0; i < order.size(); i++) {
            CompiledScripts.CompiledTrigger trigger = order.get(i);
            if (trigger.event() == ScriptEvent.LOAD && loaded.test(trigger)) {
                run.accept(trigger);
            }
            loadWalk.reach(i);
        }
    }

    /** The loaded triggers, by identity, as addons running their own events were last told. */
    private volatile Set<CompiledScripts.CompiledTrigger> live = Set.of();

    /** A loaded trigger of an event an addon runs itself. */
    private record Loaded(CompiledScripts.CompiledTrigger compiled, ScriptManager manager, Logger logger)
            implements co.xenastudios.neoskript.api.registration.LoadedTrigger {

        @Override
        public String script() {
            return compiled.script();
        }

        @Override
        public int line() {
            return compiled.line();
        }

        @Override
        public co.xenastudios.neoskript.api.element.Condition filter() {
            return compiled.filter();
        }

        @Override
        public void run(Object event, Object owner) {
            if (!manager.live.contains(compiled)) {
                // Its script has unloaded or reloaded since: its code doesn't run any more.
                return;
            }
            LoadWalk loading = manager.walk;
            if (loading != null && loading.pending(compiled.trigger())) {
                // As in Skript, an 'on load' before it in the load order caused the event: it isn't registered yet.
                return;
            }
            try {
                compiled.trigger().execute(new SimpleTriggerContext(event, owner));
            } catch (Throwable e) {
                // As any trigger's: never to the addon that ran it.
                TriggerErrors.report(logger, e);
            }
        }
    }

    /** Puts {@code byScript}'s entries in the order of {@code files}, any others after them. */
    private static <V> void reorder(Map<String, V> byScript, List<SourceFile> files) {
        Map<String, V> ordered = new LinkedHashMap<>();
        for (SourceFile file : files) {
            V value = byScript.get(file.name());
            if (value != null) {
                ordered.put(file.name(), value);
            }
        }
        byScript.forEach(ordered::putIfAbsent);
        byScript.clear();
        byScript.putAll(ordered);
    }

    /** Runs every loaded script's unload events, as the server stops. */
    synchronized void unloadAll() {
        loaded.values().forEach(this::runUnload);
    }

    /** Runs a script's {@code on load} or {@code on unload} trigger. */
    private void run(CompiledScripts.CompiledTrigger trigger) {
        run(trigger, new co.xenastudios.neoskript.runtime.ScriptLoadEvent(trigger.script(),
                trigger.event() == ScriptEvent.LOAD));
    }

    /** Runs {@code trigger} in {@code event}, which may be {@code null}. */
    private void run(CompiledScripts.CompiledTrigger trigger, Object event) {
        try {
            trigger.trigger().execute(new SimpleTriggerContext(event));
        } catch (Throwable e) {
            TriggerErrors.report(logger, e);
        }
    }

    /** Sends a problem to the console as Skript's log does: through the console, with its colors. */
    private static void toConsole(Diagnostic problem, SourceFile file) {
        AutoReload.send(List.of(Bukkit.getConsoleSender()), AutoReload.SKRIPT_PREFIX + AutoReload.describe(problem, file));
    }

    /** Whether a problem is with the file's layout, which Skript reports as it reads the file, before the code. */
    private static boolean layoutProblem(String message) {
        return message.startsWith("indentation error") || message.startsWith("Empty configuration section!");
    }

    /** A problem as Skript reports it: the line and file, the message, then the line itself. */
    private static String describe(Diagnostic problem, SourceFile file) {
        String code = "";
        if (file != null && problem.line() >= 1 && problem.line() <= file.lines().size()) {
            code = file.lines().get(problem.line() - 1).strip();
        }
        return "Line " + problem.line() + ": (" + problem.file() + ")\n    " + problem.message()
                + (code.isEmpty() ? "" : "\n    Line: " + code);
    }
}
