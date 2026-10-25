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
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Skript's runtime error output and spam limits: a trigger erroring every tick prints its error twice (with
 * the script, the element, the line and its code), is summed up at the end of each 1 second frame, and is
 * put in timeout for 10 frames, after which it prints again.
 */
public final class NeoSkriptRuntimeErrorsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(1);
    private static final Pattern ERROR = Pattern.compile(Pattern.quote(
            "The script 'errors.sk' encountered an error while executing the 'Color from Hex Code' expression:") + "$");
    private static final Pattern MESSAGE = Pattern.compile("    Could not parse 'nope' as a hex code!$");
    private static final Pattern LINE = Pattern.compile(Pattern.quote("    Line 2: set {_c} to color from hex code \"nope\"#")
            + " every tick$");
    private static final Pattern SKIPPED = Pattern.compile("\\d+ runtime error\\[s] were thrown but not printed in the"
            + " last frame\\. From: 'errors\\.sk' line 2 \\(\\d+\\)$");
    private static final Pattern TIMED_OUT = Pattern.compile(Pattern.quote("Line 2 of script 'errors.sk' produced too many"
            + " runtime errors (4 allowed per frame). No errors from this line will be printed for 10 frames.") + "$");

    @Override
    public String name() {
        return "neoskript-runtime-errors";
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
                .file("plugins/NeoSkript/scripts/errors.sk",
                        "every tick:\n\tset {_c} to color from hex code \"nope\" # every tick\n");
        try (ServerProcess server = builder.start()) {
            server.await(ERROR, WAIT);
            server.await(MESSAGE, WAIT);
            server.await(LINE, WAIT);
            server.await(SKIPPED, WAIT);
            server.await(TIMED_OUT, WAIT);
            int printed = server.linesMatching(ERROR).size();
            if (printed > 4) {
                throw new HarnessFailure("A line printed more than 2 errors per frame before its timeout (" + printed
                        + "). See " + server.logFile());
            }
            // In timeout: only the summaries for a while.
            int mark = server.mark();
            Thread.sleep(6_000);
            if (server.linesMatching(ERROR).size() != printed) {
                throw new HarnessFailure("A line in timeout printed errors. See " + server.logFile());
            }
            if (server.linesMatching(SKIPPED).size() < 5) {
                throw new HarnessFailure("Frames in timeout weren't summed up. See " + server.logFile());
            }
            if (server.linesMatching(TIMED_OUT).size() != 1) {
                throw new HarnessFailure("The timeout was announced more than once. See " + server.logFile());
            }
            // After 10 frames, the line prints again, and is soon timed out again.
            server.await(ERROR, mark, WAIT);
            server.await(Pattern.compile(TIMED_OUT.pattern()), mark, WAIT);
            context.summary("errorsPrinted", server.linesMatching(ERROR).size());
            context.summary("framesSummed", server.linesMatching(SKIPPED).size());
            server.stop();
        }
    }
}
