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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** NeoSkript compiles and runs a script on every server: the first end-to-end check of M2. */
public final class NeoSkriptRunScenario implements Scenario {

    static final Pattern LOADED = Pattern.compile("\\[NeoSkript] Loaded 1 script with 1 trigger in (\\d+) ms$");
    static final List<Pattern> OUTPUT = List.of(
            Pattern.compile("\\[harness] neoskript hello: ok$"),
            Pattern.compile("\\[harness] neoskript says hi 1 and 2\\.5$"));

    @Override
    public String name() {
        return "neoskript-hello";
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
                .file("plugins/NeoSkript/scripts/hello.sk", context.script("neoskript-hello.sk"))
                .start()) {
            Matcher loaded = server.await(LOADED, Duration.ofMinutes(5));
            for (Pattern line : OUTPUT) {
                server.await(line, Duration.ofMinutes(5));
            }
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int exitCode = server.stop();
            List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
            if (!errors.isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
            }
            if (exitCode != 0) {
                throw new HarnessFailure("Server exited with code " + exitCode + ". See " + server.logFile());
            }
            context.summary("loadMs", Integer.parseInt(loaded.group(1)));
        }
    }
}
