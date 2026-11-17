/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Where a script is stuck or recursing. A trigger in an endless loop freezes its thread as in Skript, and Paper's
 * watchdog then prints the thread's stack, which leaves hidden classes out; so a loop that runs for seconds without
 * waiting is named, with its line, by {@link co.xenastudios.neoskript.runtime.LongLoops}.
 */
class StackFramesTest {

    private final List<StackTraceElement[]> captured = new ArrayList<>();
    private final int[] rounds = {0};

    private CompiledScripts compile(String name, String text) {
        Library library = new Library();
        library.type("number", "number", Number.class);
        library.type("timespan", "timespan", co.xenastudios.neoskript.runtime.Timespan.class);
        library.type("integer", "integer", Long.class);
        library.subtype("integer", "number");
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.effect("Capture", info -> (context, args) -> captured.add(Thread.currentThread().getStackTrace()),
                "capture the stack");
        library.effect("Nothing", info -> (context, args) -> {}, "do nothing");
        library.condition("KeepGoing", info -> (context, args) -> rounds[0]++ < 3_000_000, "keep going");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile(name, text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics());
        compiled.linkFunctions();
        return compiled;
    }

    @Test
    void stackTracesLeaveCompiledCodeOut() {
        // Why loops are checked: the watchdog's thread dump can't show where a script is stuck.
        CompiledScripts compiled = compile("loops/spin.sk", """
                function inner(n: number):
                \tcapture the stack
                on load:
                \tinner(1)
                """);
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertTrue(Arrays.stream(captured.getFirst()).noneMatch(frame -> "loops/spin.sk".equals(frame.getFileName())));
    }

    @Test
    void aLongLoopIsNamedWithItsLine() {
        List<String> logged = new ArrayList<>();
        System.setProperty(co.xenastudios.neoskript.runtime.LongLoops.WARN_AFTER_PROPERTY, "0");
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(co.xenastudios.neoskript.runtime.errors.RuntimeError error) {}

            @Override
            public void printFrameOutput(co.xenastudios.neoskript.runtime.errors.ErrorFrame.Output output,
                    co.xenastudios.neoskript.runtime.errors.RuntimeError.Level level) {}

            @Override
            public void logError(String message) {
                logged.add(message);
            }
        }, co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT,
                co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT);
        try {
            CompiledScripts compiled = compile("loops/spin.sk", """
                    on load:
                    \tset {_i} to 0
                    \twhile keep going:
                    \t\tdo nothing
                    """);
            compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        } finally {
            System.clearProperty(co.xenastudios.neoskript.runtime.LongLoops.WARN_AFTER_PROPERTY);
            RuntimeErrors.install(null, co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT,
                    co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT);
        }
        assertEquals(1, logged.size(), logged.toString());
        assertTrue(logged.getFirst().startsWith("A loop in loops/spin.sk, line 3, has been running for "), logged.getFirst());
    }

    @Test
    void aLoopThatWaitsEachTimeRoundIsNeverReported() {
        // Waiting time counted as running time: a loop with 'wait 1 tick' was reported after about 55 minutes.
        List<String> logged = new ArrayList<>();
        java.util.ArrayDeque<Runnable> later = new java.util.ArrayDeque<>();
        System.setProperty(co.xenastudios.neoskript.runtime.LongLoops.WARN_AFTER_PROPERTY, "0");
        logInto(logged);
        co.xenastudios.neoskript.runtime.Waits.install(new co.xenastudios.neoskript.runtime.scheduler.Scheduler() {
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
        }, e -> {
            throw e;
        });
        try {
            CompiledScripts compiled = compile("loops/waits.sk", """
                    on load:
                    \twhile keep going:
                    \t\twait 1 second
                    """);
            rounds[0] = 3_000_000 - 200_000;
            compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
            int resumed = 0;
            while (!later.isEmpty()) {
                later.poll().run();
                resumed++;
            }
            assertTrue(resumed >= 199_000, "the loop waited " + resumed + " times");
        } finally {
            System.clearProperty(co.xenastudios.neoskript.runtime.LongLoops.WARN_AFTER_PROPERTY);
            logInto(null);
        }
        assertEquals(List.of(), logged);
    }

    /** Sends {@link RuntimeErrors#logError} to {@code logged}, or nowhere with {@code null}. */
    private static void logInto(List<String> logged) {
        RuntimeErrors.install(logged == null ? null : new RuntimeErrors.Consumer() {
            @Override
            public void printError(co.xenastudios.neoskript.runtime.errors.RuntimeError error) {}

            @Override
            public void printFrameOutput(co.xenastudios.neoskript.runtime.errors.ErrorFrame.Output output,
                    co.xenastudios.neoskript.runtime.errors.RuntimeError.Level level) {}

            @Override
            public void logError(String message) {
                logged.add(message);
            }
        }, co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT,
                co.xenastudios.neoskript.runtime.errors.ErrorFrame.Limits.DEFAULT);
    }

    @Test
    void endlessRecursionStopsTheInnermostCallAsInSkript() {
        // Skript catches the overflow in the innermost call with room to spare, tells admins once, and the callers
        // carry on with nothing from it.
        List<String> reported = new ArrayList<>();
        co.xenastudios.neoskript.runtime.Overflows.install(reported::add);
        try {
            CompiledScripts compiled = compile("loops/recurse.sk", """
                    function down(n: number) :: number:
                    \treturn down({_n} + 1)
                    on load:
                    \tset {_x} to down(1)
                    \tcapture the stack
                    """);
            compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        } finally {
            co.xenastudios.neoskript.runtime.Overflows.install(null);
        }
        assertEquals(List.of("recurse.sk"), reported);
        assertEquals(1, captured.size(), "the trigger carried on after the call");
    }
}
