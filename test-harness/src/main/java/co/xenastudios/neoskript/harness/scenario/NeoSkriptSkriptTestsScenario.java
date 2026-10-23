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

/**
 * The M2 exit check: Skript's own control-flow and function tests, run by NeoSkript in test mode on every
 * server. The {@link #MUST_PASS} tests have to pass; the {@link #PENDING} ones need later milestones. Any
 * other test failing, or not running, fails the check too, so new tests have to be sorted into one list.
 */
public final class NeoSkriptSkriptTestsScenario implements Scenario {

    /** Skript's tests for control flow and functions, relative to its test folder. */
    static final List<String> FILES = List.of(
            "syntaxes/sections/SecConditional.sk",
            "syntaxes/sections/SecLoop.sk",
            "syntaxes/sections/SecWhile.sk",
            "syntaxes/effects/EffContinue.sk",
            "syntaxes/expressions/ExprLoopValue.sk",
            "syntaxes/structures/StructFunction.sk",
            "misc/function overloading.sk");

    /** Tests that have to pass: every control-flow and function test that doesn't need a later milestone. */
    static final List<String> MUST_PASS = List.of(
            "function overloading with 1 parameter",
            "function overloading with 2 parameters",
            "function overloading with different return types",
            "function overloading with supertype",
            "function overloading with single param lists",
            "continue effect",
            "SecConditional - ParseIf",
            "SecConditional",
            "SecConditional - if all true",
            "SecConditional - if all false",
            "SecConditional - if any true",
            "SecConditional - if any false",
            "SecConditional - if any else",
            "SecConditional - if all else",
            "SecConditional - if all by default",
            "SecConditional - else if all true",
            "SecConditional - else if any true",
            "SecConditional - else if any false",
            "SecConditional - else if all false",
            "SecConditional - starting with a non-multiline conditional",
            "SecConditional - non-multiline conditional in the middle",
            "SecConditional - non-multiline conditional at the end",
            "SecConditional - repeating then sections",
            "SecConditional - delay logic 1",
            "SecConditional - delay logic 2",
            "SecConditional - delay logic 3",
            "SecConditional - delay logic 4",
            "SecLoop",
            "while section",
            "functions",
            "function list argument with a single default value",
            "function argument with a default value",
            "function default arguments");

    /** Tests in these files that need later milestones, and why. They're reported but don't fail the check. */
    static final Map<String, String> PENDING = Map.of(
            "loop peeking", "Skript's test-only loop peeking expression and its restrictions (M4)",
            "function structure arguments", "the location() function and Skript's 'Did you mean' suggestion (M4)",
            "named function arguments", "the location() function (M4)",
            "named function arguments with single list params", "the type Skript reports for nested lists (M4)",
            "previous and next loop value", "item literals (M4)",
            "literal type parsing", "spawning entities (M4)",
            "duplicate keys in function arguments", "keyed list arguments (M4)",
            "clone objects at function boundary", "vectors (M4)");

    @Override
    public String name() {
        return "neoskript-skript-tests";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
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
        for (String file : FILES) {
            builder.file("plugins/NeoSkript/scripts/" + file, Files.readString(tests.resolve(file)));
        }
        try (ServerProcess server = builder.start()) {
            server.awaitExit(Duration.ofMinutes(10));
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
        int skipped = results.getAsJsonArray("skipped").size();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("files", FILES);
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("skipped", skipped);
        report.put("unrunnable", unrunnable);
        context.writeReport(name(), report);
        context.summary("passed", passed.size());
        context.summary("failed", failed.size());
        context.summary("cantRunYet", unrunnable.size());

        List<String> missing = MUST_PASS.stream().filter(test -> !passed.contains(test)).toList();
        List<String> unexpected = new ArrayList<>();
        failed.keySet().stream().filter(test -> !PENDING.containsKey(test)).forEach(unexpected::add);
        unrunnable.keySet().stream().filter(test -> !PENDING.containsKey(test)).forEach(unexpected::add);
        context.summary("pending", PENDING.size());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new HarnessFailure("Didn't pass: " + missing + "; failing or not running and not pending: " + unexpected
                    + ". Failures: " + failed + ". Not running: " + unrunnable);
        }
    }
}
