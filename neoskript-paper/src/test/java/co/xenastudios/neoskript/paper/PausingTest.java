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
        // 'explode': its start throws. 'odd': a stage that can't be waited for.
        library.pausingEffect("TestEffExplode", info -> (context, args) -> {
            throw new IllegalStateException("Database is down");
        }, "explode");
        library.pausingEffect("TestEffOdd", info -> (context, args) -> new BrokenStage(), "odd");
        library.timeout("TestEffOdd", Duration.ofMillis(50));
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
        assertTrue(!stages.getFirst().isCancelled(), "its stage is the addon's: it isn't cancelled");
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
    void aLineWithAPausingExpressionIsAfterADelayFromItsStart() {
        run("""
                on load:
                \tsay "%fetched "x"% %has delay before%"
                """);
        stages.getFirst().complete("got");
        tick();
        assertEquals(List.of("got true"), said);
    }

    @Test
    void aReturnOnALineThatPausesIsRefusedAsAfterAWait() {
        CompiledScripts compiled = compile("""
                function f() :: text:
                \treturn fetched "x"
                """);
        assertEquals(List.of("A return statement after a delay is useless, as the calling trigger will resume when"
                + " the delay starts (and won't get any returned value)"), errors(compiled.diagnostics()));
    }

    @Test
    void anExpressionInAnElseIfHeaderGivesOneError() {
        CompiledScripts compiled = compile("""
                on load:
                \tif 1 is 2:
                \t\tsay "no"
                \telse if fetched "x" is 1:
                \t\tsay "no"
                """);
        assertEquals(1, errors(compiled.diagnostics()).size(), compiled.diagnostics().toString());
        assertTrue(errors(compiled.diagnostics()).getFirst().contains("waits for its value"),
                compiled.diagnostics().toString());
    }

    @Test
    void aStartThatThrowsIsAFailedStage() {
        run("""
                on load:
                \texplode
                \tsay "carried on"
                """);
        assertEquals(List.of("carried on"), said);
        assertEquals(List.of("2: Database is down"), errors);
    }

    @Test
    void aStageThatCantBeWaitedForFailsAndNeverResumesLater() throws InterruptedException {
        run("""
                on load:
                \todd
                \tsay "carried on"
                """);
        assertEquals(List.of("carried on"), said);
        assertEquals(List.of("2: can't wait"), errors);
        // Its timeout was cancelled with it: nothing resumes the trigger a second time.
        assertNull(later.poll(300, TimeUnit.MILLISECONDS));
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aFinishedPauseLetsGoOfItsEvent() {
        CompiledScripts compiled = compile("""
                on load:
                \tfetch "a"
                \tsay "after"
                """);
        Object event = new Object();
        java.lang.ref.WeakReference<Object> held = new java.lang.ref.WeakReference<>(event);
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(event));
        event = null;
        // The addon keeps its stage for good, as a long-lived upstream future would.
        CompletableFuture<Object> kept = stages.getFirst();
        kept.complete(null);
        tick();
        assertEquals(List.of("after"), said);
        for (int i = 0; i < 20 && held.get() != null; i++) {
            System.gc();
        }
        assertNull(held.get(), "the stage kept the event alive");
        assertTrue(kept.isDone());
    }

    @Test
    void stoppingWaitsALittleForWhatUnloadTriggersPausedFor() {
        run("""
                on load:
                \tfetch "before stopping"
                """);
        Pauses.stopping();
        run("""
                on load:
                \tfetch "in an unload trigger"
                \tsay "never"
                """);
        CompletableFuture<Object> early = stages.get(0);
        CompletableFuture<Object> late = stages.get(1);
        // The unload trigger's work finishes while the stop waits for it.
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> late.complete("written"));
        long start = System.nanoTime();
        Pauses.close(Duration.ofSeconds(5));
        long took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(late.isDone() && took < 4000, "waited " + took + " ms");
        // Neither is cancelled: the work is the addon's to finish.
        assertTrue(!early.isCancelled() && !late.isCancelled());
        tick();
        assertEquals(List.of(), said, "a trigger doesn't carry on while the server stops");
        assertEquals(0, Pauses.pending());
        // And a stage that never ends makes the stop wait no longer than the grace.
        Waits.install(null, e -> {
            throw e;
        });
    }

    @Test
    void aPauseAfterStoppingDoesntUncountAnotherWait() {
        Waits.track();
        Object event = new Object();
        CompiledScripts waiting = compile("""
                on load:
                \tfetch "a"
                """);
        waiting.triggers().getFirst().trigger().execute(new SimpleTriggerContext(event));
        assertTrue(Waits.pending(event));
        Pauses.close();
        // close() dropped that one; a new pause on the same event now stops without counting anything.
        assertTrue(!Waits.pending(event));
        CompiledScripts other = compile("""
                on load:
                \twait 1 second
                \tfetch "b"
                """);
        other.triggers().getFirst().trigger().execute(new SimpleTriggerContext(event));
        assertTrue(Waits.pending(event));
        Runnable afterWait = later.poll();
        assertNotNull(afterWait);
        // The wait carries on, uncounting itself, and its pause, refused while stopped, counts nothing.
        afterWait.run();
        assertTrue(!Waits.pending(event));
    }

    /** A stage that can't be waited for: whenComplete throws. */
    private static final class BrokenStage extends CompletableFuture<Object> {

        @Override
        public CompletableFuture<Object> toCompletableFuture() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Object> whenComplete(
                java.util.function.BiConsumer<? super Object, ? super Throwable> action) {
            throw new IllegalStateException("can't wait");
        }
    }

    @Test
    void timeoutsAreChecked() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.timeout("TestEffSlow", Duration.ZERO));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.timeout("TestSay", Duration.ofSeconds(1)));
    }

    /** What 'query' was given, and how its first argument was written while the script loaded. */
    private final List<Object> queried = Collections.synchronizedList(new ArrayList<>());
    private final List<Object> shapes = Collections.synchronizedList(new ArrayList<>());

    /** 'query %text%' wants its text's expressions apart; 'raw %text%' is an expression the template names. */
    private void templates() {
        final class Query implements co.xenastudios.neoskript.api.element.PausingEffect,
                co.xenastudios.neoskript.api.element.TemplateAware {
            @Override
            public java.util.concurrent.CompletionStage<?> start(co.xenastudios.neoskript.api.element.TriggerContext context,
                    Object[] arguments) {
                queried.add(arguments[0]);
                return null;
            }

            @Override
            public boolean template(int index) {
                return index == 0;
            }
        }
        library.pausingEffect("TestEffQuery", info -> {
            shapes.add(info.argumentText(0) != null ? info.argumentText(0) : String.valueOf(info.argumentElement(0)));
            return new Query();
        }, "query %text%");
        library.expression("TestExprRaw", "string", info -> (context, args) -> args[0], "shout %text%");
    }

    @Test
    void aTemplateAwareElementGetsATextsExpressionsApart() {
        templates();
        run("""
                on load:
                	set {_name} to "Steve"
                	set {_l::*} to 1 and 2.5
                	query "SELECT * FROM t WHERE name = %{_name}% AND n IN (%{_l::*}%) -- %shout "x"% 100%%"
                	query "plain"
                	query {_name}
                	query shout "y"
                """);
        co.xenastudios.neoskript.api.element.TextTemplate first =
                (co.xenastudios.neoskript.api.element.TextTemplate) queried.get(0);
        assertEquals(List.of("SELECT * FROM t WHERE name = ", " AND n IN (", ") -- ", " 100%"), first.pieces());
        assertEquals(List.of("Steve"), first.holes().get(0).values());
        assertEquals(null, first.holes().get(0).element());
        assertEquals("{_name}", first.holes().get(0).written());
        assertEquals(List.of(1L, 2.5), first.holes().get(1).values());
        assertEquals(List.of("1", "2.5"), first.holes().get(1).texts());
        assertEquals("TestExprRaw", first.holes().get(2).element());
        assertEquals(List.of("x"), first.holes().get(2).values());
        assertEquals(new co.xenastudios.neoskript.api.element.TextTemplate(List.of("plain"), List.of()), queried.get(1));
        assertEquals(List.of("Steve", "y"), queried.subList(2, 4));
        // While loading, the factory sees the text's shape, without values, or what the argument is.
        co.xenastudios.neoskript.api.element.TextTemplate shape =
                (co.xenastudios.neoskript.api.element.TextTemplate) shapes.getFirst();
        assertEquals(first.pieces(), shape.pieces());
        assertEquals(List.of(), shape.holes().getFirst().values());
        assertTrue(shapes.contains("TestExprRaw"), shapes::toString);
        assertTrue(shapes.contains("null"), shapes::toString);
    }

    @Test
    void aStageCanLeaveItsTargetAsItIsWithoutAnError() {
        run("""
                on load:
                	set {_kept} to "old"
                	load "x" into {_kept}
                	say "kept %{_kept}%"
                	load "y" into {_when}
                	say "%{_when}% is a date: %whether {_when} is a date%"
                """);
        stages.get(0).complete(PausingEffect.UNCHANGED);
        tick();
        stages.get(1).complete(java.time.Instant.ofEpochMilli(0));
        tick();
        assertEquals("kept old", said.getFirst());
        assertTrue(said.get(1).endsWith("is a date: true"), said.get(1));
        assertEquals(List.of(), errors);
    }

    /** 'tx %text%:' pauses before and after its code, with the test completing each stage. */
    private final List<String> ends = Collections.synchronizedList(new ArrayList<>());
    /** The runs 'tx' started for, and was told were abandoned. */
    private final List<Object> startedRuns = Collections.synchronizedList(new ArrayList<>());
    private final List<Object> abandonedRuns = Collections.synchronizedList(new ArrayList<>());

    private void sections() {
        library.pausingSection("TestSecTx", info -> new co.xenastudios.neoskript.api.element.PausingSection() {
            @Override
            public java.util.concurrent.CompletionStage<?> start(co.xenastudios.neoskript.api.element.TriggerContext context,
                    Object[] arguments) {
                startedRuns.add(context);
                return stage();
            }

            @Override
            public java.util.concurrent.CompletionStage<?> end(co.xenastudios.neoskript.api.element.TriggerContext context) {
                ends.add("end");
                return stage();
            }

            @Override
            public void abandoned(co.xenastudios.neoskript.api.element.TriggerContext context) {
                ends.add("abandoned");
                abandonedRuns.add(context);
            }
        }, "tx %text%");
        library.timeout("TestSecTx", Duration.ofSeconds(5));
    }

    @Test
    void aPausingSectionWaitsBeforeAndAfterItsCode() {
        sections();
        run("""
                on load:
                	set {_x} to "kept"
                	tx "a":
                		say "inside %{_x}%"
                		fetch "b"
                		say "after fetch"
                	say "after %{_x}%"
                	tx "skipped":
                		say "never"
                	say "done"
                """);
        assertEquals(List.of(), said);
        stages.get(0).complete(true);
        tick();
        assertEquals(List.of("inside kept"), said);
        stages.get(1).complete(null);
        tick();
        assertEquals(List.of("inside kept", "after fetch"), said);
        assertEquals(List.of("end"), ends);
        stages.get(2).complete(null);
        tick();
        assertEquals(List.of("inside kept", "after fetch", "after kept"), said);
        // A start that gives false skips the code, and the end still runs.
        stages.get(3).complete(false);
        tick();
        assertEquals(2, ends.size());
        stages.get(4).complete(null);
        tick();
        assertEquals(List.of("inside kept", "after fetch", "after kept", "done"), said);
        assertEquals(List.of(), errors);
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aPausingSectionsCodeHasWaited() {
        sections();
        run("""
                on load:
                	tx "a":
                		say has delay before
                """);
        stages.get(0).complete(null);
        tick();
        assertEquals(List.of("true"), said);
        stages.get(1).complete(null);
        tick();
        assertEquals(0, Pauses.pending());
    }

    @Test
    void anEffectCanLeaveThePausingSectionItsIn() {
        sections();
        library.pausingEffect("TestEffLeave", info -> (context, args) ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        co.xenastudios.neoskript.api.element.PausingSection.LEAVE), "fail here");
        run("""
                on load:
                \ttx "a":
                \t\tsay "one"
                \t\tloop 3 times:
                \t\t\tfail here
                \t\t\tsay "never"
                \t\tsay "never either"
                \tsay "after"
                \tfail here
                \tsay "outside, nothing to leave"
                """);
        stages.get(0).complete(null);
        tick();
        assertEquals(List.of("one"), said);
        assertEquals(List.of("end"), ends);
        stages.get(1).complete(null);
        tick();
        assertEquals(List.of("one", "after", "outside, nothing to leave"), said);
        assertEquals(List.of(), errors);
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aKeyAwarePausingEffectCanGetEveryValueUnderAList() {
        final class Batch implements PausingEffect, co.xenastudios.neoskript.api.element.KeyAware {
            @Override
            public java.util.concurrent.CompletionStage<?> start(co.xenastudios.neoskript.api.element.TriggerContext context,
                    Object[] arguments) {
                co.xenastudios.neoskript.runtime.KeyedValues rows = (co.xenastudios.neoskript.runtime.KeyedValues) arguments[0];
                said.add(String.join(",", rows.keys()) + " = " + Values.text(rows.values()));
                return null;
            }

            @Override
            public boolean recursiveKeys() {
                return true;
            }
        }
        library.pausingEffect("TestEffBatch", info -> new Batch(), "batch %objects%");
        run("""
                on load:
                \tset {_rows::1::*} to "Steve" and 100
                \tset {_rows::2::*} to "Alex" and 250
                \tset {_rows::3} to "lone"
                \tbatch {_rows::*}
                """);
        assertEquals(List.of("1::1,1::2,2::1,2::2,3 = Steve, 100, Alex, 250 and lone"), said);
    }

    @Test
    void aPausingSectionLeftAnotherWayIsToldAtOnce() {
        sections();
        library.effect("TestEffBoom", info -> (context, args) -> {
            throw new IllegalStateException("boom");
        }, "boom");
        run("""
                on load:
                \ttx "stop":
                \t\tsay "in"
                \t\tstop
                """);
        stages.get(0).complete(null);
        tick();
        assertEquals(List.of("in"), said);
        assertEquals(List.of("abandoned"), ends, "stop skips the end and tells the section");
        assertEquals(0, co.xenastudios.neoskript.runtime.PausingSections.open());
    }

    @Test
    void exitSectionEndsAPausingSectionAsItsEndDoes() {
        sections();
        run("""
                on load:
                \ttx "a":
                \t\texit section
                \t\tsay "never"
                \tsay "after"
                \tloop 2 times:
                \t\ttx "b":
                \t\t\tcontinue
                \tsay "done"
                """);
        stages.get(0).complete(null);
        tick();
        assertEquals(List.of("end"), ends);
        stages.get(1).complete(null);
        tick();
        assertEquals(List.of("after"), said);
        stages.get(2).complete(null);
        tick();
        stages.get(3).complete(null);
        tick();
        assertEquals(List.of("end", "abandoned", "abandoned"), ends, "continue past it abandons it");
        assertEquals(List.of("after", "done"), said);
        assertEquals(0, co.xenastudios.neoskript.runtime.PausingSections.open());
        assertEquals(0, Pauses.pending());
    }

    @Test
    void aPausingSectionIsntAConditional() {
        sections();
        CompiledScripts compiled = compile("""
                on load:
                \ttx "a":
                \t\texit 1 conditional
                """);
        assertEquals(List.of("Can't stop any conditionals as there are no conditionals present"),
                errors(compiled.diagnostics()));
    }

    @Test
    void aPausingExpressionNeverGivesASentinel() {
        library.pausingExpression("TestExprSentinel", "object", info -> (context, args) ->
                CompletableFuture.completedFuture(PausingEffect.UNCHANGED), "sentinel");
        run("""
                on load:
                \tset {_v} to sentinel
                \tsay "v is %{_v}%"
                """);
        assertEquals(List.of("v is <none>"), said);
    }

    @Test
    void eachRunHasAContextOfItsOwnThoughItsEventIsShared() {
        sections();
        library.effect("TestEffBoom", info -> (context, args) -> {
            throw new IllegalStateException("boom");
        }, "boom");
        CompiledScripts compiled = compile("""
                on load:
                \ttx "a":
                \t\tfetch "waits"
                \t\tsay "a carried on"

                on load:
                \ttx "b":
                \t\tboom
                """);
        assertEquals(List.of(), errors(compiled.diagnostics()));
        // Both triggers are given the same context, as an event's triggers are.
        SimpleTriggerContext shared = new SimpleTriggerContext(null);
        for (var trigger : compiled.triggers()) {
            try {
                trigger.trigger().execute(shared);
            } catch (RuntimeException expected) {
                // b's boom, once its section has started.
            }
        }
        // a's and b's sections started for runs of their own.
        assertEquals(2, startedRuns.size());
        assertTrue(startedRuns.get(0) != startedRuns.get(1) && startedRuns.get(0) != shared);
        stages.get(0).complete(null); // a: into its code, where it waits
        tick();
        stages.get(1).complete(null); // b: into its code, where it throws
        try {
            tick();
        } catch (RuntimeException expected) {
            // b's boom.
        }
        // b throwing abandons b's section only: a still waits in its own.
        assertEquals(List.of(startedRuns.get(1)), abandonedRuns);
        stages.get(2).complete(null); // a's fetch
        tick();
        assertTrue(said.contains("a carried on"));
        stages.get(3).complete(null); // a's end
        tick();
        assertEquals(List.of("abandoned", "end"), ends);
        assertEquals(0, co.xenastudios.neoskript.runtime.PausingSections.open());
    }

    @Test
    void functionsCalledFromNowhereEachHaveARunOfTheirOwn() {
        sections();
        run("""
                function fa():
                \ttx "fa":
                \t\tsay "fa"

                function fb():
                \ttx "fb":
                \t\tsay "fb"

                on load:
                \tfa()
                \tfb()
                """);
        assertEquals(2, startedRuns.size());
        assertTrue(startedRuns.get(0) != startedRuns.get(1));
        stages.get(0).complete(null);
        stages.get(1).complete(null);
        tick();
        assertEquals(List.of("fa", "fb"), said.stream().sorted().toList());
    }
}
