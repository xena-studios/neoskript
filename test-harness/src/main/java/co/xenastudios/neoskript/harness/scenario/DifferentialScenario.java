/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessConfig;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M4's exit check: the same scripts on the reference Skript and on NeoSkript, compared. Each script is a
 * unit, and a unit behaves identically when every line gets the same messages when it loads and, for
 * scripts that run, the console shows the same output. The units are Skript's documentation examples
 * and the checks in {@code corpus/differential/checks} (load only), the probes in {@code corpus/differential} (each runs its {@code probe_<name>()}) and the
 * real-world corpus (each runs its self-test). Variables left at the end are compared too.
 *
 * <p>Some of Skript's results vary between runs, so it's run {@code -Pharness.differential.skriptRuns} times (3 by
 * default), and a unit is identical when NeoSkript gives what Skript gave in any of them. The report lists the units
 * that vary in Skript.
 *
 * <p>Skript doesn't run on Folia, so Folia is compared with Skript on Paper of the same Minecraft
 * version. Skript's results are cached by their inputs in {@code .harness-cache/differential}.
 *
 * <p>Fails when under {@value #REQUIRED}% of units match, or when a unit differs that
 * {@code corpus/differential/known-differences.txt} doesn't list (with {@code folia:} before it, only on Folia).
 */
public final class DifferentialScenario implements Scenario {

    static final int REQUIRED = 95;
    /** Bump when the way results are gathered changes, so cached results of the reference Skript go stale. */
    private static final int VERSION = 1;

    private static final Pattern LOADED = Pattern.compile("Loaded \\d+ scripts? with");
    private static final Pattern PREFIX = Pattern.compile("^\\[\\d\\d:\\d\\d:\\d\\d (\\w+)]: ");
    private static final Pattern HEADER = Pattern.compile("^(?:\\[(?:Neo)?Skript] )?Line (\\d+): \\((.+)\\)$");
    private static final Pattern UNIT = Pattern.compile("(?:^|/)((?:examples|checks|probes|real-world)/[^/]+)\\.sk$");
    private static final Pattern DONE = Pattern.compile("\\[diff] end ([\\w./-]+)$");
    private static final Pattern UNKNOWN = Pattern.compile("Unknown or incomplete command|Unknown command|Error in diff-runner/");
    private static final Pattern STACK = Pattern.compile("^(\\s+at |\\s*\\.\\.\\. \\d+ more|Caused by: |[\\w.$]+(Exception|Error)(: |$))");
    private static final Pattern EVENT_LIST = Pattern.compile("(.* can only be used in )(the .+ event)");
    private static final Duration LOAD_WAIT = Duration.ofMinutes(10);
    private static final Duration RUN_WAIT = Duration.ofSeconds(20);

    private static volatile List<Environment> environments;

    /** One implementation's results: each unit's load messages and output, and the variables left at the end. */
    record Results(Map<String, List<String>> messages, Map<String, List<String>> output, List<String> variables) {}

    private record Implementation(String name, String folder, Path jar) {}

    private record Inputs(Map<String, String> loadOnly, Map<String, String> running, List<String> runnable) {}

    @Override
    public String name() {
        return "differential";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return reference(environment).isPresent();
    }

    /** The environment to run the reference Skript on: this one, or Paper of the same version for Folia. */
    private static Optional<Environment> reference(Environment environment) {
        if (environment.skriptReference()) {
            return Optional.of(environment);
        }
        try {
            if (environments == null) {
                environments = Environment.loadAll(HarnessConfig.fromSystemProperties().environmentsDir());
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return environments.stream()
                .filter(e -> e.skriptReference() && e.minecraftVersion().equals(environment.minecraftVersion()))
                .findFirst();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Inputs inputs = inputs(context);
        Environment referenceEnv = reference(context.environment()).orElseThrow();
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());

        String key = cacheKey(context, referenceEnv, inputs);
        Gson gson = new Gson();
        // Some of Skript's results vary between runs (it iterates hash sets and maps), so it's run several times,
        // each run cached, and NeoSkript matches a unit if it gives what Skript gave in any of them.
        List<Results> skriptRuns = new ArrayList<>();
        for (int run = 1; run <= skriptRuns(); run++) {
            Path cached = context.config().cacheDir().resolve("differential")
                    .resolve(key + (run == 1 ? "" : ".run" + run) + ".json");
            if (Files.exists(cached)) {
                skriptRuns.add(gson.fromJson(Files.readString(cached), Results.class));
                continue;
            }
            Implementation implementation = new Implementation("Skript", "Skript", context.skriptJar());
            Results skript = gather(context, referenceEnv, implementation, inputs, runs.resolve("skript"));
            Files.createDirectories(cached.getParent());
            Files.writeString(cached, gson.toJson(skript));
            skriptRuns.add(skript);
        }
        Implementation neoskript = new Implementation("NeoSkript", "NeoSkript", context.neoskriptJar());
        Results neo = gather(context, context.environment(), neoskript, inputs, runs.resolve("neoskript"));

        List<String> units = new ArrayList<>(inputs.loadOnly().keySet());
        units.addAll(inputs.running().keySet());
        Map<String, Object> differences = new TreeMap<>();
        List<String> varying = new ArrayList<>();
        for (String unit : units) {
            // The difference from Skript's first run, unless NeoSkript gives what Skript gave in another.
            Map<String, Object> difference = null;
            for (Results skript : skriptRuns) {
                Map<String, Object> found = difference(skript.messages().getOrDefault(unit, List.of()),
                        neo.messages().getOrDefault(unit, List.of()), skript.output().get(unit), neo.output().get(unit));
                if (difference == null || found.isEmpty()) {
                    difference = found;
                }
                if (found.isEmpty()) {
                    break;
                }
            }
            if (variesBetween(skriptRuns, unit)) {
                varying.add(unit);
            }
            if (difference != null && !difference.isEmpty()) {
                differences.put(unit, difference);
            }
        }
        boolean variablesMatch = skriptRuns.stream().anyMatch(skript -> skript.variables().equals(neo.variables()));
        int total = units.size() + 1;
        int identical = total - differences.size() - (variablesMatch ? 0 : 1);
        double percent = 100.0 * identical / total;

        Map<String, String> known = knownDifferences(context);
        List<String> unexpected = differences.keySet().stream().filter(unit -> !known.containsKey(unit)).toList();
        List<String> fixed = known.keySet().stream()
                .filter(unit -> units.contains(unit) && !differences.containsKey(unit))
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reference", "Skript " + context.referenceSkript().version() + " on " + referenceEnv.name());
        report.put("units", total);
        report.put("identical", identical);
        report.put("percent", Math.floor(percent * 10) / 10);
        report.put("unexpected", unexpected);
        report.put("knownAndNowIdentical", fixed);
        report.put("skriptRuns", skriptRuns.size());
        report.put("varyInSkript", varying);
        if (!variablesMatch) {
            report.put("variables", Map.of("skript", skriptRuns.getFirst().variables(), "neoskript", neo.variables()));
        }
        report.put("differences", differences);
        Path reportFile = context.writeReport(name(), report);
        context.summary("units", total);
        context.summary("identical", identical);
        context.summary("percent", Math.floor(percent * 10) / 10);
        context.summary("unexpected", unexpected.size());
        context.summary("varyInSkript", varying.size());

        if (percent < REQUIRED || !unexpected.isEmpty() || !variablesMatch) {
            throw new HarnessFailure(String.format(
                    "%.1f%% of %d units behave identically (%d%% needed); %d unexpected differences%s: %s. See %s",
                    percent, total, REQUIRED, unexpected.size(), variablesMatch ? "" : ", and the variables differ",
                    unexpected.stream().limit(20).toList(), reportFile));
        }
    }

    /** How many times to run the reference Skript: {@code -Pharness.differential.skriptRuns=N}, 3 by default. */
    private static int skriptRuns() {
        return Math.max(1, Integer.parseInt(System.getProperty("harness.differential.skriptRuns", "3").strip()));
    }

    /** Whether Skript's messages or output for the unit differ between its runs. */
    private static boolean variesBetween(List<Results> runs, String unit) {
        for (Results run : runs.subList(1, runs.size())) {
            if (!eventListsSorted(run.messages().getOrDefault(unit, List.of()))
                            .equals(eventListsSorted(runs.getFirst().messages().getOrDefault(unit, List.of())))
                    || !java.util.Objects.equals(run.output().get(unit), runs.getFirst().output().get(unit))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> difference(
            List<String> skriptMessages, List<String> neoMessages, List<String> skriptOutput, List<String> neoOutput) {
        Map<String, Object> difference = new LinkedHashMap<>();
        if (!eventListsSorted(skriptMessages).equals(eventListsSorted(neoMessages))) {
            difference.put("skriptMessages", skriptMessages);
            difference.put("neoskriptMessages", neoMessages);
        }
        if (skriptOutput != null && !skriptOutput.equals(neoOutput)) {
            difference.put("skriptOutput", skriptOutput);
            difference.put("neoskriptOutput", neoOutput);
        }
        return difference;
    }

    private static Inputs inputs(ScenarioContext context) throws IOException {
        Map<String, String> loadOnly = new LinkedHashMap<>();
        for (DocExamples.Example example : DocExamples.load(context.config().skriptCorpus().resolve("src/main/java"))) {
            loadOnly.put("examples/" + example.name(), example.script());
        }
        for (Path check : CorpusRunner.scripts(probesDir(context).resolve("checks"))) {
            loadOnly.put("checks/" + CorpusRunner.suiteName(check), Files.readString(check));
        }
        Map<String, String> running = new LinkedHashMap<>();
        List<String> runnable = new ArrayList<>();
        for (Path probe : CorpusRunner.scripts(probesDir(context))) {
            String unit = "probes/" + CorpusRunner.suiteName(probe);
            running.put(unit, Files.readString(probe));
            runnable.add(unit);
        }
        for (Path script : CorpusRunner.scripts(context.config().realWorldCorpus())) {
            String unit = "real-world/" + CorpusRunner.suiteName(script);
            running.put(unit, Files.readString(script));
            runnable.add(unit);
        }
        return new Inputs(loadOnly, running, runnable);
    }

    private static Path probesDir(ScenarioContext context) {
        return context.config().realWorldCorpus().resolveSibling("differential");
    }

    private static Map<String, String> knownDifferences(ScenarioContext context) throws IOException {
        Map<String, String> known = new LinkedHashMap<>();
        Path file = probesDir(context).resolve("known-differences.txt");
        if (!Files.exists(file)) {
            return known;
        }
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // 'folia: <unit>' only differs on Folia.
            if (trimmed.startsWith("folia:")) {
                if (context.environment().platform() != Environment.Platform.FOLIA) {
                    continue;
                }
                trimmed = trimmed.substring("folia:".length()).strip();
            }
            int hash = trimmed.indexOf('#');
            String unit = (hash < 0 ? trimmed : trimmed.substring(0, hash)).strip();
            known.put(unit, hash < 0 ? "" : trimmed.substring(hash + 1).strip());
        }
        return known;
    }

    /** Boots the implementation twice: once with the examples, which only load, then with the scripts that run. */
    private static Results gather(
            ScenarioContext context, Environment environment, Implementation implementation, Inputs inputs, Path runDir)
            throws Exception {
        String scripts = "plugins/" + implementation.folder() + "/scripts/";
        Map<String, List<String>> messages = new TreeMap<>();

        ServerBuilder loading = context.server(environment, runDir.resolve("examples"))
                .plugin(implementation.jar())
                .file(scripts + "differential-show.sk", context.script("differential-show.sk"));
        inputs.loadOnly().forEach((unit, script) -> loading.file(scripts + unit + ".sk", script));
        try (ServerProcess server = loading.start()) {
            server.await(LOADED, LOAD_WAIT);
            server.await(ServerProcess.DONE, LOAD_WAIT);
            messages.putAll(messages(server.lines(0)));
            server.stop();
        }

        ServerBuilder running = context.server(environment, runDir.resolve("run"))
                .plugin(implementation.jar())
                .file(scripts + "corpus-assert.sk", context.script("corpus-assert.sk"))
                .file(scripts + "differential-show.sk", context.script("differential-show.sk"));
        inputs.running().forEach((unit, script) -> running.file(scripts + unit + ".sk", script));
        for (String unit : inputs.runnable()) {
            running.file(scripts + "diff-runner/" + id(unit) + ".sk", runner(unit));
        }
        Map<String, List<String>> output = new TreeMap<>();
        List<String> variables;
        try (ServerProcess server = running.start()) {
            server.await(LOADED, LOAD_WAIT);
            server.await(ServerProcess.DONE, LOAD_WAIT);
            messages.putAll(messages(server.lines(0)));
            for (String unit : inputs.runnable()) {
                int mark = server.mark();
                server.command("diff-" + id(unit));
                server.await(Pattern.compile(DONE.pattern() + "|" + UNKNOWN.pattern()), mark, RUN_WAIT);
                output.put(unit, output(server.lines(mark)));
            }
            server.stop();
            variables = variables(server.logFile().resolveSibling("plugins/" + implementation.folder() + "/variables.csv"));
        }
        return new Results(messages, output, variables);
    }

    private static String id(String unit) {
        return unit.replace('/', '-');
    }

    /** A command that runs one unit between markers. */
    private static String runner(String unit) {
        String suite = unit.substring(unit.indexOf('/') + 1);
        boolean probe = unit.startsWith("probes/");
        String function = probe ? "probe_" + suite.replace('-', '_') : CorpusRunner.selftestFunction(suite);
        StringBuilder script = new StringBuilder()
                .append("# Generated by the NeoSkript test harness. Do not edit.\n")
                .append("command /diff-").append(id(unit)).append(":\n")
                .append("\texecutable by: console\n")
                .append("\ttrigger:\n")
                .append("\t\tsend \"[diff] begin ").append(unit).append("\" to console\n")
                .append("\t\t").append(function).append("()\n");
        if (!probe) {
            script.append("\t\tcorpus_report(\"").append(suite).append("\")\n");
        }
        return script.append("\t\tsend \"[diff] end ").append(unit).append("\" to console\n").toString();
    }

    /**
     * The messages with the events in each "can only be used in the on X event, the on Y event or ..." sorted:
     * Skript orders its events by their hash codes after their complexity, so the order changes from run to run.
     */
    static List<String> eventListsSorted(List<String> messages) {
        List<String> sorted = new ArrayList<>();
        for (String message : messages) {
            Matcher list = EVENT_LIST.matcher(message);
            if (list.matches()) {
                List<String> events = new ArrayList<>(List.of(list.group(2).split(", | or ")));
                java.util.Collections.sort(events);
                message = list.group(1) + String.join(", ", events);
            }
            sorted.add(message);
        }
        return sorted;
    }

    /** Each unit's load messages, as {@code <line> <level>: <message>}. */
    static Map<String, List<String>> messages(List<String> console) {
        Map<String, List<String>> messages = new TreeMap<>();
        for (int i = 0; i < console.size(); i++) {
            Matcher prefix = PREFIX.matcher(console.get(i));
            if (!prefix.find()) {
                continue;
            }
            String level = prefix.group(1);
            Matcher header = HEADER.matcher(console.get(i).substring(prefix.end()));
            if (!header.find()) {
                continue;
            }
            Matcher unit = UNIT.matcher(header.group(2));
            if (!unit.find()) {
                continue;
            }
            StringBuilder message = new StringBuilder();
            for (int j = i + 1; j < console.size(); j++) {
                String text = PREFIX.matcher(console.get(j)).replaceFirst("");
                if (!text.startsWith("    ") || text.startsWith("    Line: ")) {
                    break;
                }
                if (!text.startsWith("    (from ")) {
                    message.append(message.isEmpty() ? "" : " ").append(text.strip());
                }
            }
            messages.computeIfAbsent(unit.group(1), u -> new ArrayList<>())
                    .add(header.group(1) + " " + level + ": " + message);
        }
        return messages;
    }

    /**
     * The console lines between a unit's markers, without timestamps and with NeoSkript's prefix as Skript's.
     * Stack traces are left out, as they differ between the two.
     */
    private static List<String> output(List<String> console) {
        List<String> output = new ArrayList<>();
        boolean inside = false;
        for (String line : console) {
            String text = PREFIX.matcher(line).replaceFirst("").replace("[NeoSkript]", "[Skript]");
            if (text.startsWith("[diff] begin ")) {
                inside = true;
            } else if (text.startsWith("[diff] end ")) {
                return output;
            } else if (UNKNOWN.matcher(text).find()) {
                output.add(text.contains("Error in diff-runner/") ? "(an internal error stopped the command)" : text);
                return output;
            } else if (inside && !text.isBlank() && !STACK.matcher(text).find() && !text.startsWith("*** ")) {
                // Paper's notice that it's out of date comes when it likes, so it's left out.
                output.add(text);
            }
        }
        return output;
    }

    /** The saved variables, sorted, with dates left out as they depend on when the scripts ran. */
    private static List<String> variables(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.matches(".*, date, [0-9A-F]+$") ? line.replaceFirst("[0-9A-F]+$", "<date>") : line)
                .sorted()
                .toList();
    }

    private static String cacheKey(ScenarioContext context, Environment reference, Inputs inputs)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(("v" + VERSION + " " + context.referenceSkript().version() + " " + reference.name() + " "
                        + reference.build() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        for (Map<String, String> scripts : List.of(inputs.loadOnly(), inputs.running())) {
            scripts.forEach((unit, script) -> digest.update((unit + "\n" + script + "\n").getBytes(StandardCharsets.UTF_8)));
        }
        try {
            digest.update(context.script("corpus-assert.sk").getBytes(StandardCharsets.UTF_8));
            digest.update(context.script("differential-show.sk").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 24);
    }
}
