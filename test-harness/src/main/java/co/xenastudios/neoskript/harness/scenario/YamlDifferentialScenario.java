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
import java.io.IOException;
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
import java.util.stream.Stream;

/**
 * NeoSkriptYaml against the addon it replaces: {@code scripts/yaml-differential.sk} on Skript 2.17 with skript-yaml
 * 1.7.2 (pinned by SHA-256, as servers are), and on NeoSkript with NeoSkriptYaml. Each part of the script is a command,
 * run from the console with a ping after it, so a part that fails on one side doesn't stop the rest. The scenario
 * compares each {@code [yaml] label: value} line and every file the script leaves in {@code plugins/YamlTest}, byte for
 * byte. A difference {@code corpus/yaml/known-differences.txt} doesn't list, with why, fails it.
 *
 * <p>On Paper 26.x, where the reference Skript runs (D18). skript-yaml can't save locations, vectors,
 * {@code !skriptclass} values or text with {@code &} there (it reads 26.3 as a version before 1.13), so those parts also
 * run on the reference Skript on Paper 1.21.11, pinned here, and must match it with no known differences. Folia is
 * covered by {@code neoskript-yaml}.
 */
public final class YamlDifferentialScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final Duration PART = Duration.ofSeconds(30);
    /** How long a part may go on after its ping: a pause, as deleting a folder's files. */
    private static final Duration END = Duration.ofSeconds(10);
    private static final Pattern LINE = Pattern.compile("\\[yaml] (.+?): (.*)$");
    private static final Pattern PART_NAME = Pattern.compile("(?m)^command /yamltest-(\\S+):");

    /** Paper 1.21.11 (Java 21), pinned as the environments are, for the reference Skript where skript-yaml works. */
    static final Environment PAPER_1_21 = new Environment("paper-1.21.11", Environment.Platform.PAPER, "1.21.11", 132, 21,
            new Environment.Download("https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar",
                    "5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"), true);
    /** The parts skript-yaml can't save on 26.3, and the files they write. */
    static final List<String> ON_1_21 = List.of("type-location", "type-vector", "type-gamemode",
            "type-text-with-colour", "colour");
    static final List<String> ON_1_21_FILES = List.of("types/location.yml", "types/vector.yml", "types/gamemode.yml",
            "types/text-with-colour.yml", "colour-text.yml");

    /** skript-yaml's latest release, the one NeoSkriptYaml matches. */
    static final NeoSkriptHooksScenario.PinnedPlugin SKRIPT_YAML = new NeoSkriptHooksScenario.PinnedPlugin(
            "skript-yaml-1.7.2.jar", "https://github.com/Sashie/skript-yaml/releases/download/v1.7.2/skript-yaml-1.7.2.jar",
            "da715f2623db79e7d49ddbbf879fb1c0c04e4836e7836f4f1db27bad981d6e6a");

    @Override
    public String name() {
        return "yaml-differential";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference() && environment.platform() == Environment.Platform.PAPER;
    }

    /** What one server did: its lines, by order, and its files' contents, by path. */
    record Result(List<String[]> lines, Map<String, String> files, List<String> unfinished) {}

    @Override
    public void run(ScenarioContext context) throws Exception {
        String script = context.script("yaml-differential.sk");
        List<String> parts = new ArrayList<>();
        Matcher part = PART_NAME.matcher(script);
        while (part.find()) {
            parts.add(part.group(1));
        }
        Path runs = context.server().runDir();
        ServerBuilder skript = context.server(runs.resolve("skript"))
                .plugin(context.skriptJar())
                .plugin(context.download(SKRIPT_YAML.url(), SKRIPT_YAML.sha256(), SKRIPT_YAML.fileName()))
                .file("plugins/Skript/scripts/yaml.sk", script);
        Result original = run(given(skript), parts, "plugins/YamlTest");
        ServerBuilder neoskript = context.server(runs.resolve("neoskript"))
                .plugin(context.neoskriptJar())
                .plugin(NeoSkriptYamlScenario.addonJar(context))
                .file("plugins/NeoSkript/scripts/yaml.sk", script);
        Result ours = run(given(neoskript), parts, "plugins/YamlTest");
        // What skript-yaml can't save on 26.3, compared where it can: the reference Skript on Paper 1.21.11, which
        // skript-yaml reads as a version after 1.13. Only the parts that fail on 26.3, as items and the rest are
        // written differently from one Minecraft version to the next.
        // If it can't run here (no Java 21, or Paper 1.21.11 can't be downloaded), that part is skipped with a note;
        // once its server has started, anything that goes wrong fails the scenario as usual.
        Result onOlder = null;
        String olderSkipped = null;
        ServerBuilder older = null;
        try {
            Path java = context.config().javaExecutable(PAPER_1_21.javaVersion());
            if (java == null || !Files.isExecutable(java)) {
                throw new IOException("no Java " + PAPER_1_21.javaVersion() + " at " + java);
            }
            older = context.server(PAPER_1_21, runs.resolve("skript-1.21"))
                    .plugin(context.skriptJar())
                    .plugin(context.download(SKRIPT_YAML.url(), SKRIPT_YAML.sha256(), SKRIPT_YAML.fileName()))
                    .file("plugins/Skript/scripts/yaml.sk", script);
        } catch (IOException | RuntimeException unavailable) {
            olderSkipped = unavailable.toString();
        }
        if (older != null) {
            onOlder = run(given(older), ON_1_21, "plugins/YamlTest");
        }

        Map<String, String> known = NeoSkriptYamlScenario.knownDifferences(context);
        List<String> unexpected = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        compareLines(original.lines(), ours.lines(), known, unexpected, expected);
        compareFiles(original.files(), ours.files(), known, unexpected, expected);
        List<String> olderCompared = new ArrayList<>();
        if (onOlder == null) {
            report.put("paper1_21Skipped", olderSkipped);
            context.summary("1.21.11 comparison", "skipped: " + olderSkipped);
            System.out.println("[harness] yaml-differential: the Paper 1.21.11 comparison is skipped: " + olderSkipped);
        }
        for (String olderPart : onOlder == null ? List.<String>of() : ON_1_21) {
            if (onOlder.unfinished().contains(olderPart)) {
                unexpected.add("skript-yaml on Paper 1.21.11 didn't finish /yamltest-" + olderPart);
            }
        }
        List<String[]> olderLines = onOlder == null ? List.of() : onOlder.lines();
        List<String[]> ourLines = onOlder == null ? List.of() : ours.lines().stream()
                .filter(line -> ON_1_21.stream().anyMatch(p -> line[0].startsWith(p + " / "))).toList();
        List<String> olderUnexpected = new ArrayList<>();
        compareLines(olderLines, ourLines, Map.of(), olderUnexpected, new ArrayList<>());
        Map<String, String> olderFiles = new TreeMap<>();
        Map<String, String> ourFiles = new TreeMap<>();
        for (String file : onOlder == null ? List.<String>of() : ON_1_21_FILES) {
            olderFiles.put(file, onOlder.files().get(file));
            ourFiles.put(file, ours.files().get(file));
            olderCompared.add(file);
        }
        compareFiles(olderFiles, ourFiles, Map.of(), olderUnexpected, new ArrayList<>());
        olderUnexpected.forEach(found -> unexpected.add("against skript-yaml on Paper 1.21.11, " + found));
        report.put("comparedOnPaper1_21", olderCompared);
        // A known difference that doesn't happen any more is out of date: it would hide a difference that comes back.
        for (String entry : known.keySet()) {
            if (expected.stream().noneMatch(found -> found.startsWith(entry + ":") || found.startsWith(entry + " ("))) {
                unexpected.add("known-differences.txt lists '" + entry + "', which didn't differ");
            }
        }
        for (String unfinished : ours.unfinished()) {
            unexpected.add("NeoSkript didn't finish /yamltest-" + unfinished);
        }
        report.put("parts", parts);
        report.put("skriptUnfinished", original.unfinished());
        report.put("lines", ours.lines().size());
        report.put("files", ours.files().size());
        report.put("knownDifferences", expected);
        report.put("unexpected", unexpected);
        context.writeReport(name(), report);
        context.summary("lines", ours.lines().size());
        context.summary("files", ours.files().size());
        context.summary("known differences", expected.size());
        if (original.lines().isEmpty()) {
            throw new HarnessFailure("Skript with skript-yaml printed nothing: is the script valid there? See "
                    + runs.resolve("skript"));
        }
        if (!unexpected.isEmpty()) {
            throw new HarnessFailure(unexpected.size() + " difference(s) from skript-yaml that known-differences.txt"
                    + " doesn't list:\n  " + String.join("\n  ", unexpected));
        }
    }

    /** The files the script reads, as a server owner would have written them. */
    static ServerBuilder given(ServerBuilder builder) {
        return builder
                .file("plugins/YamlTest/given.yml", """
                        ## Test data
                        ## for the yaml differential

                        # The settings
                        settings:
                            name: Test Server
                            ratio: 0.75
                            big: 12345678901
                            enabled: true
                        owner: 069a79f4-44e9-4726-a5be-fca90e38aaf5
                        # Where players live
                        homes:
                            a:
                                x: 1
                                z: 2
                            b:
                                x: 3
                                z: 4
                        """)
                .file("plugins/YamlTest/motd.yml", "motd: '&6Welcome &ato the server'\n")
                .file("plugins/YamlTest/numbers.yml", "numbers:\n- 1\n- 2\n- 3\n")
                .file("plugins/YamlTest/doomed-folder/a.yml", "a: 1\n")
                .file("plugins/YamlTest/doomed-folder/b.yaml", "b: 2\n")
                .file("plugins/YamlTest/doomed-folder/notes.txt", "not yaml\n")
                .file("plugins/YamlTest/folder/one.yml", "a: 1\n")
                .file("plugins/YamlTest/folder/two.yml", "b: two\n");
    }

    private static Result run(ServerBuilder builder, List<String> parts, String folder) throws Exception {
        List<String[]> lines = new ArrayList<>();
        List<String> unfinished = new ArrayList<>();
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            for (String part : parts) {
                int mark = server.mark();
                server.command("yamltest-" + part);
                server.command("yamlping " + part);
                server.await(Pattern.compile("\\[yaml] ping " + Pattern.quote(part) + "$"), mark, PART);
                // A part that pauses (as deleting a folder's files does) carries on after the ping: its end is the
                // last thing it prints. One that failed on this side never prints it.
                Pattern end = Pattern.compile("\\[yaml] end " + Pattern.quote(part) + "$");
                try {
                    server.await(end, mark, END);
                } catch (HarnessFailure missing) {
                    unfinished.add(part);
                }
                for (String line : server.lines(mark)) {
                    Matcher matcher = LINE.matcher(line);
                    if (matcher.find() && !matcher.group(1).equals("ping") && !matcher.group(1).startsWith("end ")) {
                        lines.add(new String[] {part + " / " + matcher.group(1), matcher.group(2)});
                    }
                }
                if (!unfinished.contains(part) && server.lines(mark).stream().anyMatch(
                        line -> line.contains("An internal error occurred") || line.contains("Exception"))) {
                    unfinished.add(part);
                }
            }
            server.stop();
        }
        return new Result(lines, files(builder.runDir().resolve(folder)), unfinished);
    }

    /** Every file in a folder, by its path in it, with {@code /}. */
    static Map<String, String> files(Path folder) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(folder.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        return files;
    }

    private static void compareLines(List<String[]> original, List<String[]> ours, Map<String, String> known,
            List<String> unexpected, List<String> expected) {
        Map<String, List<String>> a = byLabel(original);
        Map<String, List<String>> b = byLabel(ours);
        java.util.Set<String> labels = new java.util.LinkedHashSet<>(a.keySet());
        labels.addAll(b.keySet());
        for (String label : labels) {
            List<String> x = a.getOrDefault(label, List.of());
            List<String> y = b.getOrDefault(label, List.of());
            if (x.equals(y)) {
                continue;
            }
            String difference = "output " + label + ": skript-yaml " + x + ", NeoSkriptYaml " + y;
            String why = known.get("output " + label);
            if (why != null) {
                expected.add(difference + " (" + why + ")");
            } else {
                unexpected.add(difference);
            }
        }
    }

    private static Map<String, List<String>> byLabel(List<String[]> lines) {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        for (String[] line : lines) {
            labels.computeIfAbsent(line[0], label -> new ArrayList<>()).add(line[1]);
        }
        return labels;
    }

    private static void compareFiles(Map<String, String> original, Map<String, String> ours, Map<String, String> known,
            List<String> unexpected, List<String> expected) {
        java.util.Set<String> paths = new java.util.TreeSet<>(original.keySet());
        paths.addAll(ours.keySet());
        for (String path : paths) {
            String x = original.get(path);
            String y = ours.get(path);
            if (java.util.Objects.equals(x, y)) {
                continue;
            }
            String difference = "file " + path + ":\n--- skript-yaml\n" + x + "\n--- NeoSkriptYaml\n" + y;
            String why = known.get("file " + path);
            if (why != null) {
                expected.add("file " + path + " (" + why + ")");
            } else {
                unexpected.add(difference);
            }
        }
    }
}
