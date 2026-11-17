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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An addon's syntax that pauses the trigger until a slow service answers, with the example addon's lottery office
 * ({@code examples/example-addon}), which answers on its own thread. The trigger carries on with its local variables
 * and loops, and a map the service gives sets a list's indices. A failure and a timeout are runtime errors on their
 * lines, after which the trigger carries on. On Folia, a trigger in {@code run on} a pig carries on on the pig's
 * thread, and a console command's on the global thread. A trigger waiting while its script is reloaded carries on
 * with its old code, as after a {@code wait}, and the server stops cleanly with a trigger still waiting. What an
 * {@code on unload} trigger pauses for finishes while the server stops, though the trigger doesn't carry on.
 */
public final class NeoSkriptPausingScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final Pattern IN_ANOTHER_REGION = Pattern.compile(Pattern.quote("    A pig is in another region,"
            + " which only that region's thread can read on Folia. Use 'run on' to run code there.") + "$");
    /** Anything thrown that reached the log: a pause must never end in a stack trace. */
    private static final Pattern EXCEPTION = Pattern.compile("Exception|\\tat [a-z]");

    @Override
    public String name() {
        return "neoskript-pausing";
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
        String script = context.script("neoskript-pausing.sk");
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .plugin(context.exampleAddonJar())
                .file("plugins/NeoSkript/scripts/pausing.sk", script);
        Path scripts = builder.runDir().resolve("plugins/NeoSkript/scripts");
        try (ServerProcess server = builder.start()) {
            server.await(line("[pausetest] loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);

            int mark = server.mark();
            server.command("pausetest values");
            inOrder(server, mark, List.of(
                    line("[pausetest] jackpot 1000 kept"),
                    line("[pausetest] receipts 2 Alex R-2"),
                    line("[pausetest] loop 1 1000"),
                    line("[pausetest] loop 2 1000")));

            mark = server.mark();
            server.command("pausetest failures");
            inOrder(server, mark, List.of(
                    line("    The lottery office at nowhere didn't answer"),
                    line("[pausetest] after failure <none>"),
                    Pattern.compile("    Gave up waiting for it after 2 seconds$"),
                    line("[pausetest] after timeout")));

            // Region correctness: both pigs' chunks stay loaded, so their regions tick.
            mark = server.mark();
            server.command("forceload add 4096 4096 4111 4111");
            server.await(Pattern.compile("(Marked|Chunk|chunk).*force loaded"), mark, WAIT);
            Thread.sleep(2_000);
            mark = server.mark();
            server.command("pausetest region");
            inOrder(server, mark, List.of(
                    Pattern.compile("\\[pausetest] pig after pause 3(\\.0)? 1000$"),
                    Pattern.compile(folia ? "\\[pausetest] global after pause <none>$"
                            : "\\[pausetest] global after pause 3(\\.0)?$")));
            int crossRegion = server.linesMatching(IN_ANOTHER_REGION).size();
            if (crossRegion != (folia ? 1 : 0)) {
                throw new HarnessFailure("Expected " + (folia ? 1 : 0) + " cross-region error after the pauses, got "
                        + crossRegion + ". See " + server.logFile());
            }

            // A trigger waiting while its script is reloaded carries on with its old code.
            mark = server.mark();
            server.command("pausetest reload");
            server.await(line("[pausetest] waiting across a reload"), mark, WAIT);
            Files.writeString(scripts.resolve("pausing.sk"), script.replace("old code carried on", "new code"));
            server.command("sk reload pausing");
            server.await(Pattern.compile("Reloaded pausing\\.sk"), mark, WAIT);
            server.await(line("[pausetest] old code carried on 1000"), mark, WAIT);
            mark = server.mark();
            server.command("pausetest reload");
            server.await(line("[pausetest] new code 1000"), mark, WAIT);

            // The server stops cleanly with a trigger still waiting.
            mark = server.mark();
            server.command("pausetest hang");
            server.await(line("[pausetest] hanging"), mark, WAIT);
            // A reload ran the unload trigger while the server ran; only what happens as it stops counts here.
            int stopping = server.mark();
            server.stop();
            List<String> afterStop = server.lines(stopping);
            if (afterStop.stream().anyMatch(l -> l.endsWith("[pausetest] after hang")
                    || l.endsWith("[pausetest] after the report"))) {
                throw new HarnessFailure("A trigger carried on while the server stopped. See " + server.logFile());
            }
            if (afterStop.stream().noneMatch(l -> l.endsWith("[lottery] report filed"))) {
                throw new HarnessFailure("The work an unload trigger paused for didn't finish before the server"
                        + " stopped. See " + server.logFile());
            }
            List<String> problems = new java.util.ArrayList<>(server.linesMatching(NeoSkriptRegionsScenario.THREAD_VIOLATION));
            problems.addAll(server.linesMatching(EXCEPTION));
            if (!problems.isEmpty()) {
                throw new HarnessFailure("Thread violations or exceptions reached the log: " + problems + ". See "
                        + server.logFile());
            }
        }
    }

    /** Waits for each pattern in turn, each after the line the one before it matched. */
    private static void inOrder(ServerProcess server, int from, List<Pattern> patterns) throws InterruptedException {
        int at = from;
        for (Pattern expected : patterns) {
            server.await(expected, at, WAIT);
            List<String> lines = server.lines(at);
            for (int i = 0; i < lines.size(); i++) {
                if (expected.matcher(lines.get(i)).find()) {
                    at += i + 1;
                    break;
                }
            }
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
