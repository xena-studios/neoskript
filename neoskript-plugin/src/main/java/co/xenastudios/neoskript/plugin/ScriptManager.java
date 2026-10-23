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
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.FunctionHandle;
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
import java.util.function.Consumer;
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
    /** The commands of each loaded script. */
    private final Map<String, List<CompiledCommand>> loadedCommands = new LinkedHashMap<>();

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
     * Reloads every script, or only {@code only} (a script's path in the scripts folder). Problems are
     * sent to {@code problems} as well as the log.
     */
    synchronized Outcome reload(String only, Consumer<String> problems) throws IOException {
        return load(only, problem -> {
            logger.error(problem);
            problems.accept(problem);
        });
    }

    /** Global variables, for storage to load and save. */
    VariableStore globals() {
        return globals;
    }

    /** Whether {@code name} is an enabled script in the scripts folder. */
    boolean exists(String name) {
        Path path = scriptsDir.resolve(name).normalize();
        return path.startsWith(scriptsDir) && isEnabledScript(path);
    }

    private Outcome load(String only, Consumer<String> problems) throws IOException {
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
            if (only != null && !diagnostic.file().equals(only)) {
                continue;
            }
            if (diagnostic.severity() == Severity.ERROR) {
                failed.add(diagnostic.file());
                errors++;
            }
            problems.accept(describe(diagnostic, byName.get(diagnostic.file())));
        }

        // Scripts that load cleanly, or for the first time, switch to the new version; a script with
        // errors keeps its previous version if it has one.
        Set<String> switching = new HashSet<>();
        for (SourceFile file : files) {
            String name = file.name();
            if ((only == null || only.equals(name)) && (!failed.contains(name) || !loaded.containsKey(name))) {
                switching.add(name);
            }
        }
        if (only == null) {
            loaded.keySet().removeIf(name -> !byName.containsKey(name));
            loadedCommands.keySet().removeIf(name -> !byName.containsKey(name));
            for (String name : List.copyOf(periodic.keySet())) {
                if (!byName.containsKey(name)) {
                    periodic.remove(name).forEach(Runnable::run);
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
            loaded.put(name, triggers.getOrDefault(name, List.of()));
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
                    scheduler.runGlobal(() -> run(trigger));
                } else if (trigger.period() != null) {
                    stops.add(scheduler.runGlobalRepeating(trigger.period().ticks(), () -> run(trigger)));
                }
            }
            periodic.put(name, stops);
        }
        if (afterTests != null) {
            Runnable done = afterTests;
            afterTests = null;
            scheduler.runGlobal(() -> {
                try {
                    TestRunner.run(compiled, logger);
                } catch (IOException e) {
                    logger.error("Couldn't write the test results", e);
                }
                done.run();
            });
        }
        return new Outcome(only == null ? files.size() : 1, errors, millis);
    }

    private List<SourceFile> read() throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path path : paths.filter(ScriptManager::isEnabledScript).sorted().toList()) {
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
    private String name(Path path) {
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
            public boolean loaded(co.xenastudios.neoskript.runtime.ScriptRef script) {
                synchronized (ScriptManager.this) {
                    return loaded.containsKey(script.name());
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

    private static boolean isEnabledScript(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".sk") && !name.startsWith("-") && Files.isRegularFile(path);
    }

    private void run(CompiledScripts.CompiledTrigger trigger) {
        try {
            trigger.trigger().execute(new SimpleTriggerContext(null));
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
