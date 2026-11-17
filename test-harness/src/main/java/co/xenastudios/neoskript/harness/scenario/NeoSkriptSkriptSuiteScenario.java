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

    /** The package SkBee's own code is in: an error counts as SkBee's only with a frame of it. */
    private static final String SKBEE_PACKAGE = "com.shanebeestudios.skbee.";

    /** At least this many of Skript's tests pass on Skript with SkBee, or the comparison means nothing. */
    private static final int SKRIPT_FLOOR = 800;

    /**
     * Tests that pass on Skript with SkBee and aren't run on NeoSkript, with why. Each is left out of the plain suite
     * the same way.
     */
    private static final Map<String, String> KNOWN_SKIPPED = Map.of("health utils update",
            "uses 'on test', an event of Skript's test runner that NeoSkript's runner doesn't have (not run in the"
                    + " plain suite either)");

    /**
     * What SkBee's own code threw on Skript, by test: each severe error in its log whose stack has a frame of SkBee's,
     * with the trigger (the test) it names, as the exception's text ({@code java.lang.X: message}).
     */
    private static Map<String, java.util.Set<String>> skbeeThrew(Path log) throws java.io.IOException {
        Map<String, java.util.Set<String>> threw = new java.util.TreeMap<>();
        if (!Files.isRegularFile(log)) {
            return threw;
        }
        java.util.regex.Pattern trigger = java.util.regex.Pattern.compile("#!#! Current trigger: test \"(.+?)\"");
        java.util.regex.Pattern cause = java.util.regex.Pattern.compile("#!#! (?:Caused by: )?([\\w.$]+(?:Exception|Error)"
                + "(?::.*)?)$");
        for (String block : Files.readString(log).split("#!#! \\[Skript] Severe Error:")) {
            java.util.regex.Matcher test = trigger.matcher(block);
            if (!test.find() || !block.contains("at SkBee-") && !block.contains(SKBEE_PACKAGE)) {
                continue;
            }
            java.util.Set<String> texts = threw.computeIfAbsent(test.group(1), name -> new java.util.TreeSet<>());
            for (String line : block.split("\\R")) {
                String text = line.replaceFirst("^\\[[^]]*] \\[[^]]*]: ", "").strip();
                java.util.regex.Matcher matcher = cause.matcher(text);
                if (matcher.find()) {
                    texts.add(matcher.group(1).strip());
                }
            }
        }
        return threw;
    }

    /**
     * Whether a test failed on NeoSkript only because SkBee's own code threw, the same as on Skript: each of its
     * messages is NeoSkriptCompat's error for SkBee's code throwing, with an exception SkBee threw in that test on
     * Skript.
     */
    private static boolean threwOnBoth(String messages, java.util.Set<String> onSkript) {
        if (onSkript == null || onSkript.isEmpty()) {
            return false;
        }
        java.util.regex.Pattern skbee = java.util.regex.Pattern.compile(
                "^Error: .*?: [\\w.$]+AddonError: SkBee's \\w+ threw in SkBee's code: (.+)$");
        com.google.gson.JsonArray all = com.google.gson.JsonParser.parseString(messages).getAsJsonArray();
        for (com.google.gson.JsonElement message : all) {
            java.util.regex.Matcher matcher = skbee.matcher(message.getAsString());
            if (!matcher.matches() || !onSkript.contains(matcher.group(1).strip())) {
                return false;
            }
        }
        return !all.isEmpty();
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
            Path skriptLog = context.config().skriptCorpus().resolve("build/test_runners")
                    .resolve(context.environment().name() + "-skbee/logs/latest.log");
            // Not vacuous: SkBee enabled on Skript, and Skript ran its suite.
            if (!Files.isRegularFile(skriptLog) || !Files.readString(skriptLog).contains("[SkBee] Successfully enabled")) {
                throw new HarnessFailure("SkBee didn't enable on Skript. See " + skriptLog);
            }
            if (passOnSkript.size() < SKRIPT_FLOOR) {
                throw new HarnessFailure("Only " + passOnSkript.size() + " of Skript's tests passed on Skript with SkBee,"
                        + " fewer than " + SKRIPT_FLOOR + ": the comparison means nothing. See " + skriptLog);
            }
            // Skript's runner counts a test that throws as passed; NeoSkript's reports the error. A test that failed
            // here only because SkBee's own code threw, and threw the same in SkBee's code on Skript, is the same.
            Map<String, java.util.Set<String>> skbeeThrew = skbeeThrew(skriptLog);
            java.util.Set<String> passedHere = new java.util.HashSet<>(passed);
            java.util.Set<String> onlyOurs = new java.util.TreeSet<>();
            java.util.Set<String> threwOnBoth = new java.util.TreeSet<>();
            java.util.Set<String> knownSkipped = new java.util.TreeSet<>();
            // Every test that passes on Skript with SkBee passes here, threw in SkBee's code on both, or is a known
            // skip: failing, can't run yet, skipped or missing are all regressions.
            for (String test : passOnSkript) {
                if (passedHere.contains(test)) {
                    continue;
                }
                Object messages = failed.get(test);
                if (messages != null && threwOnBoth(String.valueOf(messages), skbeeThrew.get(test))) {
                    threwOnBoth.add(test);
                } else if (messages == null && KNOWN_SKIPPED.containsKey(test) && !unrunnable.containsKey(test)) {
                    knownSkipped.add(test + " (" + KNOWN_SKIPPED.get(test) + ")");
                } else {
                    onlyOurs.add(test);
                }
            }
            java.util.Set<String> notOnSkript = new java.util.TreeSet<>(failed.keySet());
            notOnSkript.addAll(unrunnable.keySet());
            notOnSkript.removeAll(passOnSkript);
            report.put("passedOnSkriptWithSkBee", passOnSkript.size());
            report.put("notPassingOnSkriptWithSkBee", notOnSkript);
            report.put("threwOnBoth", threwOnBoth);
            report.put("knownSkipped", knownSkipped);
            context.writeReport(name(), report);
            context.summary("passedOnSkriptWithSkBee", passOnSkript.size());
            context.summary("notPassingOnSkriptWithSkBee", notOnSkript.size());
            context.summary("threwOnBoth", threwOnBoth.size());
            context.summary("knownSkipped", knownSkipped.size());
            if (!onlyOurs.isEmpty()) {
                throw new HarnessFailure(onlyOurs.size() + " of Skript's tests pass on Skript 2.17 with SkBee but not"
                        + " with NeoSkriptCompat and SkBee installed: " + onlyOurs + "; failed here: " + failed);
            }
        }
    }
}
