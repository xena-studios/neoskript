/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.ExportedChecks;
import co.xenastudios.neoskript.frontend.registry.ExportedRegistry;
import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * neoskript.dev's parser reports what a server does: NeoSkript loads the differential's checks, the real-world
 * corpus, the parser page's examples and the AI skill's examples on a real server, which also writes its syntax
 * export, and the browser's registry built from that export parses the same scripts. Each script's messages,
 * line and text, must be the same, but for those {@code corpus/parser-differences.txt} lists. The server also
 * checks that the export reads item, block and entity names as it does, and every one must.
 */
public final class ParserParityScenario implements Scenario {

    private static final Pattern EXPORTED = Pattern.compile("Wrote the syntax export to ");
    private static final Pattern LOADED = Pattern.compile("Loaded \\d+ scripts? with");
    private static final Pattern NAMES = Pattern.compile(
            "Checked (\\d+) names against the syntax export: (\\d+) differ, (\\d+) it can't tell");
    private static final Pattern NAME_DIFFERENCE = Pattern.compile("Name check: (.+)$");
    private static final Pattern SKILL_BLOCK = Pattern.compile("```skript\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern MESSAGE = Pattern.compile("^(\\d+) \\w+: (.*)$");
    private static final Duration WAIT = Duration.ofMinutes(5);

    @Override
    public String name() {
        return "parser-parity";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The page's export is taken on the reference Skript's Paper; once is enough.
        return environment.skriptReference() && environment.platform() == Environment.Platform.PAPER
                && environment.minecraftVersion().equals("26.3");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Map<String, String> support = new LinkedHashMap<>();
        support.put("corpus-assert.sk", context.script("corpus-assert.sk"));
        support.put("differential-show.sk", context.script("differential-show.sk"));
        // Two loads, as the page's and the skill's examples define the commands and functions the corpus does.
        List<Map<String, String>> loads = List.of(units(context, false), units(context, true));
        Map<String, String> units = new LinkedHashMap<>();
        loads.forEach(units::putAll);

        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Path exportFile = runs.resolve("syntax-export.json").toAbsolutePath();
        Map<String, List<String>> server = new TreeMap<>();
        Matcher names = null;
        List<String> nameDifferences = new ArrayList<>();
        for (int i = 0; i < loads.size(); i++) {
            ServerBuilder builder = context.server(runs.resolve("load-" + (i + 1))).plugin(context.neoskriptJar());
            if (i == 0) {
                builder.jvmArg("-Dneoskript.syntaxExport=" + exportFile);
            }
            support.forEach((name, text) -> builder.file("plugins/NeoSkript/scripts/" + name, text));
            loads.get(i).forEach((unit, text) -> builder.file("plugins/NeoSkript/scripts/" + unit + ".sk", text));
            try (ServerProcess process = builder.start()) {
                if (i == 0) {
                    process.await(EXPORTED, WAIT);
                    names = process.await(NAMES, WAIT);
                }
                process.await(LOADED, WAIT);
                process.await(ServerProcess.DONE, WAIT);
                server.putAll(DifferentialScenario.messages(process.lines(0)));
                for (String line : process.linesMatching(NAME_DIFFERENCE)) {
                    Matcher difference = NAME_DIFFERENCE.matcher(line);
                    if (difference.find()) {
                        nameDifferences.add(difference.group(1));
                    }
                }
                process.stop();
            }
        }

        // The browser's side: the registry the page builds from this export, each load's scripts parsed together,
        // with the checks the page adds.
        SyntaxRegistry registry = ExportedRegistry.build(SyntaxExport.fromJson(Files.readString(exportFile)));
        Map<String, List<String>> browser = new TreeMap<>();
        long start = System.nanoTime();
        for (Map<String, String> load : loads) {
            List<SourceFile> files = new ArrayList<>();
            support.forEach((name, text) -> files.add(new SourceFile(name, text)));
            load.forEach((unit, text) -> files.add(new SourceFile(unit, text)));
            List<ParsedScript> scripts = new ArrayList<>(new ScriptLoader(registry).load(files));
            // In the order the server sets them up, which matters for errors only the first asker gets.
            scripts.sort(java.util.Comparator.comparing(script -> script.file().name()));
            List<Diagnostic> diagnostics = new ArrayList<>();
            scripts.forEach(script -> diagnostics.addAll(script.diagnostics()));
            diagnostics.addAll(ExportedChecks.check(registry, scripts));
            {
                for (Diagnostic diagnostic : diagnostics) {
                    browser.computeIfAbsent(diagnostic.file(), f -> new ArrayList<>())
                            .add(diagnostic.line() + ": " + diagnostic.message());
                }
            }
        }
        long parseMillis = (System.nanoTime() - start) / 1_000_000;

        Map<String, String> known = known(context);
        Map<String, Object> differences = new TreeMap<>();
        int messages = 0;
        for (String unit : units.keySet()) {
            List<String> onServer = server.getOrDefault(unit, List.of()).stream().map(ParserParityScenario::bare)
                    .sorted().toList();
            List<String> inBrowser = browser.getOrDefault(unit, List.of()).stream().sorted().toList();
            messages += onServer.size();
            if (!DifferentialScenario.eventListsSorted(onServer)
                    .equals(DifferentialScenario.eventListsSorted(inBrowser))) {
                Map<String, Object> difference = new LinkedHashMap<>();
                difference.put("server", onServer);
                difference.put("browser", inBrowser);
                differences.put(unit, difference);
            }
        }
        List<String> unexpected = differences.keySet().stream().filter(unit -> !known.containsKey(unit)).toList();
        List<String> fixed = known.keySet().stream()
                .filter(unit -> units.containsKey(unit) && !differences.containsKey(unit)).toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("units", units.size());
        report.put("serverMessages", messages);
        report.put("unexpected", unexpected);
        report.put("knownAndNowIdentical", fixed);
        if (names == null) {
            throw new HarnessFailure("NeoSkript didn't check the names in its syntax export");
        }
        report.put("namesChecked", Integer.parseInt(names.group(1)));
        report.put("namesDiffering", Integer.parseInt(names.group(2)));
        report.put("namesUnknown", Integer.parseInt(names.group(3)));
        report.put("nameDifferences", nameDifferences);
        report.put("browserParseMillis", parseMillis);
        report.put("differences", differences);
        Path reportFile = context.writeReport(name(), report);
        context.summary("units", units.size());
        context.summary("differ", differences.size());
        context.summary("unexpected", unexpected.size());
        context.summary("names", names.group(1) + " checked, " + names.group(2) + " differ");

        if (!unexpected.isEmpty() || !names.group(2).equals("0")) {
            throw new HarnessFailure(unexpected.size() + " scripts get other messages in the browser than on the server "
                    + unexpected + ", and " + names.group(2) + " names are read otherwise. See " + reportFile);
        }
    }

    /** {@code 4 INFO: text}, from the server, as {@code 4: text}. */
    private static String bare(String message) {
        Matcher matcher = MESSAGE.matcher(message);
        return matcher.matches() ? matcher.group(1) + ": " + matcher.group(2) : message;
    }

    /**
     * The scripts to compare, by unit: {@code checks/messages} and {@code real-world/kits}, or with {@code examples},
     * the page's and the skill's examples ({@code examples/parser-tpa}, {@code examples/skill-1}).
     */
    private static Map<String, String> units(ScenarioContext context, boolean examples) throws Exception {
        Map<String, String> units = new LinkedHashMap<>();
        Path corpus = context.config().realWorldCorpus().getParent();
        if (examples) {
            Path page = corpus.getParent().getParent().resolve("website/src/parser/examples");
            for (Path example : CorpusRunner.scripts(page)) {
                units.put("examples/parser-" + CorpusRunner.suiteName(example), Files.readString(example));
            }
            String skill = Files.readString(context.config().skillDir().resolve("SKILL.md"));
            Matcher block = SKILL_BLOCK.matcher(skill);
            int n = 0;
            while (block.find()) {
                units.put("examples/skill-" + ++n, block.group(1) + "\n");
            }
            return units;
        }
        for (Path dir : List.of(corpus.resolve("differential/checks"), corpus.resolve("parser"))) {
            for (Path check : CorpusRunner.scripts(dir)) {
                units.put("checks/" + CorpusRunner.suiteName(check), Files.readString(check));
            }
        }
        for (Path script : CorpusRunner.scripts(context.config().realWorldCorpus())) {
            units.put("real-world/" + CorpusRunner.suiteName(script), Files.readString(script));
        }
        return units;
    }

    /** {@code corpus/parser-differences.txt}: units whose messages differ, with why. */
    private static Map<String, String> known(ScenarioContext context) throws Exception {
        Map<String, String> known = new LinkedHashMap<>();
        Path file = context.config().realWorldCorpus().resolveSibling("parser-differences.txt");
        if (!Files.exists(file)) {
            return known;
        }
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int hash = trimmed.indexOf('#');
            known.put((hash < 0 ? trimmed : trimmed.substring(0, hash)).strip(),
                    hash < 0 ? "" : trimmed.substring(hash + 1).strip());
        }
        return known;
    }
}
