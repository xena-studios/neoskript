/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * With Skript also installed, NeoSkript refuses to run and Skript keeps working (D10). With NeoSkriptCompat as well
 * (D22), Paper loads only one of the two plugins called Skript: whichever it is, exactly one enables, and the console
 * says why. Both orders of their jars' names are run.
 */
public final class SkriptConflictScenario implements Scenario {

    private static final Pattern REFUSED = Pattern.compile("Skript is installed\\. NeoSkript replaces Skript");
    private static final Pattern NEOSKRIPT_ENABLED = Pattern.compile("NeoSkript \\S+ enabled on");

    @Override
    public String name() {
        return "neoskript-skript-conflict";
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
        co.xenastudios.neoskript.harness.ServerBuilder plain = context.server();
        java.nio.file.Path runs = plain.runDir();
        try (ServerProcess server = plain
                .plugin(context.neoskriptJar())
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/smoke.sk", context.script("smoke.sk"))
                .start()) {
            server.await(REFUSED, Duration.ofMinutes(5));
            server.await(SkriptSmokeScenario.SMOKE_OK, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            if (!server.linesMatching(NEOSKRIPT_ENABLED).isEmpty()) {
                throw new HarnessFailure("NeoSkript enabled even though Skript is installed");
            }
        }
        withCompat(context, runs.resolveSibling(name() + "-compat-a"), "a-Skript.jar", "z-NeoSkriptCompat.jar");
        withCompat(context, runs.resolveSibling(name() + "-compat-b"), "z-Skript.jar", "a-NeoSkriptCompat.jar");
    }

    private static final Pattern SKRIPT_ENABLED = Pattern.compile("\\[Skript] Enabling Skript v");
    private static final Pattern COMPAT_ENABLED = Pattern.compile("\\[NeoSkriptCompat] Enabling NeoSkriptCompat v");
    private static final Pattern COMPAT_EXPLAINS = Pattern.compile("NeoSkriptCompat stands in for Skript, so Paper"
            + " loads only one of the two");

    /** NeoSkript, NeoSkriptCompat and Skript, with the jars named so Paper meets them in a given order. */
    private static void withCompat(ScenarioContext context, java.nio.file.Path runDir, String skriptName,
            String compatName) throws Exception {
        String label = skriptName + " and " + compatName;
        try (ServerProcess server = context.server(runDir)
                .plugin(context.neoskriptJar())
                .file("plugins/" + skriptName, Files.readAllBytes(context.skriptJar()))
                .file("plugins/" + compatName, Files.readAllBytes(context.compatJar()))
                .file("plugins/Skript/scripts/smoke.sk", context.script("smoke.sk"))
                .start()) {
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            boolean skript = !server.linesMatching(SKRIPT_ENABLED).isEmpty();
            boolean compat = !server.linesMatching(COMPAT_ENABLED).isEmpty();
            if (skript == compat) {
                throw new HarnessFailure("With " + label + ", " + (skript ? "both Skript and NeoSkriptCompat"
                        : "neither Skript nor NeoSkriptCompat") + " enabled");
            }
            if (compat) {
                if (server.linesMatching(NEOSKRIPT_ENABLED).isEmpty()) {
                    throw new HarnessFailure("With " + label + ", NeoSkriptCompat enabled but NeoSkript didn't");
                }
                if (server.linesMatching(COMPAT_EXPLAINS).isEmpty()) {
                    throw new HarnessFailure("With " + label + ", NeoSkriptCompat didn't say Skript was left out");
                }
            } else {
                if (server.linesMatching(REFUSED).isEmpty() || !server.linesMatching(NEOSKRIPT_ENABLED).isEmpty()) {
                    throw new HarnessFailure("With " + label + ", Skript enabled but NeoSkript didn't stand down");
                }
            }
            System.out.println("[harness] neoskript-skript-conflict: with " + label + ", "
                    + (compat ? "NeoSkriptCompat" : "Skript") + " enabled");
        }
    }
}
