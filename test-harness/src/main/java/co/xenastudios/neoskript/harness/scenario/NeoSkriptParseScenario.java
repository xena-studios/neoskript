/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.CorpusCheck;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The M1 exit check: NeoSkript's frontend parses the whole corpus with zero false errors, against the
 * stub registry built from the reference Skript's syntax. No server is needed. Also times parsing, with
 * the generated benchmark scripts as the comparison with Skript's load time.
 */
public final class NeoSkriptParseScenario implements Scenario {

    static final int WARMUPS = 3;
    static final int RUNS = 5;

    @Override
    public String name() {
        return "neoskript-parse-corpus";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // Parsing doesn't depend on the server; run it once, next to the Skript baseline.
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        ScriptLoader loader = new ScriptLoader(SkriptRegistry.get());
        List<SourceFile> realWorld = new ArrayList<>(read(context.config().realWorldCorpus()));
        realWorld.add(new SourceFile("corpus-assert.sk", context.script("corpus-assert.sk")));
        List<SourceFile> benchmark = new ArrayList<>();
        BenchmarkScripts.generate().forEach((name, text) -> benchmark.add(new SourceFile(name, text)));
        Map<String, List<SourceFile>> sets = new LinkedHashMap<>();
        sets.put("skript-tests", read(context.config().skriptTests()));
        sets.put("real-world", realWorld);
        sets.put("benchmark", benchmark);

        Map<String, Object> report = new LinkedHashMap<>();
        int falseErrors = 0;
        List<String> examples = new ArrayList<>();
        for (Map.Entry<String, List<SourceFile>> set : sets.entrySet()) {
            List<SourceFile> files = set.getValue();
            for (int i = 0; i < WARMUPS; i++) {
                loader.load(files);
            }
            List<Long> times = new ArrayList<>();
            List<ParsedScript> scripts = null;
            for (int i = 0; i < RUNS; i++) {
                long start = System.nanoTime();
                scripts = loader.load(files);
                times.add((System.nanoTime() - start) / 1_000_000);
            }
            CorpusCheck.Result result = CorpusCheck.check(scripts);
            falseErrors += result.totalFalseErrors();
            result.falseErrors().stream().limit(20).forEach(d -> examples.add(d.toString()));
            examples.addAll(result.falselyRejected().stream().limit(20).toList());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("files", result.files());
            entry.put("lines", result.lines());
            entry.put("falseErrors", result.totalFalseErrors());
            entry.put("parseMedianMs", SkriptLoadBenchmark.median(times));
            entry.put("parseMs", times);
            entry.put("parseBlocksExpectingErrors", result.expectedErrors());
            entry.put("parseBlocksErrorsCaught", result.caughtErrors());
            entry.put("parseBlocksExpectingNoErrors", result.expectedClean());
            entry.put("missedErrors", result.missed());
            report.put(set.getKey(), entry);
            context.summary(set.getKey() + "Ms", SkriptLoadBenchmark.median(times));
        }
        report.put("falseErrorExamples", examples);
        context.writeReport(name(), report);
        context.summary("falseErrors", falseErrors);
        if (falseErrors > 0) {
            throw new HarnessFailure(falseErrors + " false errors, e.g. " + examples.stream().limit(5).toList());
        }
    }

    private static List<SourceFile> read(Path dir) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                files.add(new SourceFile(dir.relativize(path).toString().replace('\\', '/'), Files.readString(path)));
            }
        }
        return files;
    }
}
