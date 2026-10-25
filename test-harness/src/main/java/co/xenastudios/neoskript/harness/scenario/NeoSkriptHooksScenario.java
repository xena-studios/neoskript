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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Skript's plugin hooks against the real plugins rather than fakes: WorldGuard regions (with the WorldEdit it
 * needs), and Vault with EssentialsX for money and LuckPerms for groups. The plugins are pinned to exact builds
 * and SHA-256 hashes, as servers are (D17).
 *
 * <p>The regions are written into WorldGuard's {@code regions.yml} before the server starts, since defining one
 * with commands needs a player's selection. The script checks regions and money as it loads; groups are checked
 * by a script command once the harness has created a LuckPerms group for them.
 *
 * <p>This is in the baseline group, not smoke: it downloads about 16 MB of plugins and starts five of them, which
 * every CI build shouldn't pay for.
 */
public final class NeoSkriptHooksScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);

    /** A plugin jar pinned to one build. */
    record PinnedPlugin(String fileName, String url, String sha256) {}

    static final List<PinnedPlugin> PLUGINS = List.of(
            new PinnedPlugin("worldedit-bukkit-7.4.6.jar",
                    "https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar",
                    "e0ce67d870ad1fef8a40dd9d7926360caf3bb44722438545d6b20b55f82a0c91"),
            new PinnedPlugin("worldguard-bukkit-7.0.20.jar",
                    "https://cdn.modrinth.com/data/DKY9btbd/versions/9YdeGXiK/worldguard-bukkit-7.0.20.jar",
                    "8768a49bcbecbb7a413ad134ee72d9871cdf967b7d938e6b96bb0ca72bb1467b"),
            new PinnedPlugin("Vault-1.7.3.jar",
                    "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar",
                    "a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d"),
            new PinnedPlugin("EssentialsX-2.22.0.jar",
                    "https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar",
                    "bda4685105977fca2e209820a9f0ad24275bd103390a03236f38e59bfdac58e6"),
            new PinnedPlugin("LuckPerms-Bukkit-5.5.71.jar",
                    "https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar",
                    "49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d"));

    @Override
    public String name() {
        return "neoskript-hooks";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The pinned plugin builds are for 26.x, and Folia runs neither WorldGuard nor EssentialsX.
        return environment.platform() == Environment.Platform.PAPER && environment.minecraftVersion().startsWith("26.");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        ServerBuilder builder = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/hooks.sk", context.script("neoskript-hooks.sk"))
                .file("plugins/WorldGuard/worlds/world/regions.yml", regions());
        for (PinnedPlugin plugin : PLUGINS) {
            builder.plugin(context.download(plugin.url(), plugin.sha256(), plugin.fileName()));
        }
        try (ServerProcess server = builder.start()) {
            server.await(Pattern.compile("\\[harness] hooks loaded$"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();
            server.command("lp creategroup vip");
            server.await(Pattern.compile("vip was successfully created", Pattern.CASE_INSENSITIVE), mark, WAIT);
            server.command("nsk-hooks-groups");
            server.await(Pattern.compile("\\[harness] hooks groups done$"), mark, WAIT);
            server.stop();
            List<String> problems = server.linesMatching(Pattern.compile(
                    "\\[harness] hooks FAIL|\\[NeoSkript] Line \\d+|Could not hook into"));
            if (!problems.isEmpty()) {
                throw new HarnessFailure("Hooks didn't work: " + problems + ". See " + server.logFile());
            }
            context.summary("checks", server.linesMatching(Pattern.compile("\\[harness] hooks ok")).size());
        }
    }

    /**
     * WorldGuard's regions file for the overworld: {@code shop}, with Alice as a member and Bob as its owner, and
     * {@code booth}, a small region inside it.
     */
    static String regions() {
        return """
                regions:
                    shop:
                        type: cuboid
                        min: {x: 0, y: -64, z: 0}
                        max: {x: 15, y: 100, z: 15}
                        priority: 0
                        flags: {}
                        members:
                            unique-ids: [%s]
                        owners:
                            unique-ids: [%s]
                    booth:
                        type: cuboid
                        min: {x: 2, y: 0, z: 2}
                        max: {x: 3, y: 1, z: 4}
                        priority: 1
                        flags: {}
                        members: {}
                        owners: {}
                """.formatted(offlineId("Alice"), offlineId("Bob"));
    }

    /** The UUID an offline-mode server gives a player, as {@code offlineplayer("<name>")} finds it. */
    static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
