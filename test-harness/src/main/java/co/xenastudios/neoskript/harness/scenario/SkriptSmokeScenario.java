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

/** The reference Skript runs a script. This is the baseline side of future differential tests. */
public final class SkriptSmokeScenario implements Scenario {

    static final Pattern SMOKE_OK = Pattern.compile("\\[harness] smoke ok: 2$");

    @Override
    public String name() {
        return "skript-smoke";
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
        try (ServerProcess server = context.server()
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/smoke.sk", context.script("smoke.sk"))
                .start()) {
            server.await(SMOKE_OK, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int exitCode = server.stop();
            if (exitCode != 0) {
                throw new HarnessFailure("Server exited with code " + exitCode + ". See " + server.logFile());
            }
            context.summary("skript", context.referenceSkript().version());
        }
    }
}
