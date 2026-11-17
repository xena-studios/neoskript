/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.Installed;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.Scripts;
import co.xenastudios.neoskript.api.Threads;
import co.xenastudios.neoskript.api.Values;
import co.xenastudios.neoskript.api.Variables;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.LoadingScript;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Scripts compiled with NeoSkript's standard library and this addon, as on a server, and run. */
final class ScriptHarness {

    final List<String> said = Collections.synchronizedList(new ArrayList<>());
    final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    final Library library = new Library();
    final Imports imports = new Imports(ScriptHarness.class.getClassLoader());
    private final VariableStore globals = new VariableStore();

    ScriptHarness() {
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(RuntimeError error) {
                warnings.add(error.error());
            }

            @Override
            public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {}

            @Override
            public void logError(String message) {
                warnings.add(message);
            }
        }, ErrorFrame.Limits.DEFAULT, ErrorFrame.Limits.DEFAULT);
        Installed.install(new TestApi());
        // The types first, so imported names are read before the standard library's literals, which need a server's
        // registries to say no.
        Syntax.types(library, imports);
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(co.xenastudios.neoskript.runtime.Values.text(args[0])),
                "say %objects%");
        // Like 'send %objects% [to %audiences%]', which needs a server.
        library.effect("TestSayTo", info -> (context, args) -> said.add(
                co.xenastudios.neoskript.runtime.Values.text(args[0]) + " to " + co.xenastudios.neoskript.runtime.Values.text(args[1])),
                "say %objects% to %strings%");
        Syntax.elements(library, imports);
    }

    /** Parses and compiles {@code scripts} (name, text, name, text...), and returns the problems. */
    List<String> load(String... scripts) {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < scripts.length; i += 2) {
            files.add(new SourceFile(scripts[i], scripts[i + 1]));
        }
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(files);
        List<String> problems = new ArrayList<>();
        for (ParsedScript script : parsed) {
            for (Diagnostic d : script.diagnostics()) {
                problems.add(d.severity() + " " + d.line() + ": " + d.message());
            }
        }
        compiled = new ScriptCompiler(library, globals).compile(parsed);
        compiled.linkFunctions();
        for (Diagnostic d : compiled.diagnostics()) {
            problems.add(d.severity() + " " + d.line() + ": " + d.message());
        }
        return problems;
    }

    private CompiledScripts compiled;

    /** Runs every load trigger. */
    void run() {
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            trigger.trigger().execute(new SimpleTriggerContext(null, null));
        }
    }

    /** Loads one script, fails on any problem, runs it, and gives what it said. */
    List<String> run(String script) {
        // Skript's own warning for 'Math.max(3, 7)' isn't this addon's to test.
        List<String> problems = load("test.sk", script).stream().filter(p -> !p.contains("missing 'and' or 'or'"))
                .toList();
        if (!problems.isEmpty()) {
            throw new AssertionError("Problems: " + problems);
        }
        run();
        return List.copyOf(said);
    }

    /** NeoSkript as the addon sees it, enough for these tests. */
    private final class TestApi implements NeoSkript {
        @Override
        public String version() {
            return "test";
        }

        @Override
        public void addon(String name, String version, Consumer<? super Registrar> registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> addons() {
            return List.of();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void whenReady(Runnable task) {
            task.run();
        }

        @Override
        public Scripts scripts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Variables variables() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Values values() {
            return new Values() {
                @Override
                public String type(Object value) {
                    return null;
                }

                @Override
                public String text(Object value) {
                    return co.xenastudios.neoskript.runtime.Values.text(value);
                }

                @Override
                public Object parse(String text, String typeId) {
                    return null;
                }

                @Override
                public Object convert(Object value, Class<?> to) {
                    Object converted = co.xenastudios.neoskript.runtime.Values.convert(value, to);
                    return to.isInstance(converted) ? converted : null;
                }

                @Override
                public Object toJava(Object value) {
                    // As NeoSkript's own.
                    return value instanceof co.xenastudios.neoskript.runtime.Timespan span
                            ? java.time.Duration.ofMillis(span.millis()) : value;
                }

                @Override
                public Object fromJava(Object value) {
                    return value;
                }

                @Override
                public Saved save(Object value) {
                    return null;
                }

                @Override
                public Object load(String type, byte[] data) {
                    return null;
                }
            };
        }

        @Override
        public Threads threads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String scriptBeingLoaded() {
            return LoadingScript.current();
        }

        @Override
        public Runnable whenStopped(Runnable task) {
            // The tests never stop NeoSkript: nothing to run, nothing to cancel.
            return () -> {};
        }

        @Override
        public void runtimeWarning(String message) {
            RuntimeErrors.warning(message);
        }

        @Override
        public void runtimeError(String message) {
            RuntimeErrors.report(message);
        }
    }
}
