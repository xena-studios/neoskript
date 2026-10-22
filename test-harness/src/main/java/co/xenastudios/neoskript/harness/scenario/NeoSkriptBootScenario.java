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
import java.util.regex.Pattern;

/** NeoSkript loads and enables on the server, and detects the right platform. */
public final class NeoSkriptBootScenario implements Scenario {

    /** NeoSkript failing to load, logging at error level, or logging an exception. */
    static final Pattern NEOSKRIPT_ERROR = Pattern.compile(
            "(Error occurred while enabling NeoSkript|Could not load .*NeoSkript|ERROR]: \\[NeoSkript]|\\[NeoSkript].*Exception)");

    @Override
    public String name() {
        return "neoskript-boot";
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
        Environment environment = context.environment();
        try (ServerProcess server = context.server().plugin(context.neoskriptJar()).start()) {
            Pattern enabled = Pattern.compile("NeoSkript \\S+ enabled on "
                    + environment.platform().displayName() + " " + Pattern.quote(environment.minecraftVersion()));
            server.await(enabled, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int exitCode = server.stop();
            List<String> errors = server.linesMatching(NEOSKRIPT_ERROR);
            if (!errors.isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors: " + errors);
            }
            if (exitCode != 0) {
                throw new HarnessFailure("Server exited with code " + exitCode + ". See " + server.logFile());
            }
        }
    }
}
