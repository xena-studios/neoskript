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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Region ownership (plan 3.3), with two pigs 4096 blocks apart, each in its own region on Folia, and console
 * commands, which run on Folia's global region thread. On Folia, a script command's line that reads a pig in
 * another region goes to that region's thread to read it, as does an {@code every} trigger's line that reads a
 * chest at another world's spawn. In a Bukkit event still being dispatched, the read is a runtime error naming
 * the line (not Folia's stack trace) and reads nothing. Changing a pig is sent to its thread, and {@code run on}
 * runs code on its thread, in order with the rest of the trigger. On Paper, where one thread owns everything, the
 * same script reads, changes and runs everything as it is, with no errors.
 */
public final class NeoSkriptRegionsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);
    /** What Folia (or Paper) says when something is done on the wrong thread, which must never reach the log. */
    static final Pattern THREAD_VIOLATION = Pattern.compile("off owning region's thread|Thread failed main thread check"
            + "|Asynchronous [\\w ]+!|IllegalStateException");
    private static final Pattern ERROR = Pattern.compile(Pattern.quote("The script 'regions.sk' encountered an error"
            + " while executing the '") + "[^']+" + Pattern.quote("' expression:") + "$");
    private static final Pattern IN_ANOTHER_REGION = Pattern.compile(Pattern.quote("    A pig is in another region,"
            + " which only that region's thread can read on Folia. Use 'run on' to run code there.") + "$");

    @Override
    public String name() {
        return "neoskript-regions";
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
        boolean folia = context.environment().platform() == Environment.Platform.FOLIA;
        try (ServerProcess server = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/regions.sk", context.script("neoskript-regions.sk"))
                .start()) {
            server.await(line("[regiontest] loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int start = server.mark();
            // Keeps both pigs' chunks loaded, so each region ticks.
            server.command("forceload add 0 0");
            server.command("forceload add 4096 4096 4111 4111");
            server.await(Pattern.compile("(Marked|Chunk|chunk).*force loaded"), start, WAIT);
            Thread.sleep(2_000);

            int mark = server.mark();
            server.command("regiontest spawn");
            server.await(line("[regiontest] spawned"), mark, WAIT);

            mark = server.mark();
            server.command("regiontest read");
            server.await(Pattern.compile("\\[regiontest] read 5(\\.0)?$"), mark, WAIT);

            mark = server.mark();
            server.command("regionevent");
            if (folia) {
                server.await(ERROR, mark, WAIT);
                server.await(IN_ANOTHER_REGION, mark, WAIT);
                server.await(Pattern.compile(Pattern.quote("    Line 9: set {_health} to health of {regiontest::near}")
                        + "$"), mark, WAIT);
                server.await(line("[regiontest] event read <none>"), mark, WAIT);
            } else {
                server.await(Pattern.compile("\\[regiontest] event read 5(\\.0)?$"), mark, WAIT);
            }

            mark = server.mark();
            server.command("regiontest worlds");
            List<Pattern> worlds = List.of(
                    line("[regiontest] every world_nether chest, dirt and stone"),
                    line("[regiontest] every world_the_end chest, dirt and stone"),
                    line("[regiontest] every done"));
            int from = mark;
            for (Pattern expected : worlds) {
                server.await(expected, from, WAIT);
                from = lineOf(server, expected, from) + 1;
            }

            mark = server.mark();
            server.command("regiontest change");
            server.await(line("[regiontest] changed"), mark, WAIT);
            Thread.sleep(1_000);

            mark = server.mark();
            server.command("regiontest check");
            List<Pattern> order = new ArrayList<>(List.of(
                    Pattern.compile("\\[regiontest] far health 3(\\.0)?$"),
                    Pattern.compile("\\[regiontest] cross 4(\\.0)?$"),
                    Pattern.compile("\\[regiontest] far after wait 3(\\.0)?$"),
                    Pattern.compile("\\[regiontest] near health 4(\\.0)?$"),
                    line("[regiontest] after run on")));
            int at = mark;
            for (Pattern expected : order) {
                server.await(expected, at, WAIT);
                at = lineOf(server, expected, at) + 1;
            }

            mark = server.mark();
            server.command("regiontest section");
            order = List.of(
                    Pattern.compile("\\[regiontest] section Daisy 3(\\.0)?$"),
                    Pattern.compile("\\[regiontest] after section 3(\\.0)?$"),
                    line("[regiontest] loop section 1"),
                    line("[regiontest] after exit loop"));
            at = mark;
            for (Pattern expected : order) {
                server.await(expected, at, WAIT);
                at = lineOf(server, expected, at) + 1;
            }
            if (!server.linesMatching(line("[regiontest] loop section 2")).isEmpty()) {
                throw new HarnessFailure("'exit loop' in a section didn't leave the loop. See " + server.logFile());
            }
            if (folia && server.linesMatching(IN_ANOTHER_REGION).size() != 1) {
                throw new HarnessFailure("Reading a pig from another region's thread should have been 1 runtime error,"
                        + " was " + server.linesMatching(IN_ANOTHER_REGION).size() + ". See " + server.logFile());
            }
            server.stop();
            if (!folia && !server.linesMatching(ERROR).isEmpty()) {
                throw new HarnessFailure("Paper reported region errors. See " + server.logFile());
            }
            List<String> violations = server.linesMatching(THREAD_VIOLATION);
            if (!violations.isEmpty()) {
                throw new HarnessFailure("Thread violations reached the log: " + violations + ". See " + server.logFile());
            }
            context.summary("regionErrors", server.linesMatching(IN_ANOTHER_REGION).size());
        }
    }

    /** The number of the first line from {@code from} that matches {@code pattern}. */
    private static int lineOf(ServerProcess server, Pattern pattern, int from) {
        List<String> lines = server.lines(from);
        for (int i = 0; i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                return from + i;
            }
        }
        throw new IllegalStateException("No line matches " + pattern);
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
