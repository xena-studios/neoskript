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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /sk reload}: changed scripts switch to their new version, and a script with errors keeps running
 * its last version (D12), so other scripts calling its functions are unaffected.
 */
public final class NeoSkriptReloadScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-reload";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    private static String a(String version, String extra) {
        return "function version() :: text:\n\treturn \"" + version + "\"\non load:\n\tsend \"[harness] reload a: %version()%\" to console\n"
                + extra;
    }

    private static final String B = "on load:\n\tsend \"[harness] reload b sees: %version()%\" to console\n";

    @Override
    public void run(ScenarioContext context) throws Exception {
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/a.sk", a("1", ""))
                .file("plugins/NeoSkript/scripts/b.sk", B);
        Path scripts = builder.runDir().resolve("plugins/NeoSkript/scripts");
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] reload a: 1"), WAIT);
            server.await(line("[harness] reload b sees: 1"), WAIT);
            server.await(ServerProcess.DONE, WAIT);

            // A clean reload: both scripts switch to the new version.
            Files.writeString(scripts.resolve("a.sk"), a("2", ""));
            int mark = server.mark();
            server.command("sk reload scripts");
            Matcher reloaded = server.await(Pattern.compile("Successfully reloaded all scripts\\. \\((\\d+)ms\\)"), mark, WAIT);
            server.await(line("[harness] reload a: 2"), mark, WAIT);
            server.await(line("[harness] reload b sees: 2"), mark, WAIT);
            context.summary("reloadMs", Integer.parseInt(reloaded.group(1)));

            // A reload with an error: a.sk keeps version 2, which b.sk still calls.
            Files.writeString(scripts.resolve("a.sk"), a("3", "\tthis is not valid syntax\n"));
            mark = server.mark();
            server.command("sk reload scripts");
            server.await(Pattern.compile("Encountered 1 error while reloading all scripts!"), mark, WAIT);
            server.await(line("[harness] reload b sees: 2"), mark, WAIT);
            if (!server.linesMatching(line("[harness] reload a: 3")).isEmpty()) {
                throw new HarnessFailure("The script with an error was reloaded anyway. See " + server.logFile());
            }

            // Reloading one script.
            Files.writeString(scripts.resolve("b.sk"), B.replace("b sees", "b2 sees"));
            mark = server.mark();
            server.command("sk reload b");
            server.await(Pattern.compile("Successfully reloaded b\\.sk\\."), mark, WAIT);
            server.await(line("[harness] reload b2 sees: 2"), mark, WAIT);
            server.stop();
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
