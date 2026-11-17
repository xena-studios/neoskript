/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
 * {@code /sk reload}: changed scripts switch to their new version, and a script with errors switches too,
 * without the lines that have them, as in Skript (D12): the rest of its trigger runs, and other scripts call its
 * new functions. Then Skript's other
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
            Matcher reloaded = server.await(Pattern.compile("\\[NeoSkript] Reloaded 4 scripts in (\\d+) ms\\.$"), mark, WAIT);
            server.await(line("[harness] reload a: 2"), mark, WAIT);
            server.await(line("[harness] reload b sees: 2"), mark, WAIT);
            context.summary("reloadMs", Integer.parseInt(reloaded.group(1)));

            // A reload with an error: as in Skript, a.sk switches to version 3 without the line, and the rest of its
            // trigger runs. b.sk calls the new version.
            Files.writeString(scripts.resolve("a.sk"), a("3",
                    "\tthis is not valid syntax\n\tsend \"[harness] reload a after the error\" to console\n"));
            mark = server.mark();
            server.command("sk reload scripts");
            // The report: the script, its error with the line number and the code, then what was done.
            server.await(line(" a.sk - 1 error"), mark, WAIT);
            server.await(Pattern.compile("   Line 5: .*this is not valid syntax.*$"), mark, WAIT);
            server.await(line("     this is not valid syntax"), mark, WAIT);
            server.await(Pattern.compile(Pattern.quote("[NeoSkript] Reloaded 4 scripts with 1 error in ") + "\\d+"
                    + Pattern.quote(" ms. What has errors is left out.") + "$"), mark, WAIT);
            server.await(line("[harness] reload a: 3"), mark, WAIT);
            server.await(line("[harness] reload a after the error"), mark, WAIT);
            server.await(line("[harness] reload b sees: 3"), mark, WAIT);

            // Reloading one script.
            Files.writeString(scripts.resolve("b.sk"), B.replace("b sees", "b2 sees"));
            mark = server.mark();
            server.command("sk reload b");
            server.await(timed("Reloaded b.sk"), mark, WAIT);
            server.await(line("[harness] reload b2 sees: 3"), mark, WAIT);

            subcommands(server, scripts);
            server.stop();
        }
    }

    /** Skript's disable, list, enable and reload of one script and a folder, from the console. */
    private static void subcommands(ServerProcess server, Path scripts) throws Exception {
        int mark = server.mark();
        server.command("sk disable c");
        server.await(line("[harness] reload c unloaded"), mark, WAIT);
        server.await(neo("Disabled c.sk."), mark, WAIT);
        if (!Files.exists(scripts.resolve("-c.sk")) || Files.exists(scripts.resolve("c.sk"))) {
            throw new HarnessFailure("/sk disable c didn't rename c.sk to -c.sk. See " + server.logFile());
        }

        mark = server.mark();
        server.command("sk list");
        server.await(neo("3 scripts enabled, 1 disabled."), mark, WAIT);
        server.await(line(" Enabled: a.sk, b.sk"), mark, WAIT);
        server.await(line("   sub/: d.sk"), mark, WAIT);
        server.await(line(" Disabled: -c.sk"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload c");
        server.await(neo("c.sk is disabled. /sk enable c enables it."), mark, WAIT);

        mark = server.mark();
        server.command("sk enable c");
        server.await(neo("Enabling c.sk..."), mark, WAIT);
        server.await(line("[harness] reload c loaded"), mark, WAIT);
        server.await(timed("Enabled c.sk"), mark, WAIT);
        if (!Files.exists(scripts.resolve("c.sk"))) {
            throw new HarnessFailure("/sk enable c didn't rename -c.sk back to c.sk. See " + server.logFile());
        }

        mark = server.mark();
        server.command("sk enable c");
        server.await(neo("c.sk is already enabled. /sk reload c reloads it."), mark, WAIT);

        mark = server.mark();
        server.command("sk reload c");
        server.await(neo("Reloading c.sk..."), mark, WAIT);
        server.await(timed("Reloaded c.sk"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload sub/");
        server.await(neo("Reloading the scripts in sub/..."), mark, WAIT);
        server.await(line("[harness] reload d loaded"), mark, WAIT);
        server.await(timed("Reloaded 1 script in sub/"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload missing");
        server.await(neo("There's no script missing in the scripts folder."), mark, WAIT);
        server.command("sk reload missing/");
        server.await(neo("There's no folder missing/ in the scripts folder."), mark, WAIT);

        // A folder: disabled, then enabled again with everything else.
        mark = server.mark();
        server.command("sk disable sub/");
        server.await(neo("Disabled 1 script in sub/."), mark, WAIT);
        if (!Files.exists(scripts.resolve("sub/-d.sk"))) {
            throw new HarnessFailure("/sk disable sub/ didn't rename sub/d.sk to sub/-d.sk. See " + server.logFile());
        }
        mark = server.mark();
        server.command("sk enable all");
        server.await(neo("Enabling all disabled scripts..."), mark, WAIT);
        server.await(line("[harness] reload d loaded"), mark, WAIT);
        server.await(timed("Enabled 1 script"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload config");
        server.await(neo("Reloading the config..."), mark, WAIT);
        server.await(timed("Reloaded the config"), mark, WAIT);

        mark = server.mark();
        server.command("sk reload");
        server.await(neo("/sk reload <all|scripts|config|aliases|last|script>"), mark, WAIT);
        server.await(line("   last - The script you reloaded last"), mark, WAIT);
        server.command("sk help");
        server.await(line(" /sk list (or show) - Lists the enabled and disabled scripts"), mark, WAIT);
        // The other names of /skript, as typed.
        server.command("ns help");
        server.await(line(" /ns reload <all|scripts|config|aliases|last|script> - Reloads scripts, the config, or both"),
                mark, WAIT);
        server.command("neoskript info");
        server.await(Pattern.compile(Pattern.quote("[NeoSkript] NeoSkript ") + "\\S+ on .+$"), mark, WAIT);
        server.await(line(" Scripts: 4 enabled, 0 disabled"), mark, WAIT);
        server.await(line(" Docs: https://neoskript.dev/docs/"), mark, WAIT);
        // The harness's jar is a development build, which never asks Modrinth.
        server.command("sk update");
        server.await(Pattern.compile(Pattern.quote("[NeoSkript] ") + "\\S+-SNAPSHOT is a development build, which doesn't"
                + " check for updates\\. Newer builds: https://neoskript\\.dev/download/$"), mark, WAIT);

        // Restore writes the loaded scripts to a new folder in dump.
        mark = server.mark();
        server.command("sk restore");
        server.await(neo("Restoring the loaded scripts..."), mark, WAIT);
        server.await(Pattern.compile(Pattern.quote("[NeoSkript] Restored 4 scripts to dump/") + ".+/\\.$"), mark, WAIT);
        Path dump = scripts.resolveSibling("dump");
        try (var folders = Files.list(dump)) {
            Path folder = folders.findFirst().orElseThrow(() -> new HarnessFailure("/sk restore wrote no folder in " + dump));
            if (!Files.readString(folder.resolve("c.sk")).equals(C) || !Files.exists(folder.resolve("sub/d.sk"))) {
                throw new HarnessFailure("/sk restore didn't write the loaded scripts to " + folder);
            }
        }
    }

    /** A message from /sk, after its prefix. */
    private static Pattern neo(String text) {
        return line("[NeoSkript] " + text);
    }

    /** A message from /sk saying what was done and how long it took: "Reloaded b.sk in 12 ms.". */
    private static Pattern timed(String done) {
        return Pattern.compile(Pattern.quote("[NeoSkript] " + done + " in ") + "\\d+ ms\\.$");
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
