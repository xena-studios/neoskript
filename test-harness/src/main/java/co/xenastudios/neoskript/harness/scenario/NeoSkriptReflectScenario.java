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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoSkriptReflect against skript-reflect 2.6.3 (pinned by SHA-256) on Skript 2.17.0: the same script
 * ({@code scripts/reflect-check.sk}, and {@code scripts/reflect-custom.sk} for custom syntax) on both, with imports, static and instance calls, overloads, varargs, fields,
 * nested classes, arrays, {@code null}, classes, a Java event and the last error, each line it prints compared. A call
 * to a method that doesn't exist must warn on both. Each also times a method call, net of the loop around it. Where
 * the reference Skript doesn't run (Folia, 1.21.4), NeoSkript's lines are checked against what Skript printed.
 */
public final class NeoSkriptReflectScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    static final String REFLECT_URL =
            "https://github.com/SkriptLang/skript-reflect/releases/download/v2.6.3/skript-reflect-2.6.3.jar";
    static final String REFLECT_SHA256 = "0b422b828e17aac9e8417b6876924c1dcc221f6a065fb0a21b00da07fc298877";

    /** What skript-reflect printed, for the servers it doesn't run on. */
    private static final List<String> EXPECTED = List.of(
            "max int: 7", "abs: 2.5", "format: a-b", "size: 2", "size plus one: 3", "get: b", "list: [a, b]",
            "spread: a and b", "contains: true", "condition: yes", "max value: 2147483647", "map: 5",
            "instance of: yes", "array: [0, 7, 0]", "array element: 7", "collect: [1, 2, 3]", "null: null",
            "class of: java.util.ArrayList", "class named: java.lang.Thread", "dot class: class java.util.ArrayList",
            "world: world", "location x: 1", "field: 5", "nested: <none>",
            "fields: 6", "field names: x, y and serialVersionUID", "typed collect: [a, b]",
            "contains size: true", "plus call: 3", "equals call: false", "is call: true", "letter 5: false",
            "letter ab: <none>",
            "error: java.lang.IndexOutOfBoundsException: Index 9 out of bounds for length 2", "system: yes", "loaded",
            "custom shout: hi", "custom big: yes", "custom doubled: 8", "custom pattern 1: a", "custom pattern 2: b",
            "custom twice: 6", "custom twices: 2 and 4", "custom even: yes", "custom not even: yes", "custom java: 1",
            "custom whisper: psst", "custom done",
            "event: reflect-event", "missing done");

    /**
     * Where NeoSkriptReflect fixes skript-reflect on purpose: what skript-reflect prints, and what NeoSkriptReflect
     * prints instead. skript-reflect reads {@code EnderDragon.Phase} as a field that isn't there and gives no value;
     * NeoSkriptReflect reads it as the nested class, as Java does.
     */
    private static final Map<String, String> FIXED = Map.of("nested: <none>", "nested: LEAVE_PORTAL");

    @Override
    public String name() {
        return "neoskript-reflect";
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
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        List<String> expected = EXPECTED;
        String skriptBench = null;
        String skriptPolyBench = null;
        if (context.environment().skriptReference()) {
            ServerBuilder skript = context.server(runs.resolve("skript"))
                    .plugin(context.skriptJar())
                    .plugin(context.download(REFLECT_URL, REFLECT_SHA256, "skript-reflect-2.6.3.jar"))
                    .file("plugins/Skript/scripts/reflect-check.sk", context.script("reflect-check.sk"))
                    .file("plugins/Skript/scripts/reflect-custom.sk", context.script("reflect-custom.sk"));
            Result result = run(skript, false);
            expected = result.lines;
            skriptBench = result.bench;
            skriptPolyBench = result.polyBench;
            if (!result.missingWarned) {
                throw new HarnessFailure("skript-reflect didn't warn about the missing method. See " + skript.runDir());
            }
        }
        // skript-reflect's jar is in plugins/ too: it can't load without Skript, and NeoSkriptReflect says to remove it
        // (D21).
        ServerBuilder neoskript = context.server(runs.resolve("neoskript"))
                .plugin(context.neoskriptJar())
                .plugin(addonJar(context))
                .plugin(context.download(REFLECT_URL, REFLECT_SHA256, "skript-reflect-2.6.3.jar"))
                .file("plugins/NeoSkript/scripts/reflect-check.sk", context.script("reflect-check.sk"))
                .file("plugins/NeoSkript/scripts/reflect-custom.sk", context.script("reflect-custom.sk"));
        Result result = run(neoskript, true);
        context.summary("lines", result.lines.size());
        context.summary("neoskript ns per call", result.bench);
        context.summary("neoskript ns per call, 6 classes", result.polyBench);
        if (skriptBench != null) {
            context.summary("skript-reflect ns per call", skriptBench);
            context.summary("skript-reflect ns per call, 6 classes", skriptPolyBench);
        }
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < Math.max(expected.size(), result.lines.size()); i++) {
            String want = i < expected.size() ? expected.get(i) : "<nothing>";
            String got = i < result.lines.size() ? result.lines.get(i) : "<nothing>";
            if (!want.equals(got) && !got.equals(FIXED.get(want))) {
                differences.add("line " + (i + 1) + ": skript-reflect '" + want + "', NeoSkript '" + got + "'");
            }
        }
        if (!result.missingWarned) {
            differences.add("No warning for the missing method");
        }
        differences.addAll(result.problems);
        if (!differences.isEmpty()) {
            throw new HarnessFailure("NeoSkriptReflect differs from skript-reflect: " + differences + ". See "
                    + neoskript.runDir());
        }
    }

    private record Result(List<String> lines, String bench, String polyBench, boolean missingWarned,
            List<String> problems) {}

    private static Result run(ServerBuilder builder, boolean original) throws Exception {
        try (ServerProcess server = builder.start()) {
            // Where skript-reflect's jar is there without Skript, Paper refuses it with a stack trace, and then
            // NeoSkriptReflect says to remove it: problems count after that.
            int from = 0;
            if (original) {
                Pattern replaced = Pattern.compile(Pattern.quote("plugins/skript-reflect-2.6.3.jar is skript-reflect,"
                        + " which needs Skript, so it doesn't load with NeoSkript. NeoSkriptReflect replaces it:"));
                server.await(replaced, WAIT);
                List<String> lines = server.lines(0);
                for (int i = 0; i < lines.size(); i++) {
                    if (replaced.matcher(lines.get(i)).find()) {
                        from = i + 1;
                        break;
                    }
                }
            }
            server.await(Pattern.compile(Pattern.quote("[reflect] loaded")), WAIT);
            server.await(Pattern.compile(Pattern.quote("[reflect] custom done")), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();
            server.command("reflect-event");
            server.await(Pattern.compile(Pattern.quote("[reflect] event: ")), mark, WAIT);
            mark = server.mark();
            server.command("reflect-missing");
            server.await(Pattern.compile(Pattern.quote("[reflect] missing done")), mark, WAIT);
            boolean warned = server.lines(mark).stream().anyMatch(line ->
                    line.contains("No matching non-static method: ArrayList#sise called without arguments"));
            mark = server.mark();
            server.command("reflect-bench");
            Matcher bench = server.await(Pattern.compile(Pattern.quote("[reflect] bench ns per call: ") + "(\\S+)"),
                    mark, Duration.ofMinutes(5));
            String ns = bench.group(1);
            Matcher poly = server.await(Pattern.compile(Pattern.quote("[reflect] poly bench ns per call: ") + "(\\S+)"),
                    mark, Duration.ofMinutes(5));
            String polyNs = poly.group(1);
            List<String> problems = new ArrayList<>();
            Pattern problem = Pattern.compile("Exception|thread violation|another region", Pattern.CASE_INSENSITIVE);
            for (String line : server.lines(from)) {
                if (!problem.matcher(line).find()) {
                    continue;
                }
                if (!line.contains("[reflect] error:") && !line.contains("IndexOutOfBounds")) {
                    problems.add(line);
                }
            }
            server.stop();
            List<String> lines = new ArrayList<>();
            // Only what the script printed: a warning quoting a line of it ("Line 78: send "[reflect] ...") isn't.
            for (String line : server.linesMatching(Pattern.compile("]: \\[reflect] "))) {
                String text = line.substring(line.indexOf("]: [reflect] ") + "]: [reflect] ".length()).strip();
                if (!text.contains("bench ns per call")) {
                    lines.add(text);
                }
            }
            return new Result(lines, ns, polyNs, warned, problems);
        }
    }

    static Path addonJar(ScenarioContext context) throws IOException {
        for (Path jar : context.officialAddonJars()) {
            if ("NeoSkriptReflect".equals(NeoSkriptOfficialAddonsScenario.properties(jar).getProperty("name"))) {
                return jar;
            }
        }
        throw new HarnessFailure("NeoSkriptReflect's jar wasn't given to the harness");
    }
}
