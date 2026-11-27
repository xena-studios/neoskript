/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A section an addon registers ({@code Registrar.section}) is code in the trigger: unlike Skript's spawn and shoot
 * sections, which are loaded apart in events of their own, it can wait and sees the loops around it.
 */
class AddonSectionTest {

    private final List<String> said = new ArrayList<>();
    private final BlockingQueue<Runnable> later = new LinkedBlockingQueue<>();

    @BeforeEach
    void install() {
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
    }

    @AfterEach
    void reopen() {
        Waits.install(null, e -> {
            throw e;
        });
    }

    private CompiledScripts compile(String script) {
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        // As an addon registers one: its code runs once for each value it gives.
        library.section("TestSecAddon", "string", info -> (context, args) -> new Object[] {"v1", "v2"}, "addon values");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("t.sk", script)));
        assertEquals(List.of(), parsed.getFirst().diagnostics(), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics(), "compile problems");
        compiled.linkFunctions();
        return compiled;
    }

    private List<String> run(String script) {
        compile(script).triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        Runnable task;
        while ((task = later.poll()) != null) {
            task.run();
        }
        return said;
    }

    @Test
    void itsCodeCanWait() {
        assertEquals(List.of("a v1", "b v1", "a v2", "b v2", "after"), run("""
                on load:
                \taddon values:
                \t\tsay "a %event-string%"
                \t\twait 1 tick
                \t\tsay "b %event-string%"
                \tsay "after"
                """));
    }

    @Test
    void itSeesTheLoopsAroundIt() {
        assertEquals(List.of("1 v1", "2 v1"), run("""
                on load:
                \tloop 3 times:
                \t\taddon values:
                \t\t\tsay "%loop-value% %event-string%"
                \t\t\tif loop-value is 2:
                \t\t\t\texit loop
                \t\t\tcontinue
                """));
    }

    @Test
    void itSeesACommandsArguments() {
        compile("""
                command /addonprobe <text>:
                \ttrigger:
                \t\taddon values:
                \t\t\tsay "%arg-1% %event-string%"
                """);
    }
}
