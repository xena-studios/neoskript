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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoSkript loads the real-world corpus and runs every script's self-test, as the reference Skript does
 * in {@code skript-real-world-corpus}.
 *
 * <p>The scripts in {@link #NEEDS_ITEMS} use items and inventories, which come in M4 step 4: their load
 * problems and failures are reported but don't fail the check yet. Every other script has to load
 * cleanly and pass.
 */
public final class NeoSkriptCorpusScenario implements Scenario {

    private static final Set<String> NEEDS_ITEMS = Set.of("kits", "warps");
    private static final Pattern RESULT = Pattern.compile("\\[corpus] ([\\w-]+): (.*)$");
    private static final Pattern DONE = Pattern.compile("\\[corpus] done$");
    private static final Pattern PROBLEM = Pattern.compile("\\[NeoSkript] Line \\d+: \\(corpus/([\\w-]+)\\.sk\\)");
    private static final Pattern ANY_PROBLEM = Pattern.compile("\\[NeoSkript] Line \\d+: \\((.+)\\)");
    private static final Pattern LOADED = Pattern.compile("\\[NeoSkript] Loaded \\d+ scripts?");
    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-real-world-corpus";
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
        List<Path> scripts = CorpusRunner.scripts(context.config().realWorldCorpus());
        List<String> suites = scripts.stream().map(CorpusRunner::suiteName).toList();
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/corpus-assert.sk", context.script("corpus-assert.sk"))
                .file("plugins/NeoSkript/scripts/corpus-runner.sk", CorpusRunner.generate(suites));
        for (Path script : scripts) {
            builder.file("plugins/NeoSkript/scripts/corpus/" + script.getFileName(), Files.readString(script));
        }

        try (ServerProcess server = builder.start()) {
            server.await(LOADED, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            Set<String> withProblems = new java.util.TreeSet<>();
            List<String> unexpectedProblems = new ArrayList<>();
            for (String line : server.linesMatching(ANY_PROBLEM)) {
                Matcher corpus = PROBLEM.matcher(line);
                if (corpus.find()) {
                    withProblems.add(corpus.group(1));
                    if (!NEEDS_ITEMS.contains(corpus.group(1))) {
                        unexpectedProblems.add(line);
                    }
                } else {
                    unexpectedProblems.add(line);
                }
            }

            int mark = server.mark();
            server.command(CorpusRunner.COMMAND);
            server.await(DONE, mark, WAIT);
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
            report.put("implementation", "NeoSkript");
            report.put("scripts", suites);
            report.put("passed", passed);
            report.put("failed", failures);
            report.put("missing", missing);
            report.put("loadProblems", withProblems);
            report.put("waitingForItems", NEEDS_ITEMS);
            context.writeReport(name(), report);
            context.summary("scripts", suites.size());
            context.summary("passed", passed.size());

            List<String> unexpected = new ArrayList<>(unexpectedProblems);
            failures.forEach((suite, lines) -> {
                if (!NEEDS_ITEMS.contains(suite)) {
                    unexpected.add(suite + ": " + lines);
                }
            });
            missing.stream().filter(suite -> !NEEDS_ITEMS.contains(suite)).forEach(suite -> unexpected.add(suite + ": no result"));
            if (!unexpected.isEmpty()) {
                throw new HarnessFailure("The corpus didn't load or pass on NeoSkript: " + unexpected + ". See "
                        + server.logFile());
            }
        }
    }
}
