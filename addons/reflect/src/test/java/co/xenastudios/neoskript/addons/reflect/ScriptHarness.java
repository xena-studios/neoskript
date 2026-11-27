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
        // A class whose methods can't be looked at: Broken, loaded where Missing, which one of them names, isn't.
        library.expression("TestBrokenClass", "javatype", info -> (context, args) -> new JavaType(brokenClass()),
                "the broken class");
        // Like 'send %objects% [to %audiences%]', which needs a server.
        library.effect("TestSayTo", info -> (context, args) -> said.add(
                co.xenastudios.neoskript.runtime.Values.text(args[0]) + " to " + co.xenastudios.neoskript.runtime.Values.text(args[1])),
                "say %objects% to %strings%");
        Syntax.elements(library, imports);
    }

    private final java.util.Map<co.xenastudios.neoskript.frontend.registry.FunctionInfo,
            co.xenastudios.neoskript.runtime.FunctionHandle> handles = new java.util.concurrent.ConcurrentHashMap<>();
    private final ScriptCompiler compiler = new ScriptCompiler(library, globals, handles);

    /**
     * The scripts the next load switches to their new code, as a reload of one script does; {@code null} for all.
     * Every script is still parsed and compiled, as the plugin does.
     */
    java.util.Set<String> switching;

    private co.xenastudios.neoskript.frontend.registry.SyntaxRegistry declared;

    /** Runs {@code line} as an effect command does, with the syntax the loaded scripts declared; its errors. */
    List<String> effectCommand(String line) {
        ScriptCompiler.CompiledLine compiled = compiler.compileLine("effect command", line, Object.class,
                "effect command", declared);
        if (compiled.trigger() != null) {
            compiled.trigger().execute(new SimpleTriggerContext(null, null));
        }
        return compiled.errors();
    }

    /** What the last load said about syntax that went while loaded scripts used it. */
    List<String> switchWarnings = List.of();

    /** Parses and compiles {@code scripts} (name, text, name, text...), and returns the problems. */
    List<String> load(String... scripts) {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < scripts.length; i += 2) {
            files.add(new SourceFile(scripts[i], scripts[i + 1]));
        }
        ScriptLoader loader = new ScriptLoader(library.registry());
        List<ParsedScript> parsed = loader.load(files);
        declared = loader.declaredRegistry();
        List<String> problems = new ArrayList<>();
        for (ParsedScript script : parsed) {
            for (Diagnostic d : script.diagnostics()) {
                problems.add(d.severity() + " " + d.line() + ": " + d.message());
            }
        }
        // One compiler, and one set of function handles, across loads, as the plugin keeps them.
        compiled = compiler.compile(parsed);
        java.util.Set<String> names = new java.util.HashSet<>();
        files.forEach(file -> names.add(file.name()));
        java.util.Set<String> switching = this.switching == null ? names : this.switching;
        compiled.linkFunctions(switching);
        switchWarnings = compiled.switchSyntax(switching, handles, names);
        for (Diagnostic d : compiled.diagnostics()) {
            problems.add(d.severity() + " " + d.line() + ": " + d.message());
        }
        return problems;
    }

    private CompiledScripts compiled;

    /** The triggers loaded now, to run after a later load has replaced them. */
    List<CompiledScripts.CompiledTrigger> triggers() {
        return List.copyOf(compiled.triggers());
    }

    /** Runs {@code triggers}, as {@link #run()} does. */
    void run(List<CompiledScripts.CompiledTrigger> triggers) {
        for (CompiledScripts.CompiledTrigger trigger : triggers) {
            trigger.trigger().execute(new SimpleTriggerContext(null, null));
        }
    }

    private static Class<?> broken;

    static synchronized Class<?> brokenClass() {
        if (broken != null) {
            return broken;
        }
        String name = "co.xenastudios.neoskript.addons.reflect.fixture.Broken";
        ClassLoader parent = ScriptHarness.class.getClassLoader();
        ClassLoader hiding = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(className)) {
                    Class<?> loaded = findLoadedClass(className);
                    if (loaded != null) {
                        return loaded;
                    }
                    if (className.endsWith("fixture.Missing")) {
                        throw new ClassNotFoundException(className);
                    }
                    if (!className.equals(name)) {
                        return parent.loadClass(className);
                    }
                    try (java.io.InputStream in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = in.readAllBytes();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            }
        };
        try {
            broken = hiding.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        return broken;
    }

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
        public boolean inDeclaredSyntaxCode() {
            return ScriptCompiler.compilingDeclaredSyntax();
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
