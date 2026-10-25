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

/** The core language (variables, arithmetic, loops, conditionals, functions) on every server. */
public final class NeoSkriptLanguageScenario implements Scenario {

    private static final Pattern LOADED = Pattern.compile("\\[NeoSkript] Loaded .*$");

    static final List<String> EXPECTED = List.of(
            "arithmetic: 7, 5, 9, 2.5",
            "functions: 610, Hi Steve!, 2, 9",
            "math functions: 1, 90, 4, 6, 6, 550, 1412121600, 069a79f4-44e9-4726-a5be-fca90e38aaf5",
            "globals: 7",
            "lists: 3 1=c 3=b 4=d ",
            "while: 1, 2, 4 and 5");

    @Override
    public String name() {
        return "neoskript-language";
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
                .file("plugins/NeoSkript/scripts/language.sk", context.script("neoskript-language.sk"))
                .start()) {
            String loaded = server.await(LOADED, Duration.ofMinutes(5)).group();
            if (loaded.contains("error")) {
                throw new HarnessFailure("The script didn't load cleanly: " + loaded + ". See " + server.logFile());
            }
            for (String expected : EXPECTED) {
                server.await(Pattern.compile(Pattern.quote("[harness] lang " + expected) + "$"), Duration.ofMinutes(5));
            }
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
            if (!errors.isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
            }
        }
    }
}
