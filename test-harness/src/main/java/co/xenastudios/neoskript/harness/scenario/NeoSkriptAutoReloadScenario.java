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
import java.util.regex.Pattern;

/**
 * Skript's {@code auto reload} structure: a script reloads itself, with Skript's messages, when its file
 * changes. A version with errors keeps the old one running (and watching); a version without the
 * structure stops watching, and {@code /sk reload} doesn't leave a second watcher behind.
 */
public final class NeoSkriptAutoReloadScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(1);
    /** Longer than a few of Skript's 2 second checks. */
    private static final long QUIET_MILLIS = 7_000;

    @Override
    public String name() {
        return "neoskript-auto-reload";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    private static String script(String version, String header, String extra) {
        return header + "on load:\n\tsend \"[harness] auto: " + version + "\" to console\n" + extra;
    }

    private static final String AUTO = "auto reload:\n\tpermission: skript.reloadnotify\n";

    @Override
    public void run(ScenarioContext context) throws Exception {
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/auto.sk", script("1", "auto reload\n", ""));
        Path file = builder.runDir().resolve("plugins/NeoSkript/scripts/auto.sk");
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] auto: 1"), WAIT);
            server.await(ServerProcess.DONE, WAIT);

            // An edit: the script reloads itself.
            int mark = server.mark();
            Files.writeString(file, script("2", AUTO, ""));
            server.await(Pattern.compile("\\[Skript] Automatically reloading auto\\.sk\\.\\.\\.$"), mark, WAIT);
            server.await(line("[harness] auto: 2"), mark, WAIT);
            server.await(Pattern.compile("\\[Skript] Successfully automatically reloaded auto\\.sk\\. \\(\\d+ms\\)$"), mark, WAIT);

            // After /sk reload, an edit reloads it once: the old watcher is gone.
            server.command("sk reload scripts");
            server.await(Pattern.compile("Successfully reloaded all scripts\\."), mark, WAIT);
            Thread.sleep(1_000);
            mark = server.mark();
            Files.writeString(file, script("3", AUTO, ""));
            server.await(line("[harness] auto: 3"), mark, WAIT);
            Thread.sleep(QUIET_MILLIS);
            if (server.linesMatching(Pattern.compile("Automatically reloading auto\\.sk")).size() != 2) {
                throw new HarnessFailure("An edit after /sk reload reloaded the script more than once. See " + server.logFile());
            }

            // An edit with an error: the error and Skript's summary, and version 3 keeps running.
            mark = server.mark();
            Files.writeString(file, script("4", AUTO, "\tthis is not valid syntax\n"));
            server.await(Pattern.compile("Line 5: \\(auto\\.sk\\)$"), mark, WAIT);
            server.await(Pattern.compile("\\[Skript] Encountered 1 error while automatically reloading auto\\.sk! \\(\\d+ms\\)$"),
                    mark, WAIT);
            if (!server.linesMatching(line("[harness] auto: 4")).isEmpty()) {
                throw new HarnessFailure("The version with an error was loaded anyway. See " + server.logFile());
            }

            // Version 3 still watches its file.
            mark = server.mark();
            Files.writeString(file, script("5", "", ""));
            server.await(line("[harness] auto: 5"), mark, WAIT);

            // Version 5 has no auto reload, so edits are no longer picked up.
            Thread.sleep(1_000);
            Files.writeString(file, script("6", "", ""));
            Thread.sleep(QUIET_MILLIS);
            if (!server.linesMatching(line("[harness] auto: 6")).isEmpty()) {
                throw new HarnessFailure("A script without auto reload was reloaded automatically. See " + server.logFile());
            }
            server.stop();
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
