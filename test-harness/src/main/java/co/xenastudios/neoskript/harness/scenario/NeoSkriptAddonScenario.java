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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The addon API on a real server: the example addon ({@code examples/example-addon}) registers a type, an event, an
 * effect, an expression, a condition and a function, and a script uses them. Its command reads the script's variable
 * and calls a function through the API, and {@code /sk info} lists it. The value of its type the script saved loads
 * again on the next start.
 */
public final class NeoSkriptAddonScenario implements Scenario {

    private static final Pattern LOADED = Pattern.compile("\\[NeoSkript] Loaded .*$");

    static final List<String> EXPECTED = List.of(
            "[harness] addon type: ticket #1 of Steve and ticket #2 of Alex",
            "[harness] addon condition: ticket #2 of Alex wins",
            "[harness] addon negated condition: ticket #1 of Steve doesn't",
            "[harness] addon function: 300",
            "[harness] addon event: ticket #2 of Alex won");

    static final List<String> COMMAND = List.of(
            "[lottery] last winner: ticket #2 of Alex",
            "[lottery] prize for 3: 300",
            "[lottery] on the global thread: true");

    @Override
    public String name() {
        return "neoskript-addon";
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
        ServerBuilder first = context.server()
                .plugin(context.neoskriptJar())
                .plugin(context.exampleAddonJar())
                .file("plugins/NeoSkript/scripts/addon.sk", context.script("neoskript-addon.sk"));
        Path variables;
        try (ServerProcess server = first.start()) {
            server.await(Pattern.compile(Pattern.quote("Variables before NeoSkript is ready: refused")),
                    Duration.ofMinutes(5));
            server.await(Pattern.compile(Pattern.quote("Registered the syntax of NeoSkriptExampleAddon")),
                    Duration.ofMinutes(5));
            loadedCleanly(server);
            for (String expected : EXPECTED) {
                server.await(Pattern.compile(Pattern.quote(expected) + "$"), Duration.ofMinutes(5));
            }
            // Nothing saved yet: the script's on load trigger sets it after NeoSkript is ready.
            server.await(Pattern.compile(Pattern.quote("NeoSkript is ready: 1 script(s) loaded, saved winner null")),
                    Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.command("lottery");
            for (String expected : COMMAND) {
                server.await(Pattern.compile(Pattern.quote(expected) + "$"), Duration.ofMinutes(1));
            }
            server.command("sk info");
            server.await(Pattern.compile("Installed Skript Addons: .*NeoSkriptExampleAddon"), Duration.ofMinutes(1));
            server.stop();
            noErrors(server);
            variables = first.runDir().resolve("plugins/NeoSkript/variables.csv");
        }
        String saved = Files.readString(variables, StandardCharsets.UTF_8);
        if (!saved.contains("lottery::last, neoskriptexample:ticket, ")) {
            throw new HarnessFailure("The ticket wasn't saved in variables.csv with the addon's codec: " + variables);
        }
        // The unload trigger runs after the addon has disabled; its change is saved too.
        if (!saved.contains("lottery::stopped, string, ")) {
            throw new HarnessFailure("A change made while the server stopped, after the addon disabled, wasn't saved: "
                    + variables);
        }

        // The next start loads the ticket back, with the addon's codec, before scripts use it.
        try (ServerProcess server = context.server()
                .plugin(context.neoskriptJar())
                .plugin(context.exampleAddonJar())
                .file("plugins/NeoSkript/variables.csv", saved)
                .file("plugins/NeoSkript/scripts/saved.sk", context.script("neoskript-addon-saved.sk"))
                .start()) {
            loadedCleanly(server);
            // Saved variables have loaded when NeoSkript is ready.
            server.await(Pattern.compile(Pattern.quote(
                    "NeoSkript is ready: 1 script(s) loaded, saved winner ticket #2 of Alex")), Duration.ofMinutes(5));
            server.await(Pattern.compile(Pattern.quote("[harness] addon saved: ticket #2 of Alex") + "$"),
                    Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            noErrors(server);
        }
    }

    private static void loadedCleanly(ServerProcess server) throws InterruptedException {
        String loaded = server.await(LOADED, Duration.ofMinutes(5)).group();
        if (loaded.contains("error")) {
            throw new HarnessFailure("The script didn't load cleanly: " + loaded + ". See " + server.logFile());
        }
    }

    private static void noErrors(ServerProcess server) {
        List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
        if (!errors.isEmpty()) {
            throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
        }
    }
}
