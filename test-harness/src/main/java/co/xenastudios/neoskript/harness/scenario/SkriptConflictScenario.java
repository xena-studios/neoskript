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

/** With Skript also installed, NeoSkript refuses to run and Skript keeps working (D10). */
public final class SkriptConflictScenario implements Scenario {

    private static final Pattern REFUSED = Pattern.compile("Skript is installed\\. NeoSkript replaces Skript");
    private static final Pattern NEOSKRIPT_ENABLED = Pattern.compile("NeoSkript \\S+ enabled on");

    @Override
    public String name() {
        return "neoskript-skript-conflict";
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
                .plugin(context.neoskriptJar())
                .plugin(context.skriptJar())
                .file("plugins/Skript/scripts/smoke.sk", context.script("smoke.sk"))
                .start()) {
            server.await(REFUSED, Duration.ofMinutes(5));
            server.await(SkriptSmokeScenario.SMOKE_OK, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            if (!server.linesMatching(NEOSKRIPT_ENABLED).isEmpty()) {
                throw new HarnessFailure("NeoSkript enabled even though Skript is installed");
            }
        }
    }
}
