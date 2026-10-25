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
 * its last version (D12), so other scripts calling its functions are unaffected. Then Skript's other
 * subcommands, with Skript's messages: disable, list, enable, reloading one script and a folder, and the
 * errors for missing and disabled scripts.
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

    private static final String C = "on load:\n\tsend \"[harness] reload c loaded\" to console\n"
            + "on unload:\n\tsend \"[harness] reload c unloaded\" to console\n";

    private static final String D = "on load:\n\tsend \"[harness] reload d loaded\" to console\n";

    @Override
    public void run(ScenarioContext context) throws Exception {
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/a.sk", a("1", ""))
                .file("plugins/NeoSkript/scripts/b.sk", B)
                .file("plugins/NeoSkript/scripts/c.sk", C)
                .file("plugins/NeoSkript/scripts/sub/d.sk", D);
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

            subcommands(server, scripts);
            server.stop();
        }
    }

    /** Skript's disable, list, enable and reload of one script and a folder, from the console. */
    private static void subcommands(ServerProcess server, Path scripts) throws Exception {
        int mark = server.mark();
        server.command("sk disable c");
        server.await(line("[harness] reload c unloaded"), mark, WAIT);
        server.await(skript("Successfully disabled c.sk."), mark, WAIT);
        if (!Files.exists(scripts.resolve("-c.sk")) || Files.exists(scripts.resolve("c.sk"))) {
            throw new HarnessFailure("/sk disable c didn't rename c.sk to -c.sk. See " + server.logFile());
        }

        mark = server.mark();
        server.command("sk list");
        server.await(skript("Enabled scripts:"), mark, WAIT);
        server.await(skript("  a.sk"), mark, WAIT);
        server.await(skript("  sub/d.sk"), mark, WAIT);
        server.await(skript("Disabled scripts:"), mark, WAIT);
        server.await(skript("  -c.sk"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload c");
        server.await(skript("c.sk is currently disabled. Use /skript enable c to enable it."), mark, WAIT);

        mark = server.mark();
        server.command("sk enable c");
        server.await(skript("Enabling c.sk..."), mark, WAIT);
        server.await(line("[harness] reload c loaded"), mark, WAIT);
        server.await(skript("Successfully enabled & parsed c.sk."), mark, WAIT);
        if (!Files.exists(scripts.resolve("c.sk"))) {
            throw new HarnessFailure("/sk enable c didn't rename -c.sk back to c.sk. See " + server.logFile());
        }

        mark = server.mark();
        server.command("sk enable c");
        server.await(skript("c.sk is already enabled! Use /skript reload c to reload it if it was changed."), mark, WAIT);

        mark = server.mark();
        server.command("sk reload c");
        server.await(skript("Reloading c.sk..."), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[Skript] Successfully reloaded c.sk. (") + "\\d+ms\\)$"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload sub/");
        server.await(skript("Reloading all scripts in sub..."), mark, WAIT);
        server.await(line("[harness] reload d loaded"), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[Skript] Successfully reloaded 1 script in sub. (") + "\\d+ms\\)$"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload missing");
        server.await(skript("Can't find the script 'missing' in the scripts folder!"), mark, WAIT);
        server.command("sk reload missing/");
        server.await(skript("Can't find the folder 'missing/' in the scripts folder!"), mark, WAIT);

        // A folder: disabled, then enabled again with everything else.
        mark = server.mark();
        server.command("sk disable sub/");
        server.await(skript("Successfully disabled 1 script(s) in sub."), mark, WAIT);
        if (!Files.exists(scripts.resolve("sub/-d.sk"))) {
            throw new HarnessFailure("/sk disable sub/ didn't rename sub/d.sk to sub/-d.sk. See " + server.logFile());
        }
        mark = server.mark();
        server.command("sk enable all");
        server.await(skript("Enabling all disabled scripts..."), mark, WAIT);
        server.await(line("[harness] reload d loaded"), mark, WAIT);
        server.await(skript("Successfully enabled & parsed all previously disabled scripts."), mark, WAIT);

        mark = server.mark();
        server.command("sk reload config");
        server.await(skript("Reloading the main configuration..."), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[Skript] Successfully reloaded the main configuration. (") + "\\d+ms\\)$"),
                mark, WAIT);

        mark = server.mark();
        server.command("sk reload");
        server.await(line("Usage: /skript reload ..."), mark, WAIT);
        server.await(line("   last - Reloads the last script that was reloaded"), mark, WAIT);
        server.command("sk help");
        server.await(line("   list/show - Lists all enabled and disabled scripts"), mark, WAIT);
        server.command("sk info");
        server.await(Pattern.compile(Pattern.quote("[Skript] Server Version: ") + ".+"), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[Skript] NeoSkript Version: ") + ".+"), mark, WAIT);

        // Restore writes the loaded scripts to a new folder in dump.
        mark = server.mark();
        server.command("sk restore");
        server.await(skript("Restoring scripts..."), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[Skript] Restored all scripts and saved them inside the ") + ".*dump.* directory$"),
                mark, WAIT);
        Path dump = scripts.resolveSibling("dump");
        try (var folders = Files.list(dump)) {
            Path folder = folders.findFirst().orElseThrow(() -> new HarnessFailure("/sk restore wrote no folder in " + dump));
            if (!Files.readString(folder.resolve("c.sk")).equals(C) || !Files.exists(folder.resolve("sub/d.sk"))) {
                throw new HarnessFailure("/sk restore didn't write the loaded scripts to " + folder);
            }
        }
    }

    /** A message from /sk, after Skript's prefix. */
    private static Pattern skript(String text) {
        return line("[Skript] " + text);
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
