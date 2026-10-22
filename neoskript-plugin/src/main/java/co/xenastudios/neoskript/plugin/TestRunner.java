/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
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

    /** Runs the tests and writes the results. Call on the global thread. */
    static void run(CompiledScripts compiled, Logger logger) throws IOException {
        List<String> passed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, List<String>> failed = new LinkedHashMap<>();
        for (CompiledScripts.CompiledTrigger test : compiled.triggers()) {
            if (test.testName() == null) {
                continue;
            }
            TestRun.begin();
            try {
                test.trigger().execute(new SimpleTriggerContext(null));
            } catch (ScriptRuntimeException e) {
                TestRun.error(e);
            }
            if (TestRun.skipped()) {
                skipped.add(test.testName());
            } else if (TestRun.failures().isEmpty()) {
                passed.add(test.testName());
            } else {
                failed.put(test.testName(), TestRun.failures());
            }
        }
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
