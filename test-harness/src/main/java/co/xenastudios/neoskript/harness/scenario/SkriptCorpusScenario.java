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
 * The reference Skript loads the real-world corpus without errors or warnings, and every script's
 * self-test passes. These results are what NeoSkript gets compared against.
 */
public final class SkriptCorpusScenario implements Scenario {

    private static final Pattern RESULT = Pattern.compile("\\[corpus] ([\\w-]+): (.*)$");
    private static final Pattern DONE = Pattern.compile("\\[corpus] done$");

    @Override
    public String name() {
        return "skript-real-world-corpus";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        List<Path> scripts = CorpusRunner.scripts(context.config().realWorldCorpus());
        List<String> suites = scripts.stream().map(CorpusRunner::suiteName).toList();
        ServerBuilder builder = context.server()
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/corpus-assert.sk", context.script("corpus-assert.sk"))
                .file("plugins/Skript/scripts/corpus-runner.sk", CorpusRunner.generate(suites));
        for (Path script : scripts) {
            builder.file("plugins/Skript/scripts/corpus/" + script.getFileName(), Files.readString(script));
        }

        try (ServerProcess server = builder.start()) {
            Matcher loaded = server.await(SkriptLoadBenchmark.LOADED, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            List<String> problems = server.linesMatching(SkriptLoadBenchmark.SCRIPT_PROBLEM);
            if (!problems.isEmpty() || server.linesMatching(SkriptLoadBenchmark.NO_ERRORS).isEmpty()) {
                throw new HarnessFailure("Skript reported problems in the corpus: " + problems
                        + ". See " + server.logFile());
            }
            int expectedScripts = scripts.size() + 2;
            if (Integer.parseInt(loaded.group(1)) != expectedScripts) {
                throw new HarnessFailure("Expected " + expectedScripts + " scripts, Skript loaded " + loaded.group(1));
            }

            int mark = server.mark();
            server.command(CorpusRunner.COMMAND);
            server.await(DONE, mark, Duration.ofMinutes(1));
            Map<String, List<String>> failures = new LinkedHashMap<>();
            List<String> passed = new ArrayList<>();
            for (String line : server.linesMatching(RESULT)) {
                Matcher result = RESULT.matcher(line);
                if (!result.find() || result.group(1).equals("done")) {
                    continue;
                }
                if (result.group(2).equals("ok")) {
                    passed.add(result.group(1));
                } else {
                    failures.computeIfAbsent(result.group(1), suite -> new ArrayList<>()).add(result.group(2));
                }
            }
            server.stop();

            List<String> missing = suites.stream()
                    .filter(suite -> !passed.contains(suite) && !failures.containsKey(suite))
                    .toList();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("implementation", "Skript " + context.referenceSkript().version());
            report.put("scripts", suites);
            report.put("lines", countLines(scripts));
            report.put("passed", passed);
            report.put("failed", failures);
            report.put("missing", missing);
            context.writeReport(name(), report);
            context.summary("scripts", suites.size());
            context.summary("passed", passed.size());
            if (!failures.isEmpty() || !missing.isEmpty()) {
                throw new HarnessFailure("Corpus self-tests failed: " + failures
                        + (missing.isEmpty() ? "" : ", no result for " + missing) + ". See " + server.logFile());
            }
        }
    }

    private static long countLines(List<Path> scripts) throws java.io.IOException {
        long lines = 0;
        for (Path script : scripts) {
            lines += Files.readAllLines(script).size();
        }
        return lines;
    }
}
