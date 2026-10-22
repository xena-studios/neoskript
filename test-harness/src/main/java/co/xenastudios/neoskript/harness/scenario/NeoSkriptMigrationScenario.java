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
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Replacing Skript.jar with NeoSkript.jar just works: NeoSkript copies the scripts from
 * {@code plugins/Skript} on its first start and runs them, leaving Skript's folder as it was.
 */
public final class NeoSkriptMigrationScenario implements Scenario {

    private static final Pattern COPIED = Pattern.compile("\\[NeoSkript] Copied scripts from plugins/Skript \\(1 script\\)");

    @Override
    public String name() {
        return "neoskript-skript-migration";
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
        var builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/Skript/scripts/hello.sk", context.script("neoskript-hello.sk"));
        try (ServerProcess server = builder.start()) {
            server.await(COPIED, Duration.ofMinutes(5));
            server.await(NeoSkriptRunScenario.LOADED, Duration.ofMinutes(5));
            for (Pattern line : NeoSkriptRunScenario.OUTPUT) {
                server.await(line, Duration.ofMinutes(5));
            }
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            if (!server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR).isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors. See " + server.logFile());
            }
            if (!Files.exists(builder.runDir().resolve("plugins/Skript/scripts/hello.sk"))) {
                throw new HarnessFailure("The script was moved out of plugins/Skript instead of copied");
            }
        }
    }
}
