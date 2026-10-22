/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * Loads the scripts in {@code plugins/NeoSkript/scripts}: parses and compiles them, reports problems the
 * way Skript does, and runs their triggers. Files whose names start with {@code -} are disabled, as in
 * Skript.
 */
final class ScriptManager {

    private final Path scriptsDir;
    private final Library library;
    private final Scheduler scheduler;
    private final Logger logger;
    /** Global variables. Kept in memory for now; saving them comes in M3. */
    private final VariableStore globals = new VariableStore();

    /** In test mode, what to do after the tests ran (stop the server); otherwise {@code null}. */
    private final Runnable afterTests;

    ScriptManager(Path scriptsDir, Library library, Scheduler scheduler, Logger logger, Runnable afterTests) {
        this.scriptsDir = scriptsDir;
        this.library = library;
        this.scheduler = scheduler;
        this.logger = logger;
        this.afterTests = afterTests;
    }

    void loadAll() throws IOException {
        long start = System.nanoTime();
        Files.createDirectories(scriptsDir);
        List<SourceFile> files = read();
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(files);
        CompiledScripts compiled = new ScriptCompiler(library, globals).compile(parsed);
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        Map<String, SourceFile> byName = new HashMap<>();
        files.forEach(file -> byName.put(file.name(), file));
        List<Diagnostic> problems = new ArrayList<>();
        parsed.forEach(script -> problems.addAll(script.diagnostics()));
        problems.addAll(compiled.diagnostics());
        for (Diagnostic problem : problems) {
            report(problem, byName.get(problem.file()));
        }
        long errors = problems.stream().filter(d -> d.severity() == Severity.ERROR).count();
        logger.info("Loaded {} {} with {} {} in {} ms{}", files.size(), files.size() == 1 ? "script" : "scripts",
                compiled.triggers().size(), compiled.triggers().size() == 1 ? "trigger" : "triggers", elapsed,
                errors == 0 ? "" : " (" + errors + (errors == 1 ? " error" : " errors") + ")");

        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            if (trigger.event() == ScriptEvent.LOAD) {
                scheduler.runGlobal(() -> run(trigger));
            }
        }
        if (afterTests != null) {
            scheduler.runGlobal(() -> {
                try {
                    TestRunner.run(compiled, logger);
                } catch (IOException e) {
                    logger.error("Couldn't write the test results", e);
                }
                afterTests.run();
            });
        }
    }

    private List<SourceFile> read() throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path path : paths.filter(ScriptManager::isEnabledScript).sorted().toList()) {
                String name = scriptsDir.relativize(path).toString().replace('\\', '/');
                files.add(new SourceFile(name, Files.readString(path)));
            }
        }
        return files;
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

    /** Reports a problem like Skript does: the line and file, the message, then the line itself. */
    private void report(Diagnostic problem, SourceFile file) {
        String code = "";
        if (file != null && problem.line() >= 1 && problem.line() <= file.lines().size()) {
            code = file.lines().get(problem.line() - 1).strip();
        }
        String text = "Line " + problem.line() + ": (" + problem.file() + ")\n    " + problem.message()
                + (code.isEmpty() ? "" : "\n    Line: " + code);
        if (problem.severity() == Severity.ERROR) {
            logger.error(text);
        } else {
            logger.warn(text);
        }
    }
}
