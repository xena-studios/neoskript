/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a trigger costs per event, on Skript and on NeoSkript, with the same script ({@code event-benchmark.sk}) on
 * the same server. A harness bot joins, then fires each of the script's events {@value #EVENTS} times in a row on
 * its thread, {@value #ROUNDS} rounds of each. The first {@value #WARMUP} rounds warm the JIT up; the median of
 * the others is the result, in microseconds per event.
 */
public final class EventBenchmark implements Scenario {

    static final int EVENTS = 20_000;
    static final int ROUNDS = 12;
    static final int WARMUP = 4;
    /** The script's events, by the bot's names for them, and what each trigger does. */
    static final Map<String, String> WORKLOADS = Map.of(
            "sneak", "global variables and text",
            "sprint", "a loop with arithmetic and a local list",
            "flight", "a condition only");
    private static final List<String> ORDER = List.of("flight", "sneak", "sprint");
    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final Pattern BENCH = Pattern.compile("\\[bot] bench (\\w+) (\\d+) (\\d+)ns$");

    @Override
    public String name() {
        return "event-benchmark";
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
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Map<String, Double> skript = measure(context, runs.resolve("skript"), context.skriptJar(), "Skript");
        Map<String, Double> neoskript = measure(context, runs.resolve("neoskript"), context.neoskriptJar(), "NeoSkript");

        Map<String, Object> events = new LinkedHashMap<>();
        for (String event : ORDER) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workload", WORKLOADS.get(event));
            result.put("skriptMicros", round(skript.get(event)));
            result.put("neoskriptMicros", round(neoskript.get(event)));
            result.put("speedup", round(skript.get(event) / neoskript.get(event)));
            events.put(event, result);
            context.summary(event, round(skript.get(event)) + " -> " + round(neoskript.get(event)) + " us");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("environment", context.environment().name());
        report.put("skript", "Skript " + context.referenceSkript().version());
        report.put("eventsPerRound", EVENTS);
        report.put("rounds", ROUNDS);
        report.put("warmupRounds", WARMUP);
        report.put("events", events);
        context.writeReport(name(), report);
    }

    /** Runs the benchmark on one plugin: the median microseconds per event, for each event. */
    private static Map<String, Double> measure(ScenarioContext context, Path runDir, Path plugin, String folder)
            throws Exception {
        try (ServerProcess server = context.server(runDir)
                .plugin(plugin)
                .plugin(context.botJar())
                .file("plugins/" + folder + "/scripts/event-benchmark.sk", context.script("event-benchmark.sk"))
                .start()) {
            server.await(Pattern.compile(Pattern.quote("[harness] event benchmark loaded") + "$"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            List<String> problems = server.linesMatching(SkriptLoadBenchmark.SCRIPT_PROBLEM);
            if (!problems.isEmpty()) {
                throw new HarnessFailure(folder + " reported problems in the benchmark script: " + problems + ". See "
                        + server.logFile());
            }
            int mark = server.mark();
            server.command("bot join Bench");
            server.await(Pattern.compile(Pattern.quote("[bot] Bench joined") + "$"), mark, WAIT);

            Map<String, List<Double>> micros = new LinkedHashMap<>();
            for (int round = 0; round < ROUNDS; round++) {
                for (String event : ORDER) {
                    mark = server.mark();
                    server.command("bot bench Bench " + event + " " + EVENTS);
                    Matcher done = server.await(BENCH, mark, WAIT);
                    if (!done.group(1).equals(event)) {
                        throw new HarnessFailure("Asked for " + event + ", the bot ran " + done.group(1));
                    }
                    if (round >= WARMUP) {
                        micros.computeIfAbsent(event, e -> new ArrayList<>())
                                .add(Long.parseLong(done.group(3)) / 1_000.0 / EVENTS);
                    }
                }
            }
            List<String> broadcasts = server.linesMatching(Pattern.compile("wrong (total|size)"));
            if (!broadcasts.isEmpty()) {
                throw new HarnessFailure(folder + " got the benchmark's sums wrong: " + broadcasts);
            }
            server.stop();
            Map<String, Double> medians = new LinkedHashMap<>();
            micros.forEach((event, values) -> medians.put(event, median(values)));
            return medians;
        }
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
