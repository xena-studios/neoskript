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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AI skill's examples are real scripts: every {@code ```skript} block in {@code plugins/skript}'s SKILL.md
 * loads without an error or warning on NeoSkript, and, where the reference Skript runs, on Skript too. The
 * skill tells models to write only syntax that exists, so its own examples have to.
 */
public final class SkillExamplesScenario implements Scenario {

    private static final Pattern BLOCK = Pattern.compile("```skript\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern NEOSKRIPT_LOADED =
            Pattern.compile("Loaded (\\d+) scripts? with (\\d+) triggers? in (\\d+) ms(?: \\((\\d+) errors?\\))?$");
    private static final Duration WAIT = Duration.ofMinutes(3);

    @Override
    public String name() {
        return "skill-examples";
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
        Map<String, String> scripts = examples(context);

        ServerBuilder neoskript = context.server().plugin(context.neoskriptJar());
        scripts.forEach((name, text) -> neoskript.file("plugins/NeoSkript/scripts/" + name, text));
        try (ServerProcess server = neoskript.start()) {
            Matcher loaded = server.await(NEOSKRIPT_LOADED, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            check("NeoSkript", server, Integer.parseInt(loaded.group(1)), scripts.size());
            server.stop();
        }

        if (context.environment().skriptReference()) {
            ServerBuilder skript = context.server().plugin(context.skriptJar());
            scripts.forEach((name, text) -> skript.file("plugins/Skript/scripts/" + name, text));
            try (ServerProcess server = skript.start()) {
                Matcher loaded = server.await(SkriptLoadBenchmark.LOADED, WAIT);
                server.await(ServerProcess.DONE, WAIT);
                check("Skript", server, Integer.parseInt(loaded.group(1)), scripts.size());
                server.stop();
            }
        }
        context.summary("examples", scripts.size());
    }

    /** Each block as its own script, named after its first line's {@code # name.sk:} comment when it has one. */
    private static Map<String, String> examples(ScenarioContext context) throws IOException {
        String skill = Files.readString(context.config().skillDir().resolve("SKILL.md"));
        Map<String, String> scripts = new LinkedHashMap<>();
        Matcher block = BLOCK.matcher(skill);
        while (block.find()) {
            String text = block.group(1) + "\n";
            Matcher named = Pattern.compile("^# ([\\w-]+\\.sk):").matcher(text);
            String name = named.find() ? named.group(1) : "example-" + (scripts.size() + 1) + ".sk";
            if (scripts.put(name, text) != null) {
                throw new HarnessFailure("Two examples in SKILL.md are both named " + name);
            }
        }
        if (scripts.isEmpty()) {
            throw new HarnessFailure("No ```skript blocks in " + context.config().skillDir().resolve("SKILL.md"));
        }
        return scripts;
    }

    private static void check(String implementation, ServerProcess server, int loaded, int expected) {
        List<String> problems = server.linesMatching(SkriptLoadBenchmark.SCRIPT_PROBLEM);
        if (!problems.isEmpty()) {
            throw new HarnessFailure(implementation + " reported problems in the skill's examples: " + problems
                    + ". See " + server.logFile());
        }
        if (loaded != expected) {
            throw new HarnessFailure("Expected " + expected + " examples, " + implementation + " loaded " + loaded
                    + ". See " + server.logFile());
        }
    }
}
