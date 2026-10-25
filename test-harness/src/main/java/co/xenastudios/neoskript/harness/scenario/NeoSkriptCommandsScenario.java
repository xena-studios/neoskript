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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Script commands on a real server: arguments, aliases, subcommands, choices, default values, named
 * arguments, {@code executable by}, usage messages, {@code execute command}, information about commands,
 * Skript's effect commands ({@code !set {x} to 5}),
 * and reloading a script that
 * changes and removes commands. The test servers have no players, so the console runs everything.
 */
public final class NeoSkriptCommandsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-commands";
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
                .file("plugins/NeoSkript/scripts/commands.sk", context.script("neoskript-commands.sk"))
                // Skript's effect commands, which are off by default.
                .file("plugins/NeoSkript/config.yml", "effect-commands:\n  enabled: true\n");
        Path scripts = builder.runDir().resolve("plugins/NeoSkript/scripts");
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] commands loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();

            expect(server, mark, "nsk-add 1 2", "[harness] commands sum 3 via nsk-add");
            expect(server, mark, "nsk-plus 4 5", "[harness] commands sum 9 via nsk-plus");
            expect(server, mark, "skript:nsk-add 1 1", "[harness] commands sum 2 via nsk-add");
            expect(server, mark, "nsk-calc 5", "[harness] commands calc 5");
            expect(server, mark, "nsk-calc 5 double", "[harness] commands double 10");
            expect(server, mark, "nsk-calc 5 inc", "[harness] commands calc 6");
            expect(server, mark, "nsk-calc 5 dec 3", "[harness] commands calc 2");
            expect(server, mark, "nsk-say", "[harness] commands said nothing by the console");
            expect(server, mark, "nsk-say hello there", "[harness] commands said hello there by the console");

            // Arguments that don't fit: the usage is shown and the trigger doesn't run.
            server.command("nsk-add one 2");
            server.await(Pattern.compile("Usage: /nsk-add <number> <number>$"), mark, WAIT);
            server.command("nsk-players-only");
            server.await(Pattern.compile("This command can't be used by the console$"), mark, WAIT);

            expect(server, mark, "nsk-run", "[harness] commands sum 5 via nsk-add");
            server.await(line("[harness] commands said from execute by the console"), mark, WAIT);

            expect(server, mark, "nsk-info", "[harness] commands listed");
            server.await(line("[harness] commands nsk-add is a script command"), mark, WAIT);
            server.await(line("[harness] commands help isn't a script command"), mark, WAIT);
            server.await(line("[harness] commands description: Adds two numbers"), mark, WAIT);

            effectCommands(server);

            // Reloading changes one command and removes the others.
            Files.writeString(scripts.resolve("commands.sk"), """
                    command /nsk-add <number> <number>:
                    \ttrigger:
                    \t\tsend "[harness] commands product %arg-1 * arg-2%"
                    """);
            server.command("sk reload commands.sk");
            server.await(Pattern.compile("Successfully reloaded .*commands\\.sk"), mark, WAIT);
            expect(server, mark, "nsk-add 3 4", "[harness] commands product 12");
            int unknown = server.mark();
            server.command("nsk-say still here");
            server.await(Pattern.compile("nsk-say still here<--\\[HERE]"), unknown, WAIT);
            server.stop();

            if (!server.linesMatching(line("[harness] commands players only ran")).isEmpty()) {
                throw new HarnessFailure("A players-only command ran for the console. See " + server.logFile());
            }
            if (!server.linesMatching(Pattern.compile(".*commands said still here.*")).isEmpty()) {
                throw new HarnessFailure("A command removed by reloading still ran. See " + server.logFile());
            }
        }
    }

    /**
     * Skript's effect commands, which the scenario's config turns on: the console's run straight away, can use the
     * global variables and the scripts' functions, and say what ran or what's wrong.
     */
    private static void effectCommands(ServerProcess server) throws Exception {
        int mark = server.mark();
        server.command("!set {effcmd} to 5");
        server.await(Pattern.compile(Pattern.quote("Executing 'set {effcmd} to 5'")), mark, WAIT);
        expect(server, mark, "!send \"[harness] commands effcmd %{effcmd}%\" to console", "[harness] commands effcmd 5");
        // (Broadcasts reach the players, and the test servers have none.)
        server.command("!broadcast \"[harness] commands effcmd broadcast\"");
        server.await(Pattern.compile(Pattern.quote("Executing 'broadcast \"[harness] commands effcmd broadcast\"'")),
                mark, WAIT);
        server.command("!set {effcmd::doubled} to nskEffcmdDouble(21)");
        expect(server, mark, "!send \"[harness] commands effcmd doubled %{effcmd::doubled}%\" to console",
                "[harness] commands effcmd doubled 42");
        expect(server, mark, "!send \"[harness] commands effcmd sender %command sender%, player %player%, me %me%\""
                + " to console", "[harness] commands effcmd sender the console, player <none>, me <none>");
        // Local variables don't outlast the effect.
        server.command("!set {_effcmd} to 1");
        expect(server, mark, "!send \"[harness] commands effcmd local %{_effcmd}%\" to console",
                "[harness] commands effcmd local <none>");

        // One that doesn't parse: Skript's error, with no more to say.
        int broken = server.mark();
        server.command("!this is not an effect at all");
        server.await(Pattern.compile(Pattern.quote("Error in: this is not an effect at all")), broken, WAIT);
        server.await(Pattern.compile(Pattern.quote("(No specific information is available)")), broken, WAIT);
        // A condition isn't an effect either.
        int condition = server.mark();
        server.command("!1 is 1");
        server.await(Pattern.compile(Pattern.quote("Error in: 1 is 1")), condition, WAIT);
        server.await(Pattern.compile(Pattern.quote("(No specific information is available)")), condition, WAIT);
        // The console isn't told they're unknown commands: they were effect commands.
        if (!server.linesMatching(Pattern.compile(".*Unknown or incomplete command.*")).isEmpty()
                || !server.linesMatching(Pattern.compile(".*!this is not an effect at all<--.*")).isEmpty()) {
            throw new HarnessFailure("An effect command also ran as a command. See " + server.logFile());
        }
    }

    private static void expect(ServerProcess server, int mark, String command, String output) throws Exception {
        server.command(command);
        server.await(line(output), mark, WAIT);
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
