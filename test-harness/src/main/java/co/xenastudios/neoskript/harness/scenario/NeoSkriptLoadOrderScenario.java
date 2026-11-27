/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * What an {@code on load} reaches, as in Skript 2.17.0, which registers each event's trigger in turn (script by
 * script, line by line) and runs each {@code on load} when it gets to it: an event it fires reaches only the
 * triggers before it. Reloading one script, the others' triggers listen all along, and its own run after theirs,
 * as Skript registers them again last. Reloading every script, the order is the first one's again. The expected
 * lines are what Skript printed for these scripts. The differential checks the first load against Skript too
 * ({@code load-fire-*}).
 */
public final class NeoSkriptLoadOrderScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-load-order";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    // The event is a weather change, which on Folia belongs to the global thread 'on load' runs on, so the scenario
    // runs there too. Only the changes made while {load-on} is set count, whatever the weather was.
    private static final String ONE = """
            on weather change:
            \t{load-on} is set
            \tadd "1: weather" to {load::*}
            """;

    private static final String TWO = """
            on weather change:
            \t{load-on} is set
            \tadd "2: weather, above the on load" to {load::*}

            on load:
            \tadd "2: load" to {load::*}
            \tset weather in world "world" to clear
            \tset {load-on} to true
            \tset weather in world "world" to rain
            \tdelete {load-on}
            \tadd "2: load, after the weather" to {load::*}

            on weather change:
            \t{load-on} is set
            \tadd "2: weather, below the on load" to {load::*}
            """;

    private static final String THREE = """
            on weather change:
            \t{load-on} is set
            \tadd "3: weather, above the on load" to {load::*}

            on load:
            \tadd "3: load" to {load::*}
            \tset {load-on} to true
            \tset weather in world "world" to clear
            \tdelete {load-on}
            \tsend "[harness] load order: loaded" to console

            on weather change:
            \t{load-on} is set
            \tadd "3: weather, below the on load" to {load::*}
            """;

    private static final String SHOW = """
            command /loadorder:
            \texecutable by: console
            \ttrigger:
            \t\tsend "[harness] load order: %{load::*}%" to console
            \t\tdelete {load::*}
            """;

    /** What Skript printed after loading every script: at startup, and reloading them all. */
    private static final String ALL = "2: load, 1: weather, 2: weather, above the on load, 2: load, after the weather,"
            + " 3: load, 1: weather, 2: weather, above the on load, 2: weather, below the on load and 3: weather, above the"
            + " on load";

    /** What Skript printed after reloading load-fire-2.sk on its own. */
    private static final String TWO_ALONE = "2: load, 1: weather, 3: weather, above the on load, 3: weather, below the"
            + " on load, 2: weather, above the on load and 2: load, after the weather";

    @Override
    public void run(ScenarioContext context) throws Exception {
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/load-fire-1.sk", ONE)
                .file("plugins/NeoSkript/scripts/load-fire-2.sk", TWO)
                .file("plugins/NeoSkript/scripts/load-fire-3.sk", THREE)
                .file("plugins/NeoSkript/scripts/show.sk", SHOW);
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            server.await(line("[harness] load order: loaded"), WAIT);
            int mark = server.mark();
            server.command("loadorder");
            server.await(line("[harness] load order: " + ALL), mark, WAIT);

            mark = server.mark();
            server.command("sk reload load-fire-2");
            server.await(Pattern.compile(Pattern.quote("[NeoSkript] Reloaded load-fire-2.sk in ") + "\\d+ ms\\.$"), mark, WAIT);
            mark = server.mark();
            server.command("loadorder");
            server.await(line("[harness] load order: " + TWO_ALONE), mark, WAIT);

            mark = server.mark();
            server.command("sk reload scripts");
            server.await(line("[harness] load order: loaded"), mark, WAIT);
            mark = server.mark();
            server.command("loadorder");
            server.await(line("[harness] load order: " + ALL), mark, WAIT);
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
