/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
 * {@code plugins/Skript} on its first start and runs them, with Skript's alias files and settings, leaving Skript's
 * folder as it was.
 */
public final class NeoSkriptMigrationScenario implements Scenario {

    private static final Pattern COPIED = Pattern.compile(
            "\\[NeoSkript] Copied scripts and aliases from plugins/Skript \\(2 scripts\\)");
    private static final Pattern ALIAS_USED = Pattern.compile("\\[harness] migrated alias: 2 rubys$");
    private static final Pattern DEFAULT_ALIASES = Pattern.compile(
            "\\[NeoSkript] Skript's default aliases are not loaded, as in Skript's config\\.sk\\.");
    private static final Pattern EFFECT_COMMANDS = Pattern.compile("\\[NeoSkript] Effect commands are set up as in Skript's config\\.sk\\.");

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
                .file("plugins/Skript/scripts/hello.sk", context.script("neoskript-hello.sk"))
                // An alias file of the server's own, and a script that uses it.
                .file("plugins/Skript/aliases/gems.sk", "gems:\n\truby¦s = minecraft:red_dye\n")
                .file("plugins/Skript/scripts/gems.sk", "on load:\n\tset {_gems} to 2 rubys\n"
                        + "\tsend \"[harness] migrated alias: %{_gems}%\" to console\n")
                // Skript's settings that NeoSkript has too, copied to its config.yml.
                .file("plugins/Skript/config.sk", "enable effect commands: true\neffect command token: ?\n"
                        + "use player UUIDs in variable names: false\nload default aliases: false\n");
        try (ServerProcess server = builder.start()) {
            server.await(COPIED, Duration.ofMinutes(5));
            server.await(DEFAULT_ALIASES, Duration.ofMinutes(5));
            server.await(EFFECT_COMMANDS, Duration.ofMinutes(5));
            server.await(ALIAS_USED, Duration.ofMinutes(5));
            server.await(Pattern.compile("\\[NeoSkript] Loaded 2 scripts with 2 triggers in \\d+ ms$"), Duration.ofMinutes(5));
            for (Pattern line : NeoSkriptRunScenario.OUTPUT) {
                server.await(line, Duration.ofMinutes(5));
            }
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
            if (!server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR).isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors. See " + server.logFile());
            }
            String config = Files.readString(builder.runDir().resolve("plugins/NeoSkript/config.yml"));
            // The token in either quotes: a current file is changed through Bukkit, which keeps its comments.
            for (String setting : new String[] {"^  enabled: true$", "^  token: [\"']\\?[\"']$",
                    "^  player-uuids-in-names: false$", "^  load-defaults: false$", "^config-version: 3$"}) {
                if (!Pattern.compile(setting, Pattern.MULTILINE).matcher(config).find()) {
                    throw new HarnessFailure("config.yml doesn't have " + setting + " from Skript's config.sk:\n" + config);
                }
            }
            if (!Files.exists(builder.runDir().resolve("plugins/NeoSkript/aliases/gems.sk"))
                    || !Files.exists(builder.runDir().resolve("plugins/Skript/aliases/gems.sk"))) {
                throw new HarnessFailure("Skript's alias file wasn't copied to plugins/NeoSkript/aliases");
            }
            if (!Files.exists(builder.runDir().resolve("plugins/Skript/scripts/hello.sk"))) {
                throw new HarnessFailure("The script was moved out of plugins/Skript instead of copied");
            }
        }
    }
}
