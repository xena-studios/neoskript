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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Measures how long the reference Skript takes to load and reload the generated benchmark scripts.
 * NeoSkript runs the same measurement once it can load scripts.
 */
public final class SkriptLoadBenchmark implements Scenario {

    static final int RELOADS = 5;

    private static final Pattern LOADED =
            Pattern.compile("Loaded (\\d+) scripts? with a total of (\\d+) structures? in (.+)$");
    private static final Pattern RELOAD_DONE = Pattern.compile("(Successfully reloaded|Encountered (\\d+) errors?).*\\((\\d+)ms\\)");
    private static final Pattern NO_ERRORS = Pattern.compile("\\[Skript] All scripts loaded without errors\\.");
    /** Skript reports script errors and warnings as "Line 12: (file.sk)". */
    private static final Pattern SCRIPT_PROBLEM = Pattern.compile("Line \\d+: \\(");

    @Override
    public String name() {
        return "skript-load-benchmark";
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
        Map<String, String> scripts = BenchmarkScripts.generate();
        ServerBuilder builder = context.server().plugin(context.skriptJar());
        scripts.forEach((path, content) -> builder.file("plugins/Skript/scripts/" + path, content));
        int expectedStructures = BenchmarkScripts.FILES * BenchmarkScripts.structuresPerFile();

        try (ServerProcess server = builder.start()) {
            Matcher loaded = server.await(LOADED, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int loadedScripts = Integer.parseInt(loaded.group(1));
            int loadedStructures = Integer.parseInt(loaded.group(2));
            String startupLoad = loaded.group(3).trim();
            List<String> problems = server.linesMatching(SCRIPT_PROBLEM);
            if (!problems.isEmpty() || server.linesMatching(NO_ERRORS).isEmpty()) {
                throw new HarnessFailure("Skript reported problems in the benchmark scripts: " + problems
                        + ". See " + server.logFile());
            }
            if (loadedScripts != scripts.size() || loadedStructures != expectedStructures) {
                throw new HarnessFailure("Expected " + scripts.size() + " scripts with " + expectedStructures
                        + " structures, Skript loaded " + loadedScripts + " with " + loadedStructures);
            }

            List<Long> reportedMs = new ArrayList<>();
            List<Long> wallMs = new ArrayList<>();
            for (int i = 0; i < RELOADS; i++) {
                int mark = server.mark();
                long start = System.nanoTime();
                server.command("skript reload scripts");
                Matcher done = server.await(RELOAD_DONE, mark, Duration.ofMinutes(5));
                wallMs.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
                if (done.group(2) != null) {
                    throw new HarnessFailure("Reload " + (i + 1) + " reported " + done.group(2) + " errors");
                }
                reportedMs.add(Long.parseLong(done.group(3)));
            }
            server.stop();

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("implementation", "Skript " + context.referenceSkript().version());
            report.put("environment", context.environment().name());
            report.put("scripts", loadedScripts);
            report.put("structures", loadedStructures);
            report.put("lines", scripts.values().stream().mapToLong(s -> s.lines().count()).sum());
            report.put("startupLoad", startupLoad);
            report.put("reloadReportedMs", reportedMs);
            report.put("reloadWallMs", wallMs);
            report.put("reloadMedianMs", median(reportedMs));
            context.writeReport("skript-load-benchmark", report);
            context.summary("startupLoad", startupLoad);
            context.summary("reloadMedianMs", median(reportedMs));
        }
    }

    static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
