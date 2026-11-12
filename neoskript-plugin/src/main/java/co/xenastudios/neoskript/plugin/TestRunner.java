/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.testing.TestEvent;
import co.xenastudios.neoskript.runtime.testing.TestRun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Test mode: runs every {@code test "name":} in the loaded scripts, writes the results as JSON and stops
 * the server, like Skript's test runner. Turned on with {@code -Dneoskript.testing.enabled=true}; the
 * results go to {@code -Dneoskript.testing.results} (default {@code test_results.json}).
 */
final class TestRunner {

    static final String ENABLED = "neoskript.testing.enabled";
    static final String RESULTS = "neoskript.testing.results";

    private TestRunner() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLED);
    }

    /** How long a test that waits may take to finish, in ticks, before it fails. */
    private static final int TIMEOUT_TICKS = 20 * 60;

    /**
     * Runs the tests one at a time and writes the results, then runs {@code finished}. Call on the thread that owns
     * {@code place}, test-location, which is where tests run: a test that waits (or goes to another thread, on
     * Folia) finishes before the next one starts.
     */
    static void run(CompiledScripts compiled, Logger logger, Scheduler scheduler, Object place, Runnable finished) {
        List<CompiledScripts.CompiledTrigger> tests = compiled.triggers().stream()
                .filter(test -> test.testName() != null)
                .toList();
        Waits.track();
        new Run(compiled, tests, logger, scheduler, place, finished).next(0);
    }

    private static final class Run {
        private final CompiledScripts compiled;
        private final List<CompiledScripts.CompiledTrigger> tests;
        private final Logger logger;
        private final Scheduler scheduler;
        private final Object place;
        private final Runnable finished;
        private final List<String> passed = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private final Map<String, List<String>> failed = new LinkedHashMap<>();

        Run(CompiledScripts compiled, List<CompiledScripts.CompiledTrigger> tests, Logger logger, Scheduler scheduler,
                Object place, Runnable finished) {
            this.compiled = compiled;
            this.tests = tests;
            this.logger = logger;
            this.scheduler = scheduler;
            this.place = place;
            this.finished = finished;
        }

        /** Runs the tests from {@code index} on, until one waits. */
        void next(int index) {
            for (int i = index; i < tests.size(); i++) {
                CompiledScripts.CompiledTrigger test = tests.get(i);
                TestRun.begin();
                TestEvent event = new TestEvent();
                try {
                    test.trigger().execute(new SimpleTriggerContext(event, place));
                } catch (ScriptRuntimeException e) {
                    TestRun.error(e);
                }
                if (Waits.pending(event)) {
                    int resume = i + 1;
                    settle(test, event, 0, () -> next(resume));
                    return;
                }
                record(test);
            }
            try {
                write();
            } catch (IOException e) {
                logger.error("Couldn't write the test results", e);
            }
            finished.run();
        }

        /** Waits for {@code test}'s trigger to carry on and finish, then records it and goes on. */
        private void settle(CompiledScripts.CompiledTrigger test, TestEvent event, int ticks, Runnable then) {
            scheduler.runLater(place, 1, () -> {
                if (Waits.pending(event) && ticks < TIMEOUT_TICKS) {
                    settle(test, event, ticks + 1, then);
                    return;
                }
                if (Waits.pending(event)) {
                    TestRun.fail(test.testName(), 0, "the test didn't finish within " + TIMEOUT_TICKS / 20 + " seconds");
                }
                record(test);
                then.run();
            });
        }

        private void record(CompiledScripts.CompiledTrigger test) {
            if (TestRun.skipped()) {
                skipped.add(test.testName());
            } else if (TestRun.failures().isEmpty()) {
                passed.add(test.testName());
            } else {
                failed.put(test.testName(), TestRun.failures());
            }
        }

        private void write() throws IOException {
            logger.info("Tests: {} passed, {} failed, {} skipped, {} can't run yet", passed.size(), failed.size(),
                    skipped.size(), compiled.unrunnableTests().size());
            failed.forEach((name, failures) -> logger.info("Failed: {}: {}", name, failures));

            StringBuilder json = new StringBuilder("{\n  \"passed\": ").append(array(passed))
                    .append(",\n  \"skipped\": ").append(array(skipped))
                    .append(",\n  \"failed\": {");
            appendMap(json, failed);
            json.append("},\n  \"unrunnable\": {");
            Map<String, List<String>> unrunnable = new LinkedHashMap<>();
            compiled.unrunnableTests().forEach((name, reason) -> unrunnable.put(name, List.of(String.valueOf(reason))));
            appendMap(json, unrunnable);
            json.append("}\n}\n");
            Files.writeString(Path.of(System.getProperty(RESULTS, "test_results.json")), json);
        }
    }

    private static void appendMap(StringBuilder json, Map<String, List<String>> map) {
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            json.append(first ? "\n    " : ",\n    ").append(string(entry.getKey())).append(": ")
                    .append(array(entry.getValue()));
            first = false;
        }
        if (!map.isEmpty()) {
            json.append("\n  ");
        }
    }

    private static String array(List<String> values) {
        List<String> quoted = values.stream().map(TestRunner::string).toList();
        return "[" + String.join(", ", quoted) + "]";
    }

    private static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
