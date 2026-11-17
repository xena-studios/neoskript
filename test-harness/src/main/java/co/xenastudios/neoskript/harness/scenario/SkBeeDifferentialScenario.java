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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoSkriptCompat against Skript, with a real Skript addon: {@code scripts/skbee-differential.sk} on Skript 2.17 with
 * SkBee 3.26.0 (pinned by SHA-256), and on NeoSkript with NeoSkriptCompat and the same SkBee jar. Each part of the
 * script is a command, run from the console with a ping after it. Each label's {@code [skbee] label: value} lines must
 * match. On NeoSkript, SkBee must enable, NeoSkriptCompat must register its syntax, and no part may throw.
 *
 * <p>SkBee 3.26.0 needs Skript 2.15 or later and Minecraft 26.1 or later, so this runs on Paper 26.x only. Folia is
 * left out: SkBee doesn't support it.
 */
public final class SkBeeDifferentialScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final Duration PART = Duration.ofSeconds(30);
    private static final Duration END = Duration.ofSeconds(10);
    private static final Pattern LINE = Pattern.compile("\\[skbee] (.+?): (.*)$");
    private static final Pattern PART_NAME = Pattern.compile("(?m)^command /skbeetest-(\\S+):");

    /** SkBee's release the scenario runs, from Modrinth. */
    static final NeoSkriptHooksScenario.PinnedPlugin SKBEE = new NeoSkriptHooksScenario.PinnedPlugin(
            "SkBee-3.26.0.jar", "https://cdn.modrinth.com/data/a0tlbHZO/versions/FUHfTDWo/SkBee-3.26.0.jar",
            "68c0bc5a98478ffe16ad08e179bdf3825bbb4efcc4c68bfe81345e28b79ed4a2");

    @Override
    public String name() {
        return "skbee-differential";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference() && environment.platform() == Environment.Platform.PAPER
                && environment.minecraftVersion().startsWith("26.");
    }

    /** What one server did: its lines, in order, the parts that didn't finish, and its console. */
    record Result(List<String[]> lines, List<String> unfinished, List<String> console) {}

    @Override
    public void run(ScenarioContext context) throws Exception {
        String script = context.script("skbee-differential.sk");
        List<String> parts = new ArrayList<>();
        Matcher part = PART_NAME.matcher(script);
        while (part.find()) {
            parts.add(part.group(1));
        }
        Path skbee = context.download(SKBEE.url(), SKBEE.sha256(), SKBEE.fileName());
        Path runs = context.server().runDir();
        Result original = run(context.server(runs.resolve("skript"))
                .plugin(context.skriptJar())
                .plugin(skbee)
                .file("plugins/Skript/scripts/skbee.sk", script), parts);
        Result ours = run(context.server(runs.resolve("neoskript"))
                .plugin(context.neoskriptJar())
                .plugin(context.compatJar())
                .plugin(skbee)
                .file("plugins/NeoSkript/scripts/skbee.sk", script), parts);

        List<String> unexpected = new ArrayList<>();
        Map<String, List<String>> originalLines = byLabel(original.lines());
        Map<String, List<String>> ourLines = byLabel(ours.lines());
        java.util.Set<String> labels = new java.util.LinkedHashSet<>(originalLines.keySet());
        labels.addAll(ourLines.keySet());
        for (String label : labels) {
            List<String> x = originalLines.getOrDefault(label, List.of());
            List<String> y = ourLines.getOrDefault(label, List.of());
            if (!x.equals(y)) {
                unexpected.add(label + ": Skript with SkBee " + x + ", NeoSkript with SkBee " + y);
            }
        }
        for (String unfinished : original.unfinished()) {
            unexpected.add("Skript with SkBee didn't finish /skbeetest-" + unfinished);
        }
        for (String unfinished : ours.unfinished()) {
            unexpected.add("NeoSkript with SkBee didn't finish /skbeetest-" + unfinished);
        }
        if (ours.console().stream().noneMatch(line -> line.contains("[SkBee] Successfully enabled"))) {
            unexpected.add("SkBee didn't enable on NeoSkript");
        }
        String registered = ours.console().stream().filter(line -> line.contains("SkBee on NeoSkript:")).findFirst()
                .orElse(null);
        if (registered == null) {
            unexpected.add("NeoSkriptCompat didn't register SkBee's syntax");
        }
        for (String line : ours.console()) {
            if (line.contains("couldn't be registered") || line.contains("Error occurred while enabling")) {
                unexpected.add("NeoSkript's console: " + line.strip());
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("parts", parts);
        report.put("lines", original.lines().size());
        report.put("registered", registered == null ? null : registered.substring(registered.indexOf("SkBee on")));
        report.put("unexpected", unexpected);
        context.writeReport(name(), report);
        context.summary("lines", original.lines().size());
        if (original.lines().isEmpty()) {
            throw new HarnessFailure("Skript with SkBee printed nothing: is the script valid there? See "
                    + runs.resolve("skript"));
        }
        if (!unexpected.isEmpty()) {
            throw new HarnessFailure(unexpected.size() + " difference(s) from Skript with SkBee:\n  "
                    + String.join("\n  ", unexpected));
        }
    }

    /** Each label's values, in order: {@code part / label}. */
    private static Map<String, List<String>> byLabel(List<String[]> lines) {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        for (String[] line : lines) {
            labels.computeIfAbsent(line[0], label -> new ArrayList<>()).add(line[1]);
        }
        return labels;
    }

    private static Result run(ServerBuilder builder, List<String> parts) throws Exception {
        List<String[]> lines = new ArrayList<>();
        List<String> unfinished = new ArrayList<>();
        List<String> console;
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            for (String part : parts) {
                int mark = server.mark();
                server.command("skbeetest-" + part);
                server.command("skbeeping " + part);
                server.await(Pattern.compile("\\[skbee] ping " + Pattern.quote(part) + "$"), mark, PART);
                try {
                    server.await(Pattern.compile("\\[skbee] end " + Pattern.quote(part) + "$"), mark, END);
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
            console = server.lines(0);
            server.stop();
        }
        return new Result(lines, unfinished, console);
    }
}
