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
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.region.RegionAccess;
import co.xenastudios.neoskript.runtime.region.Regions;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Region ownership as code compiled for Folia checks it (plan 3.3), against a pretend server: "here" is the
 * region this test's thread owns, and anything else belongs to another thread, whose tasks the test runs itself.
 */
class RegionOwnershipTest {

    /** An entity or place, in a region. */
    record Thing(String name, String region) {}

    static final class Place {
        final String region;

        Place(String region) {
            this.region = region;
        }
    }

    private final Thing near = new Thing("near", "here");
    private final Thing far = new Thing("far", "there");
    private final List<String> said = new ArrayList<>();
    private final List<RuntimeError> errors = new ArrayList<>();
    /** Tasks run on another thread: what they're for, and the task. */
    private final Deque<Object[]> elsewhere = new ArrayDeque<>();
    /** The region whose thread is running now. */
    private String running = "here";
    private Library library;

    private final RegionAccess access = new RegionAccess() {
        @Override
        public Kind kindOf(Class<?> type) {
            return type == Thing.class ? Kind.ENTITY : type == Place.class ? Kind.LOCATION : Kind.NONE;
        }

        @Override
        public Kind kind(Object value) {
            return value instanceof Thing ? Kind.ENTITY : value instanceof Place ? Kind.LOCATION : Kind.NONE;
        }

        @Override
        public boolean owns(Object value) {
            return switch (value) {
                case Thing thing -> thing.region().equals(running);
                case Place place -> place.region.equals(running);
                case null -> running.equals("global");
                default -> true;
            };
        }

        @Override
        public void execute(Object value, Runnable task) {
            elsewhere.add(new Object[] {value, task});
        }

        @Override
        public String describe(Object value) {
            return "the thing " + ((Thing) value).name();
        }
    };

    private final Scheduler scheduler = new Scheduler() {
        @Override
        public void runGlobal(Runnable task) {
            elsewhere.add(new Object[] {null, task});
        }

        @Override
        public void runGlobalLater(long ticks, Runnable task) {
            runGlobal(task);
        }

        @Override
        public Runnable runGlobalRepeating(long ticks, Runnable task) {
            return () -> {};
        }

        @Override
        public void runLater(Object owner, long ticks, Runnable task) {
            elsewhere.add(new Object[] {owner, task});
        }
    };

    @BeforeEach
    void library() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.type("timespan", "timespan", co.xenastudios.neoskript.runtime.Timespan.class);
        library.type("entity", "entity", Thing.class);
        library.type("location", "location", Place.class);
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.effect("EffMessage", info -> (context, args) -> said.add(String.valueOf(args[0])), "say %string%");
        library.effect("Poke", info -> (context, args) -> {
            for (Object thing : (Object[]) args[0]) {
                said.add("poked " + ((Thing) thing).name() + " on " + running);
            }
        }, "poke %entities%");
        library.expression("Near", "entity", info -> (context, args) -> near, "the near thing");
        library.expression("Far", "entity", info -> (context, args) -> far, "the far thing");
        library.expression("Both", "entity", info -> (context, args) -> new Object[] {near, far}, "both things");
        library.expression("FarPlace", "location", info -> (context, args) -> new Place("there"), "the far place");
        library.expression("NameOf", "string", info -> (context, args) -> ((Thing) args[0]).name() + "!",
                "name of %entity%");
        library.expression("Refused", "string", info -> (context, args) -> {
            IllegalStateException refused = new IllegalStateException("Accessing entity state off owning region's thread");
            refused.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("ca.spottedleaf.moonrise.common.util.TickThread", "ensureTickThread", null, 1)});
            throw refused;
        }, "something folia refuses");
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(RuntimeError error) {
                errors.add(error);
            }

            @Override
            public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {}

            @Override
            public void logError(String message) {}
        }, new ErrorFrame.Limits(100, 100, 100, 1), ErrorFrame.Limits.DEFAULT);
        Waits.install(scheduler, e -> {
            throw e;
        });
    }

    @AfterEach
    void uninstall() {
        Regions.uninstall();
    }

    private void folia() {
        Regions.install(access, e -> {
            throw e;
        });
    }

    private void run(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        CompiledScripts compiled = new ScriptCompiler(library, new co.xenastudios.neoskript.runtime.variables.VariableStore())
                .compile(parsed);
        assertEquals(List.of(), compiled.diagnostics());
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            trigger.trigger().execute(new SimpleTriggerContext(null));
        }
    }

    /** Runs the tasks sent to other threads, each on the thread it was for, until there are none left. */
    private void runElsewhere() {
        while (!elsewhere.isEmpty()) {
            Object[] next = elsewhere.poll();
            String outer = running;
            running = switch (next[0]) {
                case Thing thing -> thing.region();
                case Place place -> place.region;
                case null, default -> "here";
            };
            try {
                ((Runnable) next[1]).run();
            } finally {
                running = outer;
            }
        }
    }

    @Test
    void readingAnEntityInAnotherRegionIsARuntimeErrorAndGivesNothing() {
        folia();
        run("on load:\n\tsay \"%name of the near thing%\"\n\tsay \"%name of the far thing%\"\n\tsay \"after\"\n");
        assertEquals(List.of("near!", "<none>", "after"), said);
        assertEquals(1, errors.size());
        RuntimeError error = errors.getFirst();
        assertEquals(3, error.source().lineNumber());
        assertEquals("test.sk", error.source().script());
        assertTrue(error.error().startsWith("The thing far is in another region"), error.error());
    }

    @Test
    void onPaperNothingIsChecked() {
        run("on load:\n\tsay \"%name of the far thing%\"\n\tpoke the far thing\n");
        assertEquals(List.of("far!", "poked far on here"), said);
        assertEquals(List.of(), errors);
        assertTrue(elsewhere.isEmpty());
    }

    @Test
    void whatFoliaRefusesIsARuntimeErrorNotAStackTrace() {
        folia();
        run("on load:\n\tsay \"x\"\n\tsay something folia refuses\n\tsay \"after\"\n");
        assertEquals(List.of("x", "null", "after"), said);
        assertEquals(1, errors.size());
        assertEquals(3, errors.getFirst().source().lineNumber());
        assertTrue(errors.getFirst().error().contains("Accessing entity state off owning region's thread"),
                errors.getFirst().error());
    }

    @Test
    void effectsOnEntitiesInAnotherRegionRunOnTheirThreadWithoutWaiting() {
        folia();
        run("on load:\n\tpoke the near thing\n\tpoke the far thing\n\tsay \"after\"\n");
        assertEquals(List.of("poked near on here", "after"), said);
        runElsewhere();
        assertEquals(List.of("poked near on here", "after", "poked far on there"), said);
    }

    @Test
    void anEffectOnSeveralEntitiesRunsOnEachOnesThread() {
        folia();
        run("on load:\n\tpoke both things\n");
        assertEquals(List.of("poked near on here"), said);
        runElsewhere();
        assertEquals(List.of("poked near on here", "poked far on there"), said);
    }

    @Test
    void runOnRunsItsCodeOnTheEntitysThreadThenCarriesOnAtHome() {
        folia();
        run("on load:\n\tsay \"before\"\n\trun on the far thing:\n\t\tsay \"%name of the far thing%\"\n"
                + "\t\tpoke the far thing\n\tsay \"after\"\n\tpoke the near thing\n");
        assertEquals(List.of("before"), said);
        runElsewhere();
        assertEquals(List.of("before", "far!", "poked far on there", "after", "poked near on here"), said);
        assertEquals(List.of(), errors);
    }

    @Test
    void runOnWhatThisThreadOwnsCarriesStraightOn() {
        folia();
        run("on load:\n\trun on the near thing:\n\t\tsay \"in\"\n\tsay \"after\"\n");
        // The trigger's own thread here is the global one, which it goes back to.
        assertEquals(List.of("in"), said);
        runElsewhere();
        assertEquals(List.of("in", "after"), said);
    }

    @Test
    void aWaitInRunOnCarriesOnOnTheSameThread() {
        folia();
        run("on load:\n\trun on the far thing:\n\t\twait 1 tick\n\t\tsay \"%name of the far thing%\"\n\tsay \"after\"\n");
        runElsewhere();
        assertEquals(List.of("far!", "after"), said);
        assertEquals(List.of(), errors);
    }

    @Test
    void runOnALocation() {
        folia();
        run("on load:\n\trun on the far place:\n\t\tpoke the far thing\n");
        runElsewhere();
        assertEquals(List.of("poked far on there"), said);
    }

    @Test
    void onPaperRunOnJustRunsItsCode() {
        run("on load:\n\tsay \"before\"\n\trun on the far thing:\n\t\tsay \"in\"\n\tsay \"after\"\n");
        assertEquals(List.of("before", "in", "after"), said);
        assertTrue(elsewhere.isEmpty());
    }

    @Test
    void runOnNothingSkipsItsCode() {
        library.expression("Nothing", "entity", info -> (context, args) -> null, "no thing");
        run("on load:\n\trun on no thing:\n\t\tsay \"in\"\n\tsay \"after\"\n");
        assertEquals(List.of("after"), said);
    }

    @Test
    void codeInRunOnCantJumpOutOfIt() {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk",
                "on load:\n\tloop the near thing and the far thing:\n\t\trun on the far thing:\n\t\t\tcontinue\n")));
        CompiledScripts compiled = new ScriptCompiler(library, new co.xenastudios.neoskript.runtime.variables.VariableStore())
                .compile(parsed);
        assertEquals(1, compiled.diagnostics().size(), compiled.diagnostics().toString());
        assertTrue(compiled.diagnostics().getFirst().message().startsWith("Can't leave a 'run on' section"),
                compiled.diagnostics().toString());
    }
}
