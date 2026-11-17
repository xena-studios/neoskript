/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import co.xenastudios.neoskript.addons.web.http.HttpEngine;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Runs scripts with NeoSkriptWeb's syntax through NeoSkript's real compiler and standard library, without a server:
 * {@code say %objects%} records what it's given, and triggers paused by a request carry on when {@link #settle} runs
 * what the scheduler was given.
 */
final class Scripts implements AutoCloseable {

    final List<String> said = Collections.synchronizedList(new ArrayList<>());
    final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private final BlockingQueue<Runnable> later = new LinkedBlockingQueue<>();
    final HttpEngine engine;
    final Library library;

    Scripts(WebConfig config) {
        Waits.install(new Scheduler() {
            @Override
            public void runGlobal(Runnable task) {
                later.add(task);
            }

            @Override
            public void runGlobalLater(long ticks, Runnable task) {
                later.add(task);
            }

            @Override
            public Runnable runGlobalRepeating(long ticks, Runnable task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void runLater(Object owner, long ticks, Runnable task) {
                later.add(task);
            }
        }, e -> {
            throw e;
        });
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(RuntimeError error) {
                errors.add(error.error());
            }

            @Override
            public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {}

            @Override
            public void logError(String message) {
                errors.add(message);
            }
        }, ErrorFrame.Limits.DEFAULT, ErrorFrame.Limits.DEFAULT);
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        engine = new HttpEngine(config);
        library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        WebSyntax.register(library, new WebAddon.Web(engine, config, new Console(logger), null));
    }

    /** Parse and compile problems, none expected. */
    List<String> problems(String script) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        List<String> problems = new ArrayList<>(errors(parsed.getFirst().diagnostics()));
        if (problems.isEmpty()) {
            CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
            problems.addAll(errors(compiled.diagnostics()));
        }
        return problems;
    }

    /** Runs a script's triggers, then what they paused for, until nothing's left or {@code seconds} pass. */
    void run(String script, int seconds) throws InterruptedException {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        List<String> problems = errors(parsed.getFirst().diagnostics());
        if (!problems.isEmpty()) {
            throw new AssertionError("parse problems: " + problems);
        }
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        compiled.linkFunctions();
        if (!errors(compiled.diagnostics()).isEmpty()) {
            throw new AssertionError("compile problems: " + errors(compiled.diagnostics()));
        }
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        settle(seconds);
    }

    /** Runs what the scheduler is given until it's been idle a moment, or the time is up. */
    void settle(int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            Runnable task = later.poll(300, TimeUnit.MILLISECONDS);
            if (task == null) {
                if (co.xenastudios.neoskript.runtime.Pauses.pending() == 0) {
                    return;
                }
                continue;
            }
            task.run();
        }
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }

    @Override
    public void close() {
        engine.close();
        Waits.install(null, e -> {
            throw e;
        });
    }
}
