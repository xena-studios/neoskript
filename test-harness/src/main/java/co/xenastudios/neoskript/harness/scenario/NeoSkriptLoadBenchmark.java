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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The same measurement as {@link SkriptLoadBenchmark}, on NeoSkript: the generated benchmark scripts loaded
 * at startup, then reloaded {@value SkriptLoadBenchmark#RELOADS} times with {@code /sk reload scripts}. It runs
 * where the reference Skript runs, so the two reports compare the same scripts on the same server.
 */
public final class NeoSkriptLoadBenchmark implements Scenario {

    private static final Pattern LOADED =
            Pattern.compile("Loaded (\\d+) scripts? with (\\d+) triggers? in (\\d+) ms(?: \\((\\d+) errors?\\))?$");
    private static final Pattern RELOAD_DONE =
            Pattern.compile("(Successfully reloaded all scripts|Encountered (\\d+) errors?).*\\((\\d+)ms\\)");

    @Override
    public String name() {
        return "neoskript-load-benchmark";
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
        ServerBuilder builder = context.server().plugin(context.neoskriptJar());
        scripts.forEach((path, content) -> builder.file("plugins/NeoSkript/scripts/" + path, content));

        try (ServerProcess server = builder.start()) {
            Matcher loaded = server.await(LOADED, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int loadedScripts = Integer.parseInt(loaded.group(1));
            if (loaded.group(4) != null) {
                throw new HarnessFailure("NeoSkript reported " + loaded.group(4) + " errors in the benchmark scripts. See "
                        + server.logFile());
            }
            if (loadedScripts != scripts.size()) {
                throw new HarnessFailure("Expected " + scripts.size() + " scripts, NeoSkript loaded " + loadedScripts);
            }

            List<Long> reportedMs = new ArrayList<>();
            List<Long> wallMs = new ArrayList<>();
            for (int i = 0; i < SkriptLoadBenchmark.RELOADS; i++) {
                int mark = server.mark();
                long start = System.nanoTime();
                server.command("sk reload scripts");
                Matcher done = server.await(RELOAD_DONE, mark, Duration.ofMinutes(5));
                wallMs.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
                if (done.group(2) != null) {
                    throw new HarnessFailure("Reload " + (i + 1) + " reported " + done.group(2) + " errors");
                }
                reportedMs.add(Long.parseLong(done.group(3)));
            }
            server.stop();

            long median = SkriptLoadBenchmark.median(reportedMs);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("implementation", "NeoSkript");
            report.put("environment", context.environment().name());
            report.put("scripts", loadedScripts);
            report.put("triggers", Integer.parseInt(loaded.group(2)));
            report.put("lines", scripts.values().stream().mapToLong(s -> s.lines().count()).sum());
            report.put("startupLoadMs", Long.parseLong(loaded.group(3)));
            report.put("reloadReportedMs", reportedMs);
            report.put("reloadWallMs", wallMs);
            report.put("reloadMedianMs", median);
            context.writeReport("neoskript-load-benchmark", report);
            context.summary("startupLoadMs", Long.parseLong(loaded.group(3)));
            context.summary("reloadMedianMs", median);
        }
    }
}
