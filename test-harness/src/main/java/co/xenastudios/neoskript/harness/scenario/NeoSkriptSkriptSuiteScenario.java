/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
 *
 * <p>{@code neoskript-skript-suite-skbee} runs the same suite with NeoSkriptCompat and SkBee 3.26.0 installed, and
 * Skript's own runner on Skript 2.17 with the same SkBee: every test that passes on Skript with SkBee must pass on
 * NeoSkript with SkBee. A Skript addon's syntax is tried as Skript tries it, so where it changes how a line of Skript's
 * own reads, it does on Skript too, and those tests fail on both. Paper 26.x only, as SkBee needs Minecraft 26.1.
 */
public final class NeoSkriptSkriptSuiteScenario implements Scenario {

    private final boolean withSkBee;

    public NeoSkriptSkriptSuiteScenario() {
        this(false);
    }

    public NeoSkriptSkriptSuiteScenario(boolean withSkBee) {
        this.withSkBee = withSkBee;
    }

    @Override
    public String name() {
        return withSkBee ? "neoskript-skript-suite-skbee" : "neoskript-skript-suite";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return !withSkBee || (environment.platform() == Environment.Platform.PAPER
                && environment.minecraftVersion().startsWith("26."));
    }

    /** The tests that threw on Skript: its log names the trigger of each severe error. */
    private static java.util.Set<String> threw(Path log) throws java.io.IOException {
        java.util.Set<String> tests = new java.util.TreeSet<>();
        if (!Files.isRegularFile(log)) {
            return tests;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#!#! Current trigger: test \"(.+?)\"")
                .matcher(Files.readString(log));
        while (matcher.find()) {
            tests.add(matcher.group(1));
        }
        return tests;
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
        if (withSkBee) {
            NeoSkriptHooksScenario.PinnedPlugin skbee = SkBeeDifferentialScenario.SKBEE;
            builder.plugin(context.compatJar()).plugin(context.download(skbee.url(), skbee.sha256(), skbee.fileName()));
        }
        int files = 0;
        try (Stream<Path> paths = Files.walk(tests)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                builder.file("plugins/NeoSkript/scripts/" + tests.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
                files++;
            }
        }
        boolean skbeeEnabled;
        try (ServerProcess server = builder.start()) {
            server.awaitExit(Duration.ofMinutes(15));
            skbeeEnabled = server.lines(0).stream().anyMatch(line -> line.contains("[SkBee] Successfully enabled"));
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
        if (withSkBee) {
            if (!skbeeEnabled) {
                throw new HarnessFailure("SkBee didn't enable on NeoSkript. See " + builder.runDir());
            }
            NeoSkriptHooksScenario.PinnedPlugin skbee = SkBeeDifferentialScenario.SKBEE;
            JsonObject skript = SkriptTestSuiteScenario.results(context, context.environment().name() + "-skbee",
                    List.of(context.download(skbee.url(), skbee.sha256(), skbee.fileName())));
            // Passing on Skript: in its succeeded list. A test whose file doesn't load there is in neither list.
            java.util.Set<String> passOnSkript = new java.util.TreeSet<>();
            skript.getAsJsonArray("succeeded").forEach(test -> passOnSkript.add(test.getAsString()));
            // Skript's runner counts a test that throws as passed; NeoSkript's reports the error. A test failing here
            // only with errors, which threw on Skript too (SkBee's own code throwing the same), is the same on both.
            java.util.Set<String> threwOnSkript = threw(context.config().skriptCorpus()
                    .resolve("build/test_runners").resolve(context.environment().name() + "-skbee/logs/latest.log"));
            java.util.Set<String> onlyOurs = new java.util.TreeSet<>();
            java.util.Set<String> threwOnBoth = new java.util.TreeSet<>();
            failed.forEach((test, messages) -> {
                if (!passOnSkript.contains(test)) {
                    return;
                }
                if (threwOnSkript.contains(test) && String.valueOf(messages).matches("\\[(\"Error: [^\"]*\",?)+]")) {
                    threwOnBoth.add(test);
                } else {
                    onlyOurs.add(test);
                }
            });
            unrunnable.keySet().stream().filter(passOnSkript::contains).forEach(onlyOurs::add);
            java.util.Set<String> notOnSkript = new java.util.TreeSet<>(failed.keySet());
            notOnSkript.removeAll(passOnSkript);
            report.put("notPassingOnSkriptWithSkBee", notOnSkript);
            report.put("threwOnBoth", threwOnBoth);
            context.writeReport(name(), report);
            context.summary("notPassingOnSkriptWithSkBee", notOnSkript.size());
            context.summary("threwOnBoth", threwOnBoth.size());
            if (!onlyOurs.isEmpty()) {
                throw new HarnessFailure(onlyOurs.size() + " of Skript's tests fail with NeoSkriptCompat and SkBee"
                        + " installed but pass on Skript 2.17 with SkBee: " + onlyOurs + " " + failed);
            }
        }
    }
}
