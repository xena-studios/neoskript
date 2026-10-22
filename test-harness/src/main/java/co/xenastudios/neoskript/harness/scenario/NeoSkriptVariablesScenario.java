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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * M3's exit check: variables round-trip between Skript and NeoSkript byte for byte.
 *
 * <ol>
 *   <li>The reference Skript saves a {@code variables.csv} with many kinds of values.
 *   <li>NeoSkript picks it up (through the Skript migration), reads it, and rewrites it: the result has to
 *       be identical to Skript's file.
 *   <li>NeoSkript saves its own variables; Skript has to read them, and its rewrite has to be identical to
 *       NeoSkript's file.
 * </ol>
 */
public final class NeoSkriptVariablesScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);

    @Override
    public String name() {
        return "neoskript-variables";
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
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());

        // 1. Skript saves its variables.
        ServerBuilder skript = context.server(runs.resolve("1-skript-writes"))
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/set.sk", context.script("variables-skript.sk"));
        stopAfter(skript, Pattern.compile("\\[harness] variables set"));
        byte[] skriptFile = Files.readAllBytes(skript.runDir().resolve("plugins/Skript/variables.csv"));

        // 2. NeoSkript takes over the install, reads the variables and rewrites them.
        ServerBuilder neoskript = context.server(runs.resolve("2-neoskript-reads"))
                .plugin(context.neoskriptJar())
                .file("plugins/Skript/variables.csv", new String(skriptFile, StandardCharsets.UTF_8))
                .file("plugins/Skript/scripts/read.sk", context.script("variables-neoskript-read.sk"));
        List<String> read = stopAfter(neoskript, Pattern.compile("\\[harness] read loc: .*"));
        String values = read.stream().filter(line -> line.contains("[harness] read: ")).findFirst().orElse("");
        String expected = "hello | héllo ✓ 日本 | say \"hi\" | 42 | 2.5 | true | 5 minutes | a, b and ten | <itemtype>";
        if (!values.endsWith(expected)) {
            throw new HarnessFailure("NeoSkript read: '" + values + "', expected it to end with '" + expected + "'");
        }
        if (read.stream().anyMatch(line -> line.contains("read loc: <location>"))) {
            throw new HarnessFailure("NeoSkript didn't decode the location");
        }
        byte[] rewritten = Files.readAllBytes(neoskript.runDir().resolve("plugins/NeoSkript/variables.csv"));
        context.summary("variables", new String(skriptFile, StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).count());
        if (!Arrays.equals(skriptFile, rewritten)) {
            Files.write(runs.resolve("skript-variables.csv"), skriptFile);
            Files.write(runs.resolve("neoskript-variables.csv"), rewritten);
            throw new HarnessFailure("NeoSkript's rewrite of Skript's variables differs. Compare "
                    + runs.resolve("skript-variables.csv") + " and " + runs.resolve("neoskript-variables.csv"));
        }

        // 3. NeoSkript saves variables of its own, and Skript reads and rewrites them.
        ServerBuilder neoskriptWrites = context.server(runs.resolve("3-neoskript-writes"))
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/write.sk", context.script("variables-neoskript-write.sk"));
        stopAfter(neoskriptWrites, ServerProcess.DONE);
        byte[] neoskriptFile = Files.readAllBytes(neoskriptWrites.runDir().resolve("plugins/NeoSkript/variables.csv"));

        ServerBuilder skriptReads = context.server(runs.resolve("4-skript-reads"))
                .plugin(context.skriptJar())
                .file("plugins/Skript/variables.csv", new String(neoskriptFile, StandardCharsets.UTF_8))
                .file("plugins/Skript/scripts/read.sk", context.script("variables-skript-read.sk"));
        List<String> skriptRead = stopAfter(skriptReads, Pattern.compile("\\[harness] skript read: .*"));
        String skriptValues = skriptRead.stream().filter(line -> line.contains("[harness] skript read: ")).findFirst()
                .orElse("");
        String skriptExpected = "from neoskript ✓ | 7 | 0.25 | false | 2 hours and 3 seconds | x, y and z";
        if (!skriptValues.endsWith(skriptExpected)) {
            throw new HarnessFailure("Skript read: '" + skriptValues + "', expected it to end with '" + skriptExpected + "'");
        }
        byte[] skriptRewrite = Files.readAllBytes(skriptReads.runDir().resolve("plugins/Skript/variables.csv"));
        if (!Arrays.equals(neoskriptFile, skriptRewrite)) {
            Files.write(runs.resolve("neoskript-own-variables.csv"), neoskriptFile);
            Files.write(runs.resolve("skript-rewrite-variables.csv"), skriptRewrite);
            throw new HarnessFailure("Skript's rewrite of NeoSkript's variables differs. Compare "
                    + runs.resolve("neoskript-own-variables.csv") + " and " + runs.resolve("skript-rewrite-variables.csv"));
        }
    }

    /** Starts the server, waits for {@code marker}, stops it cleanly, and returns its console lines. */
    private static List<String> stopAfter(ServerBuilder builder, Pattern marker) throws Exception {
        try (ServerProcess server = builder.start()) {
            server.await(marker, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int exit = server.stop();
            if (exit != 0) {
                throw new HarnessFailure("Server exited with code " + exit + ". See " + server.logFile());
            }
            return server.linesMatching(Pattern.compile(".*"));
        }
    }
}
