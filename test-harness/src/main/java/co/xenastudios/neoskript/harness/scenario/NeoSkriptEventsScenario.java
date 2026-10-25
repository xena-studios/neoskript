/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Server events on a real server: triggers run for the events they listen to, filters pick the right
 * ones, {@code cancel event} works, a message without {@code to} goes to the event's sender, and a trigger
 * that waits carries on afterwards. The test servers have no players, so console commands drive it.
 */
public final class NeoSkriptEventsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-events";
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
        try (ServerProcess server = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/events.sk", context.script("neoskript-events.sk"))
                .start()) {
            server.await(line("[harness] events loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);

            int mark = server.mark();
            server.command("nsk-greet");
            server.await(Pattern.compile("\\[harness] events greeted by .*console$"), mark, WAIT);
            server.await(line("[harness] events any command"), mark, WAIT);

            // An uncancelled command the server doesn't know: the server says so, as a control.
            server.command("nsk-plain");
            server.await(Pattern.compile("nsk-plain<--\\[HERE]"), mark, WAIT);

            server.command("nsk-wait");
            server.await(Pattern.compile("\\[harness] events after wait: .*console$"), mark, WAIT);
            // Entity events. On Folia a console command runs on the global thread, which can't spawn entities.
            boolean folia = context.environment().platform() == Environment.Platform.FOLIA;
            if (!folia) {
                server.command("nsk-pig");
                server.await(line("[harness] events spawned pig"), mark, WAIT);
                server.await(line("[harness] events damage 1 by custom"), mark, WAIT);
                server.await(line("[harness] events death of pig"), mark, WAIT);
                server.await(Pattern.compile("\\[harness] events weather in .* to rain$"), mark, WAIT);
            }
            // Counted before stopping, as 'stop' is a command too.
            int anyCommand = server.linesMatching(line("[harness] events any command")).size();
            server.stop();

            if (!server.linesMatching(Pattern.compile(".*nsk-(greet|wait)<--\\[HERE].*")).isEmpty()) {
                throw new HarnessFailure("Cancelling the command event didn't stop the command. See " + server.logFile());
            }
            if (!server.linesMatching(line("[harness] events wrong filter ran")).isEmpty()) {
                throw new HarnessFailure("A trigger ran for a command its filter excludes. See " + server.logFile());
            }
            if (!server.linesMatching(line("[harness] events wrong spawn filter ran")).isEmpty()) {
                throw new HarnessFailure("A spawn trigger ran for an entity its filter excludes. See " + server.logFile());
            }
            if (anyCommand != (folia ? 3 : 4)) {
                throw new HarnessFailure("'on command' should have run once per command. See " + server.logFile());
            }
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
