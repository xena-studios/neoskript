/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.PausingEffect;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.Pauses;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An addon's pausing effects and expressions, run through the real standard library and compiler. */
class PausingTest {

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    /** Tasks the scheduler was given, run by the test as the server would. */
    private final BlockingQueue<Runnable> later = new LinkedBlockingQueue<>();
    /** The owners carried-on triggers were scheduled for. */
    private final List<Object> owners = Collections.synchronizedList(new ArrayList<>());
    /** The stages the 'fetch' syntax handed out, in order, for the test to complete. */
    private final List<CompletableFuture<Object>> stages = Collections.synchronizedList(new ArrayList<>());
    private final VariableStore globals = new VariableStore();
    private Library library;

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
                owners.add(owner);
                later.add(task);
            }
        }, e -> {
            throw e;
        });
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(RuntimeError error) {
                errors.add(error.source().lineNumber() + ": " + error.error());
            }

            @Override
            public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {}

            @Override
            public void logError(String message) {
                errors.add(message);
            }
        }, ErrorFrame.Limits.DEFAULT, ErrorFrame.Limits.DEFAULT);
        library = new Library();
        StandardLibrary.register(library);
        library.enableTesting();
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        // 'fetch "x"': a stage the test completes. 'quick "x"': one that's complete already.
        library.pausingEffect("TestEffFetch", info -> (context, args) -> stage(), "fetch %text% [into %-objects%]");
        library.pausingEffect("TestEffStore", info -> PausingEffect.storing(1, (context, args) -> stage()),
                "load %text% into %-objects%");
        library.pausingEffect("TestEffQuick", info -> (context, args) -> CompletableFuture.completedFuture(args[0]),
                "quick %text%");
        library.pausingExpression("TestExprFetched", "object", info -> (context, args) -> stage(), "fetched %text%");
        library.pausingExpression("TestExprReady", "object",
                info -> (context, args) -> CompletableFuture.completedFuture("ready " + args[0]), "ready %text%");
        library.pausingEffect("TestEffSlow", info -> (context, args) -> new CompletableFuture<>(), "hang");
        library.timeout("TestEffSlow", Duration.ofMillis(50));
    }

    @AfterEach
    void reopen() {
        Waits.install(null, e -> {
            throw e;
        });
    }

    private CompletableFuture<Object> stage() {
        CompletableFuture<Object> stage = new CompletableFuture<>();
        stages.add(stage);
        return stage;
    }

    private CompiledScripts compile(String script) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        assertEquals(List.of(), errors(parsed.getFirst().diagnostics()), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, globals).compile(parsed);
        compiled.linkFunctions();
        return compiled;
    }

    private void run(String script) {
        CompiledScripts compiled = compile(script);
        assertEquals(List.of(), errors(compiled.diagnostics()), "compile problems");
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }

    /** Runs what the scheduler was given until there's nothing left. */
    private void tick() {
        Runnable task;
        while ((task = later.poll()) != null) {
            task.run();
        }
    }

    @Test
    void anEffectPausesUntilItsStageCompletesAndKeepsTheLocalsAndLoops() {
        run("""
                on load:
                \tset {_x} to "kept"
                \tsay "before"
                \tfetch "a"
                \tsay "after %{_x}%"
                \tset {_l::*} to "p" and "q"
                \tloop {_l::*}:
                \t\tfetch loop-value
                \t\tsay "%loop-index% %loop-value%"
                \tsay "done"
                """);
        assertEquals(List.of("before"), said);
        tick();
        assertEquals(List.of("before"), said, "nothing carries on before the stage completes");
        stages.get(0).complete(null);
        tick();
        assertEquals(List.of("before", "after kept"), said);
        // Completed from another thread, as an addon's executor would.
        CompletableFuture.runAsync(() -> stages.get(1).complete("ignored")).join();
        tick();
        stages.get(2).complete(null);
        tick();
        assertEquals(List.of("before", "after kept", "1 p", "2 q", "done"), said);
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aStageThatHasCompletedCarriesStraightOn() {
        run("""
                on load:
                \tsay has delay before
                \tquick "x"
                \tsay has delay before
                \tset {_v} to ready "now"
                \tsay {_v}
                """);
        assertEquals(List.of("false", "true", "ready now"), said);
        assertTrue(later.isEmpty());
    }

    @Test
    void anExpressionsValueArrivesBeforeItsLineRuns() {
        run("""
                on load:
                \tsay "start"
                \tsay "got %fetched "a"% and %fetched "b"%"
                \tset {_n} to fetched "c"
                \tsay "n is %{_n}%"
                """);
        assertEquals(List.of("start"), said);
        stages.get(0).complete("A");
        tick();
        // The second is started once the first has arrived, and the first is kept across its wait.
        assertEquals(2, stages.size());
        stages.get(1).complete(List.of("B1", "B2"));
        tick();
        assertEquals(List.of("start", "got A and B1 and B2"), said);
        stages.get(2).complete(5L);
        tick();
        assertEquals(List.of("start", "got A and B1 and B2", "n is 5"), said);
    }

    @Test
    void aTargetGetsTheStagesValueWithAMapsKeysAsIndices() {
        run("""
                on load:
                \tload "rows" into {_rows::*}
                \tsay "%{_rows::name::1}% %{_rows::name::2}% %{_rows::count}%"
                \tfetch "one" into {_one}
                \tsay "one is %{_one}%"
                \tset {_kept} to "old"
                \tload "fails" into {_kept}
                \tsay "kept %{_kept}%"
                """);
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("name", List.of("Steve", "Alex"));
        rows.put("count", 2L);
        stages.get(0).complete(rows);
        tick();
        // 'fetch' has no target: its pattern's placeholder is only given to it.
        stages.get(1).complete("ignored");
        tick();
        assertEquals(List.of("Steve Alex 2", "one is <none>"), said);
        stages.get(2).completeExceptionally(new IllegalStateException("No such table"));
        tick();
        assertEquals(List.of("Steve Alex 2", "one is <none>", "kept old"), said);
        assertEquals(List.of("7: No such table"), errors);
    }

    @Test
    void aFailedStageIsARuntimeErrorAndTheTriggerCarriesOn() {
        run("""
                on load:
                \tset {_v} to fetched "x"
                \tsay "v is %{_v}%"
                """);
        stages.get(0).completeExceptionally(new java.io.IOException("Connection refused"));
        tick();
        assertEquals(List.of("v is <none>"), said);
        assertEquals(List.of("2: Connection refused"), errors);
    }

    @Test
    void aStageThatTakesTooLongTimesOut() throws InterruptedException {
        run("""
                on load:
                \thang
                \tsay "carried on"
                """);
        Runnable task = later.poll(5, TimeUnit.SECONDS);
        assertNotNull(task, "the timeout should have carried the trigger on");
        task.run();
        assertEquals(List.of("carried on"), said);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().startsWith("2: Gave up waiting for it after"), errors.toString());
        assertEquals(0, Pauses.pending());
    }

    @Test
    void itCountsAsADelayForSkriptsRules() {
        run("""
                on load:
                \tsay has delay before
                \tset {_v} to fetched "x"
                \tsay has delay before
                """);
        stages.getFirst().complete(1L);
        tick();
        assertEquals(List.of("false", "true"), said);
    }

    @Test
    void anExpressionInASectionsHeaderIsAnError() {
        CompiledScripts compiled = compile("""
                on load:
                \tif fetched "x" is 1:
                \t\tsay "no"
                """);
        assertEquals(1, errors(compiled.diagnostics()).size(), compiled.diagnostics().toString());
        assertTrue(errors(compiled.diagnostics()).getFirst().contains("waits for its value"),
                compiled.diagnostics().toString());
    }

    @Test
    void stoppingTheServerDropsWaitingTriggers() {
        run("""
                on load:
                \tfetch "a"
                \tsay "never"
                """);
        assertEquals(1, Pauses.pending());
        Pauses.close();
        assertEquals(0, Pauses.pending());
        assertTrue(stages.getFirst().isCancelled(), "its stage should be cancelled");
        tick();
        assertEquals(List.of(), said);
        // A trigger that pauses while the server stops doesn't carry on either.
        run("""
                on load:
                \tfetch "b"
                \tsay "never either"
                """);
        stages.get(1).complete(null);
        tick();
        assertEquals(List.of(), said);
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aStageKeepsNothingOfTheTriggerOnceItTimedOut() throws InterruptedException {
        run("""
                on load:
                \thang
                """);
        assertNotNull(later.poll(5, TimeUnit.SECONDS));
        // The stage was cancelled; nothing waits on it.
        assertEquals(0, Pauses.pending());
        assertNull(later.poll());
    }

    @Test
    void theTriggerCarriesOnOnTheThreadOfWhatStartedIt() {
        CompiledScripts compiled = compile("""
                on load:
                \tfetch "a"
                \tsay "after"
                """);
        Object pig = new Object();
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null, pig));
        stages.getFirst().complete(null);
        tick();
        assertEquals(List.of("after"), said);
        // On Folia, the scheduler runs it on the thread that owns what the trigger's event happened to.
        assertEquals(List.of(pig), owners);
    }

    @Test
    void timeoutsAreChecked() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.timeout("TestEffSlow", Duration.ZERO));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.timeout("TestSay", Duration.ofSeconds(1)));
    }
}
