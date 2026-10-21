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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the reference Skript's own test suite in Skript's test mode and records the results.
 *
 * <p>This is the baseline NeoSkript has to match. Skript's own failures are recorded, not treated as
 * harness failures. The scenario only fails if no results come out.
 */
public final class SkriptTestSuiteScenario implements Scenario {

    @Override
    public String name() {
        return "skript-test-suite";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path tests = context.config().skriptTests();
        if (!Files.isDirectory(tests)) {
            throw new HarnessFailure("Skript's test corpus is missing at " + tests + ". Run :test-harness:fetchSkriptTests");
        }
        // Run where Skript's own runner does, so tests that check relative script paths pass.
        Path runDir = context.config().skriptCorpus().resolve("build/test_runners").resolve(context.environment().name());
        ServerBuilder builder = context.server(runDir)
                .plugin(context.skriptJar())
                .jvmArg("-ea")
                .jvmArg("-Dskript.testing.enabled=true")
                .jvmArg("-Dskript.testing.dir=" + tests)
                .jvmArg("-Dskript.testing.devMode=false")
                .jvmArg("-Dskript.testing.genDocs=false")
                .jvmArg("-Dskript.testing.junit=false")
                .jvmArg("-Dskript.testing.results=test_results.json");
        try (ServerProcess server = builder.start()) {
            // Skript stops the server by itself once the tests finish.
            server.awaitExit(Duration.ofMinutes(30));
        }

        Path resultsFile = builder.runDir().resolve("test_results.json");
        if (!Files.isRegularFile(resultsFile)) {
            throw new HarnessFailure("Skript didn't write " + resultsFile);
        }
        JsonObject results = JsonParser.parseString(Files.readString(resultsFile)).getAsJsonObject();
        int succeeded = results.getAsJsonArray("succeeded").size();
        int failed = results.getAsJsonObject("failed").size();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("skript", context.referenceSkript().version());
        report.put("environment", context.environment().name());
        report.put("succeeded", succeeded);
        report.put("failed", failed);
        report.put("results", results);
        context.writeReport("skript-test-suite", report);
        context.summary("succeeded", succeeded);
        context.summary("failed", failed);
    }
}
