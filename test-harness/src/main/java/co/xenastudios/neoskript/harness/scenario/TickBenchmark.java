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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's milliseconds per tick with {@value #PLAYERS} harness bots online and {@code tick-benchmark.sk}
 * running (a busy server's per-player work every tick), on Skript, on NeoSkript, and on the server with neither (what the players cost without scripts). After
 * {@value #WARMUP_SECONDS} seconds to warm up, the server's mean over its last 100 ticks is read
 * {@value #SAMPLES} times, 5 seconds apart; the result is the median.
 */
public final class TickBenchmark implements Scenario {

    static final int PLAYERS = 20;
    static final int WARMUP_SECONDS = 20;
    static final int SAMPLES = 8;
    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final Pattern MSPT = Pattern.compile("\\[bot] mspt ([\\d.]+) ([\\d.]+)$");

    @Override
    public String name() {
        return "tick-benchmark";
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
        double[] none = measure(context, runs.resolve("none"), null, null);
        double[] skript = measure(context, runs.resolve("skript"), context.skriptJar(), "Skript");
        double[] neoskript = measure(context, runs.resolve("neoskript"), context.neoskriptJar(), "NeoSkript");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("environment", context.environment().name());
        report.put("skript", "Skript " + context.referenceSkript().version());
        report.put("players", PLAYERS);
        report.put("samples", SAMPLES);
        report.put("noScriptsMspt", round(none[0]));
        report.put("skriptMspt", round(skript[0]));
        report.put("neoskriptMspt", round(neoskript[0]));
        report.put("skriptP95Mspt", round(skript[1]));
        report.put("neoskriptP95Mspt", round(neoskript[1]));
        // What the script costs per tick, over the server with no scripts.
        report.put("skriptScriptMspt", round(skript[0] - none[0]));
        report.put("neoskriptScriptMspt", round(neoskript[0] - none[0]));
        context.writeReport(name(), report);
        context.summary("none", round(none[0]) + " ms");
        context.summary("skript", round(skript[0]) + " ms");
        context.summary("neoskript", round(neoskript[0]) + " ms");
    }

    /** The median mean and 95th percentile milliseconds per tick, with {@code plugin} (or none) running. */
    private static double[] measure(ScenarioContext context, Path runDir, Path plugin, String folder) throws Exception {
        ServerBuilder builder = context.server(runDir).plugin(context.botJar());
        if (plugin != null) {
            builder.plugin(plugin).file("plugins/" + folder + "/scripts/tick-benchmark.sk",
                    context.script("tick-benchmark.sk"));
        }
        try (ServerProcess server = builder.start()) {
            if (plugin != null) {
                server.await(Pattern.compile(Pattern.quote("[harness] tick benchmark loaded") + "$"), WAIT);
            }
            server.await(ServerProcess.DONE, WAIT);
            if (plugin != null) {
                List<String> problems = server.linesMatching(SkriptLoadBenchmark.SCRIPT_PROBLEM);
                if (!problems.isEmpty()) {
                    throw new HarnessFailure(folder + " reported problems in the benchmark script: " + problems
                            + ". See " + server.logFile());
                }
            }
            server.command("difficulty peaceful");
            for (int i = 1; i <= PLAYERS; i++) {
                String name = String.format("Bench%02d", i);
                int mark = server.mark();
                server.command("bot join " + name);
                server.await(Pattern.compile(Pattern.quote("[bot] " + name + " joined") + "$"), mark, WAIT);
            }
            Thread.sleep(WARMUP_SECONDS * 1000L);
            List<Double> means = new ArrayList<>();
            List<Double> p95s = new ArrayList<>();
            for (int i = 0; i < SAMPLES; i++) {
                int mark = server.mark();
                server.command("bot mspt");
                Matcher sample = server.await(MSPT, mark, WAIT);
                means.add(Double.parseDouble(sample.group(1)));
                p95s.add(Double.parseDouble(sample.group(2)));
                Thread.sleep(5_000);
            }
            if (!server.linesMatching(Pattern.compile("too many$")).isEmpty()) {
                throw new HarnessFailure((folder == null ? "The server" : folder) + " got the benchmark wrong");
            }
            server.stop();
            return new double[] {EventBenchmark.median(means), EventBenchmark.median(p95s)};
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
