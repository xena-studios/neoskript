/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * M4's progress measure: every one of Skript's own tests, run by NeoSkript in test mode. It reports how
 * many pass, fail, and can't run yet (their code doesn't compile on NeoSkript), and only fails if no
 * results come back. M4 is done when they all pass.
 */
public final class NeoSkriptSkriptSuiteScenario implements Scenario {

    @Override
    public String name() {
        return "neoskript-skript-suite";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path tests = context.config().skriptTests();
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .jvmArg("-Dneoskript.testing.enabled=true")
                .jvmArg("-Dneoskript.testing.results=test_results.json")
                // Script names as Skript's test runner has them: it loads the tests from its source folder.
                .jvmArg("-Dneoskript.testing.scriptsRoot=../../../../../../src/test/skript/tests");
        int files = 0;
        try (Stream<Path> paths = Files.walk(tests)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                builder.file("plugins/NeoSkript/scripts/" + tests.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
                files++;
            }
        }
        try (ServerProcess server = builder.start()) {
            server.awaitExit(Duration.ofMinutes(15));
        }
        Path resultsFile = builder.runDir().resolve("test_results.json");
        if (!Files.exists(resultsFile)) {
            throw new HarnessFailure("NeoSkript didn't write test results. See " + builder.runDir());
        }
        JsonObject results = new Gson().fromJson(Files.readString(resultsFile), JsonObject.class);
        List<String> passed = new ArrayList<>();
        results.getAsJsonArray("passed").forEach(e -> passed.add(e.getAsString()));
        Map<String, Object> failed = new LinkedHashMap<>();
        results.getAsJsonObject("failed").entrySet().forEach(e -> failed.put(e.getKey(), e.getValue().toString()));
        Map<String, Object> unrunnable = new LinkedHashMap<>();
        results.getAsJsonObject("unrunnable").entrySet().forEach(e -> unrunnable.put(e.getKey(), e.getValue().toString()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("files", files);
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("skipped", results.getAsJsonArray("skipped").size());
        report.put("unrunnable", unrunnable);
        context.writeReport(name(), report);
        context.summary("files", files);
        context.summary("passed", passed.size());
        context.summary("failed", failed.size());
        context.summary("cantRunYet", unrunnable.size());
    }
}
