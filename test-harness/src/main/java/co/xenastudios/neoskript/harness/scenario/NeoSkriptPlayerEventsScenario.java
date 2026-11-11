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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player events with real players: the harness bot's players join, chat, run a script command and
 * quit, so the server fires the join, chat, command and quit events itself. The script answers through
 * {@code send}, and the bot logs what each player is shown. Where the reference Skript runs, the same
 * script runs on it too, and both must show the players and the console exactly the same.
 */
public final class NeoSkriptPlayerEventsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);
    /** What the comparison looks at: what the players were shown, and the script's console lines. */
    private static final Pattern TRANSCRIPT = Pattern.compile("(\\[bot:\\w+] .*|\\[harness] player .*)$");

    @Override
    public String name() {
        return "neoskript-player-events";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The bot speaks the 26.x protocol.
        return !environment.minecraftVersion().startsWith("1.");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        // The reference Skript goes first where it runs: it's the spec, so if it doesn't meet the
        // expectations below, they're what's wrong.
        List<String> skript = context.environment().skriptReference()
                ? play(context, runs.resolve("skript"), context.skriptJar(), "Skript")
                : null;
        List<String> neoskript = play(context, runs.resolve("neoskript"), context.neoskriptJar(), "NeoSkript");
        context.summary("lines", neoskript.size());
        if (skript == null) {
            return;
        }
        if (!neoskript.equals(skript)) {
            Files.write(runs.resolve("neoskript-transcript.txt"), neoskript);
            Files.write(runs.resolve("skript-transcript.txt"), skript);
            throw new HarnessFailure("NeoSkript and Skript showed the players different things. Compare "
                    + runs.resolve("neoskript-transcript.txt") + " with " + runs.resolve("skript-transcript.txt"));
        }
        context.summary("matches Skript", true);
    }

    /** Runs the script on one plugin, drives the bots through it, and returns the transcript. */
    private static List<String> play(ScenarioContext context, Path runDir, Path plugin, String folder)
            throws Exception {
        try (ServerProcess server = context.server(runDir)
                .plugin(plugin)
                .plugin(context.botJar())
                .file("plugins/" + folder + "/scripts/player-events.sk", context.script("player-events.sk"))
                .start()) {
            server.await(line("[harness] player loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            // No monsters to hurt the players halfway through.
            server.command("difficulty peaceful");
            int mark = server.mark();

            // Joining: the join event's message to the player, and its join message to everyone.
            server.command("bot join Alice");
            server.await(line("[bot] Alice joined"), mark, WAIT);
            server.await(line("[harness] player join Alice"), mark, WAIT);
            server.await(line("[bot:Alice] Welcome, Alice!"), mark, WAIT);
            server.await(line("[bot:Alice] Alice arrived"), mark, WAIT);
            server.command("bot join Bob");
            server.await(line("[bot:Bob] Welcome, Bob!"), mark, WAIT);
            server.await(line("[bot:Alice] Bob arrived"), mark, WAIT);

            // Chat: the event sees the message, and a cancelled message reaches nobody.
            server.command("bot chat Alice hello there");
            server.await(line("[harness] player chat Alice: hello there"), mark, WAIT);
            server.await(line("[bot:Bob] <Alice> hello there"), mark, WAIT);
            server.await(line("[bot:Alice] <Alice> hello there"), mark, WAIT);
            server.command("bot chat Bob secret");
            server.await(line("[harness] player chat Bob: secret"), mark, WAIT);
            server.await(line("[bot:Bob] Your secret is safe"), mark, WAIT);

            // A script command run by a player, whose message without 'to' goes back to that player.
            server.command("bot command Alice nsk-hello world");
            server.await(line("[bot:Alice] Hello Alice, you said world"), mark, WAIT);
            server.await(line("[harness] player command by Alice: world"), mark, WAIT);
            server.command("bot command Bob /nsk-hello");
            server.await(line("[bot:Bob] Hello Bob, you said nothing"), mark, WAIT);

            // Quitting: the quit event, and its quit message to whoever is left.
            server.command("bot quit Alice");
            server.await(line("[harness] player quit Alice"), mark, WAIT);
            server.await(line("[bot:Bob] Alice departed"), mark, WAIT);
            server.await(line("[bot] Alice left"), mark, WAIT);
            server.command("bot quit Bob");
            server.await(line("[bot] Bob left"), mark, WAIT);
            server.stop();

            if (!server.linesMatching(Pattern.compile("\\[bot:\\w+] <Bob> secret$")).isEmpty()) {
                throw new HarnessFailure(folder + ": a cancelled chat message still reached the players. See "
                        + server.logFile());
            }
            // Each player's connection delivers in order, but two players' messages can interleave
            // either way, so the transcript is grouped by who saw the line (stably, keeping that order).
            return server.linesMatching(TRANSCRIPT).stream()
                    .map(TRANSCRIPT::matcher)
                    .filter(Matcher::find)
                    .map(Matcher::group)
                    .sorted(Comparator.comparing(text -> text.substring(0, text.indexOf(']'))))
                    .toList();
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
