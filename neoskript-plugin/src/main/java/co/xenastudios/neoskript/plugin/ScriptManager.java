/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
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
 * <p>A reload compiles everything before anything changes. A script that has errors keeps running its
 * previous version (D12); the others switch to their new version together.
 */
final class ScriptManager {

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

    /** Loads every script when the server starts. */
    synchronized void loadAll() throws IOException {
        Outcome outcome = load(null, logger::error);
        int triggers = loaded.values().stream().mapToInt(List::size).sum();
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
        return new ScriptCompiler(library, globals, functions).compileLine(EFFECT_COMMAND, effect, eventClass,
                EFFECT_COMMAND);
    }

    /** What effect commands' code is called, as the script it runs in and its event. */
    private static final String EFFECT_COMMAND = "effect command";

    /** Loads the scripts {@code scope} accepts, or every script if it's {@code null}. */
    private Outcome load(Predicate<String> scope, Consumer<String> problems) throws IOException {
        return load(scope, problems, false);
    }

    /** With {@code now}, load events run straight away rather than on the next tick: for loads a script asks for. */
    private Outcome load(Predicate<String> scope, Consumer<String> problems, boolean now) throws IOException {
        return load(scope, problems, now, null);
    }

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
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(files);
        CompiledScripts compiled = new ScriptCompiler(library, globals, functions).compile(parsed);

        Map<String, SourceFile> byName = new HashMap<>();
        files.forEach(file -> byName.put(file.name(), file));
        List<Diagnostic> diagnostics = new ArrayList<>();
        parsed.forEach(script -> diagnostics.addAll(script.diagnostics()));
        diagnostics.addAll(compiled.diagnostics());
        Set<String> failed = new HashSet<>();
        int errors = 0;
        for (Diagnostic diagnostic : diagnostics) {
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

        // Scripts that load cleanly, or for the first time, switch to the new version; a script with
        // errors keeps its previous version if it has one.
        Set<String> switching = new HashSet<>();
        int inScope = 0;
        for (SourceFile file : files) {
            String name = file.name();
            if (scope == null || scope.test(name)) {
                inScope++;
                if (!failed.contains(name) || !loaded.containsKey(name)) {
                    switching.add(name);
                }
            }
        }
        if (scope == null) {
            // Scripts that are gone run their unload events first.
            loaded.forEach((name, old) -> {
                if (!byName.containsKey(name)) {
                    runUnload(old);
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
            // The previous version of a script unloads before the new one loads.
            runUnload(loaded.getOrDefault(name, List.of()));
            loaded.put(name, triggers.getOrDefault(name, List.of()));
            sources.put(name, byName.get(name).text());
            loadedCommands.put(name, scriptCommands.getOrDefault(name, List.of()));
        }
        events.update(loaded.values().stream().flatMap(List::stream).toList());
        commands.update(loadedCommands.values().stream().flatMap(List::stream).toList());
        long millis = (System.nanoTime() - start) / 1_000_000;

        for (String name : switching) {
            periodic.getOrDefault(name, List.of()).forEach(Runnable::run);
            List<Runnable> stops = new ArrayList<>();
            for (CompiledScripts.CompiledTrigger trigger : loaded.get(name)) {
                if (trigger.event() == ScriptEvent.LOAD) {
                    if (now) {
                        run(trigger);
                    } else {
                        scheduler.runGlobal(() -> run(trigger));
                    }
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
            // As in Skript's test mode, tests start once the world has had time to load and tick its chunks.
            scheduler.runGlobalLater(100, () -> {
                try {
                    TestRunner.run(compiled, logger);
                } catch (IOException e) {
                    logger.error("Couldn't write the test results", e);
                }
                done.run();
            });
        }
        return new Outcome(inScope, errors, millis);
    }

    /**
     * Starts watching the scripts in {@code switching} that have Skript's {@code auto reload}, and stops
     * watching their previous versions. A script with errors that keeps its previous version keeps
     * watching as it did.
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
        AutoReload.send(audience, autoReload.reloading());
        try {
            Outcome outcome = load(name::equals, problem -> {}, false,
                    (problem, file) -> AutoReload.send(audience, AutoReload.describe(problem, file)));
            AutoReload.send(audience, autoReload.reloaded(outcome.errors(), (System.nanoTime() - start) / 1_000_000));
        } catch (IOException | RuntimeException e) {
            logger.error("Exception occurred while automatically reloading a script ({})", name, e);
        }
    }

    private List<SourceFile> read() throws IOException {
        List<SourceFile> files = new ArrayList<>();
        modified.clear();
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path path : paths.filter(this::isLoadable).sorted().toList()) {
                modified.put(name(path), Files.getLastModifiedTime(path).toMillis());
                files.add(new SourceFile(name(path), Files.readString(path)));
            }
        }
        return files;
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
            load(name::equals, logger::error, true);
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
        events.update(loaded.values().stream().flatMap(List::stream).toList());
        commands.update(loadedCommands.values().stream().flatMap(List::stream).toList());
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

    /** Runs every loaded script's unload events, as the server stops. */
    synchronized void unloadAll() {
        loaded.values().forEach(this::runUnload);
    }

    private void run(CompiledScripts.CompiledTrigger trigger) {
        run(trigger, null);
    }

    /** Runs {@code trigger} in {@code event}, which may be {@code null}. */
    private void run(CompiledScripts.CompiledTrigger trigger, Object event) {
        try {
            trigger.trigger().execute(new SimpleTriggerContext(event));
        } catch (ScriptRuntimeException e) {
            logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
        }
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
