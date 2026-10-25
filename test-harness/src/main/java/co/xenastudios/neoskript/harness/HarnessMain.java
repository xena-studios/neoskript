/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import co.xenastudios.neoskript.harness.scenario.NeoSkriptBootScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptEventsScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptLanguageScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptMigrationScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptParseScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptCommandsScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptCorpusScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptItemsScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptSkriptSuiteScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptReloadScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptAutoReloadScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptRunScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptSkriptTestsScenario;
import co.xenastudios.neoskript.harness.scenario.NeoSkriptVariablesScenario;
import co.xenastudios.neoskript.harness.scenario.SkriptConflictScenario;
import co.xenastudios.neoskript.harness.scenario.SkriptCorpusScenario;
import co.xenastudios.neoskript.harness.scenario.SkriptLoadBenchmark;
import co.xenastudios.neoskript.harness.scenario.SkriptSmokeScenario;
import co.xenastudios.neoskript.harness.scenario.SkriptTestSuiteScenario;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs the selected scenarios on the selected environments and prints a summary. */
public final class HarnessMain {

    static final List<Scenario> SCENARIOS = List.of(
            new NeoSkriptBootScenario(),
            new NeoSkriptRunScenario(),
            new NeoSkriptLanguageScenario(),
            new NeoSkriptSkriptTestsScenario(),
            new NeoSkriptReloadScenario(),
            new NeoSkriptAutoReloadScenario(),
            new NeoSkriptEventsScenario(),
            new NeoSkriptCommandsScenario(),
            new NeoSkriptCorpusScenario(),
            new NeoSkriptItemsScenario(),
            new NeoSkriptSkriptSuiteScenario(),
            new NeoSkriptVariablesScenario(),
            new NeoSkriptMigrationScenario(),
            new SkriptConflictScenario(),
            new SkriptSmokeScenario(),
            new SkriptCorpusScenario(),
            new NeoSkriptParseScenario(),
            new SkriptTestSuiteScenario(),
            new SkriptLoadBenchmark());

    record Result(String environment, String scenario, boolean passed, Duration duration, String message,
            Map<String, Object> summary) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseOptions(args);
        String env = options.getOrDefault("env", "all");
        String group = options.getOrDefault("group", "smoke").toUpperCase(Locale.ROOT);
        String only = options.getOrDefault("scenario", "");

        HarnessConfig config = HarnessConfig.fromSystemProperties();
        List<Environment> environments = Environment.loadAll(config.environmentsDir()).stream()
                .filter(e -> env.equals("all") || e.name().equals(env))
                .toList();
        if (environments.isEmpty()) {
            throw new IllegalArgumentException("No environment named '" + env + "' in " + config.environmentsDir());
        }
        List<Scenario> scenarios = SCENARIOS.stream()
                .filter(s -> only.isEmpty() ? group.equals("ALL") || s.group().name().equals(group) : s.name().equals(only))
                .toList();
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("No scenarios match group '" + group + "' / scenario '" + only + "'");
        }

        Downloads downloads = new Downloads(config.cacheDir().resolve("downloads"));
        ReferenceSkript referenceSkript = ReferenceSkript.load(config.referenceSkriptFile());
        List<Result> results = new ArrayList<>();
        for (Environment environment : environments) {
            for (Scenario scenario : scenarios) {
                if (scenario.supports(environment)) {
                    results.add(run(config, environment, scenario, downloads, referenceSkript));
                }
            }
        }

        printSummary(results);
        Files.createDirectories(config.reportsDir());
        Path summaryFile = config.reportsDir().resolve("summary.json");
        Files.writeString(summaryFile, new GsonBuilder().setPrettyPrinting().create().toJson(results.stream()
                .map(r -> Map.of(
                        "environment", r.environment(),
                        "scenario", r.scenario(),
                        "passed", r.passed(),
                        "seconds", r.duration().toMillis() / 1000.0,
                        "message", r.message(),
                        "summary", r.summary()))
                .toList()) + "\n");
        System.out.println("Reports: " + config.reportsDir());
        if (results.stream().anyMatch(r -> !r.passed())) {
            System.exit(1);
        }
    }

    private static Result run(
            HarnessConfig config,
            Environment environment,
            Scenario scenario,
            Downloads downloads,
            ReferenceSkript referenceSkript) {
        System.out.printf("[harness] %s / %s ...%n", environment.name(), scenario.name());
        ScenarioContext context = new ScenarioContext(config, environment, scenario, downloads, referenceSkript);
        long start = System.nanoTime();
        boolean passed;
        String message;
        try {
            scenario.run(context);
            passed = true;
            message = "";
        } catch (HarnessFailure e) {
            passed = false;
            message = e.getMessage();
        } catch (Exception e) {
            passed = false;
            message = e.toString();
            e.printStackTrace();
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        System.out.printf("[harness] %s / %s %s (%.1fs) %s%n", environment.name(), scenario.name(),
                passed ? "PASSED" : "FAILED", duration.toMillis() / 1000.0, passed ? context.summary() : message);
        return new Result(environment.name(), scenario.name(), passed, duration, message, context.summary());
    }

    private static void printSummary(List<Result> results) {
        System.out.println();
        System.out.println("=== Harness summary ===");
        for (Result result : results) {
            System.out.printf("%-6s %-14s %-28s %6.1fs  %s%n",
                    result.passed() ? "PASS" : "FAIL",
                    result.environment(),
                    result.scenario(),
                    result.duration().toMillis() / 1000.0,
                    result.passed() ? (result.summary().isEmpty() ? "" : result.summary()) : result.message());
        }
        long failed = results.stream().filter(r -> !r.passed()).count();
        System.out.printf("%d run, %d passed, %d failed%n", results.size(), results.size() - failed, failed);
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            String key = args[i].substring(2);
            String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "";
            if (!value.isEmpty()) {
                options.put(key, value);
            }
        }
        return options;
    }

    private HarnessMain() {}
}
