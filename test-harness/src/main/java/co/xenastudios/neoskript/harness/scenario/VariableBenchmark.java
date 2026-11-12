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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How long loading saved variables takes when the server starts, on Skript and on NeoSkript, from the same
 * {@code variables.csv}. Skript makes {@value #VARIABLES} variables with {@code variables-benchmark.sk} and saves
 * them; then each loads that file {@value #LOADS} times, and the result is the median of what each logs.
 */
public final class VariableBenchmark implements Scenario {

    static final int VARIABLES = 140_000;
    static final int LOADS = 3;
    private static final Duration WAIT = Duration.ofMinutes(5);
    private static final Pattern SKRIPT_LOADED = Pattern.compile("\\[Skript] Loaded (\\d+) variables? in ([\\d.]+) seconds");
    private static final Pattern NEOSKRIPT_LOADED = Pattern.compile("\\[NeoSkript] Loaded (\\d+) variables? in (\\d+) ms");

    @Override
    public String name() {
        return "variable-benchmark";
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
        String csv = make(context, runs.resolve("make"));

        List<Double> skript = new ArrayList<>();
        List<Double> neoskript = new ArrayList<>();
        for (int i = 0; i < LOADS; i++) {
            skript.add(load(context, runs.resolve("skript-" + i), context.skriptJar(), "Skript", csv, SKRIPT_LOADED, 1000));
            neoskript.add(load(context, runs.resolve("neoskript-" + i), context.neoskriptJar(), "NeoSkript", csv,
                    NEOSKRIPT_LOADED, 1));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("environment", context.environment().name());
        report.put("skript", "Skript " + context.referenceSkript().version());
        report.put("variables", VARIABLES);
        report.put("fileBytes", csv.length());
        report.put("skriptLoadMs", skript);
        report.put("neoskriptLoadMs", neoskript);
        report.put("skriptMedianMs", EventBenchmark.median(skript));
        report.put("neoskriptMedianMs", EventBenchmark.median(neoskript));
        context.writeReport(name(), report);
        context.summary("skript", EventBenchmark.median(skript) + " ms");
        context.summary("neoskript", EventBenchmark.median(neoskript) + " ms");
    }

    /** Has Skript make and save the variables; its variables.csv. */
    private static String make(ScenarioContext context, Path runDir) throws Exception {
        try (ServerProcess server = context.server(runDir)
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/variables-benchmark.sk", context.script("variables-benchmark.sk"))
                .start()) {
            server.await(Pattern.compile(Pattern.quote("[harness] variables made") + "$"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            server.stop();
        }
        Path file = runDir.resolve("plugins/Skript/variables.csv");
        if (!Files.exists(file)) {
            throw new HarnessFailure("Skript didn't save " + file);
        }
        return Files.readString(file);
    }

    /** Starts a server with {@code csv} as the plugin's saved variables; the milliseconds it logs loading them. */
    private static double load(ScenarioContext context, Path runDir, Path plugin, String folder, String csv,
            Pattern loaded, double toMillis) throws Exception {
        try (ServerProcess server = context.server(runDir)
                .plugin(plugin)
                .file("plugins/" + folder + "/variables.csv", csv)
                .start()) {
            Matcher done = server.await(loaded, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            server.stop();
            if (Integer.parseInt(done.group(1)) != VARIABLES) {
                throw new HarnessFailure(folder + " loaded " + done.group(1) + " variables, not " + VARIABLES);
            }
            return Double.parseDouble(done.group(2)) * toMillis;
        }
    }
}
