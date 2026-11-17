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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * NeoSkriptYaml on its own, on every server, Folia included ({@code scripts/neoskript-yaml.sk}): what it does
 * differently from skript-yaml on purpose, files it refuses (not YAML, a list at the top, an alias bomb, a path outside
 * the server's folder) and leaves as they were, a big file, one file changed and saved by two triggers at once (on
 * Folia, on two regions' threads), files staying loaded across {@code /sk reload}, and the saves still pending written
 * whole when the server stops. With skript-yaml's jar in {@code plugins/} (which needs Skript, so Paper doesn't load
 * it), NeoSkriptYaml loads and says to remove it (D21).
 */
public final class NeoSkriptYamlScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final String BROKEN = "a: [1, 2\nb: 3\n";
    private static final String KEPT = "home: !location\n    world: nowhere\n    x: 1.0\n    y: 2.0\n    z: 3.0\n"
            + "    yaw: 0.0\n    pitch: 0.0\n";

    @Override
    public String name() {
        return "neoskript-yaml";
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
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .plugin(addonJar(context))
                .file("plugins/NeoSkript/scripts/neoskript-yaml.sk", context.script("neoskript-yaml.sk"))
                .file("plugins/YamlCheck/numbers.yml", "numbers:\n- 1\n- 2\n- 3\n")
                .file("plugins/YamlCheck/commented.yml", "## Header\n# kept\na: 1\n")
                .file("plugins/YamlCheck/kept.yml", KEPT)
                .file("plugins/YamlCheck/broken.yml", BROKEN)
                .file("plugins/YamlCheck/list.yml", "- 1\n- 2\n")
                .file("plugins/YamlCheck/bomb.yml", bomb())
                .file("plugins/YamlCheck/big.yml", big());
        Path original = builder.runDir().resolveSibling(name() + "-original").resolve("skript-yaml.jar");
        writeOriginal(original);
        builder.plugin(original);
        Path escape = builder.runDir().getParent().resolve("yaml-escape.yml");
        Files.deleteIfExists(escape);
        List<String> failures = new ArrayList<>();
        try (ServerProcess server = builder.start()) {
            // Paper refuses skript-yaml's jar first, as it needs Skript; what's checked comes after that.
            Pattern refused = Pattern.compile("Could not load 'plugins/(\\.paper-remapped/)?skript-yaml\\.jar'"
                    + "|" + Pattern.quote("Could not load plugin 'skript-yaml"));
            server.await(refused, WAIT);
            int after = server.lines(0).size();
            for (int i = 0; i < server.lines(0).size(); i++) {
                if (refused.matcher(server.lines(0).get(i)).find()) {
                    after = i + 1;
                    break;
                }
            }
            server.await(Pattern.compile(Pattern.quote("plugins/skript-yaml.jar is skript-yaml, which needs Skript, so it"
                    + " doesn't load with NeoSkript. NeoSkriptYaml replaces it:")), after, WAIT);
            server.await(line("[yamlcheck] loaded"), after, WAIT);
            server.await(ServerProcess.DONE, after, WAIT);
            int start = server.mark();
            // Keeps both places' chunks loaded, so each region ticks on Folia.
            server.command("forceload add 0 0");
            server.command("forceload add 4096 4096 4111 4111");
            server.await(Pattern.compile("(Marked|Chunk|chunk).*force loaded"), start, WAIT);
            for (String part : List.of("basics", "refused", "big")) {
                int mark = server.mark();
                server.command("yamlcheck-" + part);
                server.await(line("[yamlcheck] " + part + " done"), mark, WAIT);
            }
            int mark = server.mark();
            server.command("yamlcheck-stress near");
            server.command("yamlcheck-stress far");
            server.await(line("[yamlcheck] stress near done"), mark, WAIT);
            server.await(line("[yamlcheck] stress far done"), mark, WAIT);
            mark = server.mark();
            server.command("yamlcheck-stress-result");
            server.await(line("[yamlcheck] stress result done"), mark, WAIT);
            mark = server.mark();
            server.command("sk reload neoskript-yaml");
            server.await(line("[yamlcheck] loaded"), mark, WAIT);
            server.command("yamlcheck-after-reload");
            server.await(line("[yamlcheck] after reload done"), mark, WAIT);
            // Saved last, as the server stops: written whole by the time it's stopped.
            server.command("yamlcheck-stress near");
            server.stop();
            failures.addAll(server.linesMatching(Pattern.compile("\\[yamlcheck] FAIL")));
            failures.addAll(server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR));
            // Paper's own refusal of skript-yaml's jar is an UnknownDependencyException: anything else isn't expected.
            server.linesMatching(Pattern.compile("Exception|An internal error|thread violation|another region",
                    Pattern.CASE_INSENSITIVE)).stream()
                    .filter(found -> !found.contains("UnknownDependencyException") && !found.contains("skript-yaml"))
                    .forEach(failures::add);
            int checks = server.linesMatching(Pattern.compile("\\[yamlcheck] ok ")).size();
            context.summary("checks", checks);
            if (server.linesMatching(Pattern.compile(Pattern.quote("outside the server's folder"))).isEmpty()) {
                failures.add("No warning for a path outside the server's folder");
            }
            if (server.linesMatching(Pattern.compile("Couldn't load 'broken' .*isn't valid YAML")).isEmpty()) {
                failures.add("No warning for a broken file");
            }
        }
        Path folder = builder.runDir().resolve("plugins/YamlCheck");
        expect(failures, folder.resolve("broken.yml"), BROKEN, "the broken file was changed");
        expect(failures, folder.resolve("list.yml"), "- 1\n- 2\n", "the list file was changed");
        if (Files.exists(escape)) {
            failures.add("A file was made outside the server's folder: " + escape);
        }
        String kept = Files.readString(folder.resolve("kept.yml"));
        if (!kept.startsWith(KEPT.replace("\n", System.lineSeparator()))) {
            failures.add("A location in a world that isn't loaded wasn't kept:\n" + kept);
        }
        String commented = Files.readString(folder.resolve("commented.yml"));
        if (!commented.contains("# kept")) {
            failures.add("The comment under the header was lost:\n" + commented);
        }
        try (var files = Files.list(folder)) {
            List<Path> leftovers = files.filter(file -> file.getFileName().toString().endsWith(".tmp")).toList();
            if (!leftovers.isEmpty()) {
                failures.add("Temporary files were left behind: " + leftovers);
            }
        }
        String stress = Files.readString(folder.resolve("stress.yml"));
        if (!stress.contains("near-list:") || !stress.contains("far-list:")) {
            failures.add("The stress file isn't whole:\n" + stress);
        }
        if (!failures.isEmpty()) {
            throw new HarnessFailure("NeoSkriptYaml failed: " + failures + ". See " + builder.runDir());
        }
    }

    private static void expect(List<String> failures, Path file, String content, String problem) throws IOException {
        if (!Files.readString(file).equals(content)) {
            failures.add(problem + ": " + file);
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }

    /** Aliases that multiply to a billion values if read naively. */
    private static String bomb() {
        StringBuilder bomb = new StringBuilder("a0: &a0 [x, x, x, x, x, x, x, x, x, x]\n");
        for (int i = 1; i < 9; i++) {
            bomb.append("a").append(i).append(": &a").append(i).append(" [");
            for (int j = 0; j < 10; j++) {
                bomb.append(j == 0 ? "" : ", ").append("*a").append(i - 1);
            }
            bomb.append("]\n");
        }
        return bomb.toString();
    }

    /** A file of 100,000 keys, about 1.5 MB. */
    private static String big() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            big.append('k').append(i).append(": ").append(i).append('\n');
        }
        return big.toString();
    }

    /** A stand-in for skript-yaml's jar: a plugin of that name that needs Skript, which Paper won't load. */
    private static void writeOriginal(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: skript-yaml\nversion: 1.7.2\nmain: me.sashie.skriptyaml.SkriptYaml\napi-version: 1.16\n"
                    + "depend: [Skript]\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** NeoSkriptYaml's jar, from the official addons the harness was given. */
    static Path addonJar(ScenarioContext context) throws IOException {
        for (Path jar : context.officialAddonJars()) {
            if ("NeoSkriptYaml".equals(NeoSkriptOfficialAddonsScenario.properties(jar).getProperty("name"))) {
                return jar;
            }
        }
        throw new HarnessFailure("NeoSkriptYaml's jar wasn't given to the harness");
    }

    /**
     * The differences from skript-yaml the yaml-differential scenario expects, from
     * {@code corpus/yaml/known-differences.txt}: {@code output <part> / <label> | why} and {@code file <path> | why}.
     */
    static Map<String, String> knownDifferences(ScenarioContext context) throws IOException {
        Map<String, String> known = new LinkedHashMap<>();
        Path file = context.config().realWorldCorpus().resolveSibling("yaml").resolve("known-differences.txt");
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int bar = line.indexOf(" | ");
            if (bar < 0) {
                throw new HarnessFailure("A known difference without a reason (' | why'): " + line);
            }
            known.put(line.substring(0, bar).strip(), line.substring(bar + 3).strip());
        }
        return known;
    }
}
