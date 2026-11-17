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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoSkriptGui (addons/gui) against the addon it replaces: the same scripts on Skript 2.17.0 with skript-gui 1.4.0
 * (pinned by SHA-256) and on NeoSkript with ours, with the harness bot's player clicking. What the scripts print, the
 * screens the player is shown, and what each inventory holds after every way of trying to take a locked item
 * (left, right, shift, double-click, number keys, the offhand key, dropping, dragging, shift-clicking into the GUI)
 * must be the same, as must the errors for lines skript-gui refuses. Skript only runs on Paper; on Folia, NeoSkript
 * must print what Skript does on Paper.
 *
 * <p>NeoSkript only: reloading the script with a GUI open closes it, and with skript-gui's jar in {@code plugins/}
 * (which Paper loads, and which disables itself without Skript), ours loads and says to remove it.
 */
public final class NeoSkriptGuiScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final String ADDON = "NeoSkriptGui";

    static final NeoSkriptHooksScenario.PinnedPlugin ORIGINAL = new NeoSkriptHooksScenario.PinnedPlugin(
            "skript-gui-1.4.0.jar",
            "https://github.com/APickledWalrus/skript-gui/releases/download/1.4.0/skript-gui-1.4.0.jar",
            "2e59842bf5a01d38fb546027b223aeb248a0e3d5abef007e2feb39cda9d61b16");

    private static final Pattern HARNESS = Pattern.compile("(\\[harness] gui .*)$");
    private static final Pattern BOT = Pattern.compile("(\\[bot:Alice] .*)$");
    private static final Pattern ERROR_AT = Pattern.compile("Line (\\d+): \\(gui-errors\\.sk\\)$");

    @Override
    public String name() {
        return "neoskript-gui";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The harness bot speaks 26.x.
        return environment.minecraftVersion().startsWith("26.");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        boolean folia = context.environment().platform() == Environment.Platform.FOLIA;
        List<String> skript = context.environment().skriptReference() ? play(context, runs.resolve("skript"), false)
                : null;
        List<String> neoskript = play(context, runs.resolve("neoskript"), true);
        Files.createDirectories(runs);
        Files.write(runs.resolve("neoskript-transcript.txt"), neoskript);
        if (skript != null) {
            Files.write(runs.resolve("skript-transcript.txt"), skript);
        }
        context.summary("lines", neoskript.size());
        List<String> expected = skript != null ? skript : expected();
        if (skript != null && !skript.equals(expected())) {
            throw new HarnessFailure("Skript with skript-gui didn't print what this scenario expects: the expectations"
                    + " are what's wrong. See " + runs.resolve("skript-transcript.txt"));
        }
        if (!neoskript.equals(expected)) {
            throw new HarnessFailure("NeoSkript with " + ADDON + " did something else than "
                    + (skript != null ? "Skript with skript-gui" : "expected") + ". Compare "
                    + runs.resolve("neoskript-transcript.txt") + " with "
                    + (skript != null ? runs.resolve("skript-transcript.txt") : "the expected lines in "
                            + getClass().getSimpleName()));
        }
        context.summary("matches Skript", skript != null);
        context.summary("folia", folia);
    }

    private static List<String> play(ScenarioContext context, Path runDir, boolean neoskript) throws Exception {
        String folder = neoskript ? "NeoSkript" : "Skript";
        ServerBuilder builder = context.server(runDir)
                .plugin(neoskript ? context.neoskriptJar() : context.skriptJar())
                .plugin(context.botJar())
                // On NeoSkript too: it can't run there, and ours says to remove it (D21).
                .plugin(context.download(ORIGINAL.url(), ORIGINAL.sha256(), ORIGINAL.fileName()))
                .file("plugins/" + folder + "/scripts/gui.sk", context.script("gui.sk"))
                .file("plugins/" + folder + "/scripts/gui-errors.sk", context.script("gui-errors.sk"))
                // The bot runs a command after each click: not spam.
                .file("spigot.yml", "commands:\n  enable-spam-exclusions: true\n  spam-exclusions:\n  - /nsk-gui-sync\n  - /nsk-gui-state\n");
        if (neoskript) {
            builder.plugin(addonJar(context));
        }
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] gui loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            if (neoskript) {
                server.await(Pattern.compile(Pattern.quote("plugins/" + ORIGINAL.fileName() + " is skript-gui, which"
                        + " needs Skript, so it doesn't load with NeoSkript. " + ADDON + " replaces it:")), 0, WAIT);
            }
            server.command("difficulty peaceful");
            int mark = server.mark();
            server.command("bot join Alice");
            server.await(line("[bot] Alice joined"), mark, WAIT);
            server.command("clear Alice");
            server.command("give Alice cobblestone 5");
            Steps steps = new Steps(server, mark);
            steps.sync();

            // The shop: a locked diamond with code, removable dirt, locked stone.
            server.command("nsk-gui Alice shop");
            server.await(line("[bot:Alice] [screen] Shop"), mark, WAIT);
            steps.sync();
            for (String how : List.of("left", "right", "shift", "double", "1", "offhand", "drop", "middle")) {
                steps.click(0, how);
            }
            steps.click(2, "left");
            steps.click(4, "left");
            // Cobblestone from the hotbar (raw slot 54 under a 3-row chest) onto the cursor, dragged over GUI slots.
            steps.click(54, "left");
            steps.drag(2, 3);
            steps.click(54, "left");
            // Shift-clicked into the GUI, and number keys over a locked slot from the player's side.
            steps.click(54, "shift");
            steps.click(2, "1");
            // The removable dirt can be taken.
            steps.click(1, "left");
            steps.click(55, "left");
            steps.click(8, "left");
            steps.state();
            steps.close();

            // A GUI whose closing is cancelled opens again, until its code lets it close.
            server.command("nsk-gui Alice sticky");
            server.await(line("[bot:Alice] [screen] Sticky"), mark, WAIT);
            steps.sync();
            int sticky = server.mark();
            steps.close();
            server.await(line("[harness] gui sticky close: <none>"), sticky, WAIT);
            server.await(line("[bot:Alice] [screen] Sticky"), sticky, WAIT);
            steps.sync();
            steps.click(0, "left");
            server.await(line("[harness] gui sticky close: true"), sticky, WAIT);
            server.await(line("[bot:Alice] [screen closed]"), sticky, WAIT);
            steps.sync();

            // A global GUI with a layout.
            server.command("nsk-gui Alice layout");
            server.await(line("[bot:Alice] [screen] Layout Two"), mark, WAIT);
            steps.sync();
            steps.click(10, "left");
            steps.click(0, "left");
            steps.click(4, "shift");
            steps.state();
            steps.close();
            server.command("nsk-gui Alice pages");
            steps.sync();

            // What's compared ends here: the rest is NeoSkript's own.
            int compared = server.mark();
            if (neoskript) {
                // Reloading the script with its GUI open closes it: its code is the old version.
                server.command("nsk-gui Alice shop");
                server.await(line("[bot:Alice] [screen] Shop"), mark, WAIT);
                steps.sync();
                int reload = server.mark();
                server.command("sk reload gui.sk");
                server.await(line("[bot:Alice] [screen closed]"), reload, WAIT);
            }
            server.command("bot quit Alice");
            server.await(line("[bot] Alice left"), mark, WAIT);
            server.stop();

            if (neoskript) {
                List<String> problems = server.linesMatching(Pattern.compile("(?i)\\[" + ADDON + "].*(exception|error)"
                        + "|Error occurred while enabling " + ADDON + "|Could not load .*" + ADDON
                        + "|" + NeoSkriptRegionsScenario.THREAD_VIOLATION.pattern()));
                if (!problems.isEmpty()) {
                    throw new HarnessFailure(ADDON + " logged errors: " + problems + ". See " + server.logFile());
                }
            }
            List<String> transcript = new ArrayList<>();
            List<String> lines = server.lines(mark).subList(0, compared - mark);
            for (Pattern pattern : List.of(HARNESS, BOT)) {
                for (String text : lines) {
                    Matcher matcher = pattern.matcher(text);
                    // Not the bot's syncs, which only time the steps.
                    if (matcher.find() && !matcher.group(1).startsWith("[harness] gui sync ")) {
                        transcript.add(matcher.group(1));
                    }
                }
            }
            transcript.addAll(NeoSkriptPlaceholdersScenario.errors(server.lines(0), ERROR_AT, "[harness] gui error "));
            return transcript;
        }
    }

    /** Clicks and their syncs: each action is followed by a command from the bot, whose line says it's handled. */
    private static final class Steps {
        private final ServerProcess server;
        private final int mark;
        private int count;

        Steps(ServerProcess server, int mark) {
            this.server = server;
            this.mark = mark;
        }

        void click(int slot, String how) throws Exception {
            server.command("bot click Alice " + slot + " " + how);
            sync();
        }

        void drag(int... slots) throws Exception {
            StringBuilder command = new StringBuilder("bot drag Alice");
            for (int slot : slots) {
                command.append(' ').append(slot);
            }
            server.command(command.toString());
            sync();
        }

        void close() throws Exception {
            server.command("bot closescreen Alice");
            sync();
        }

        void state() throws Exception {
            int before = server.mark();
            // Run by Alice, so on Folia it's on her region's thread, which may read her inventory.
            server.command("bot command Alice nsk-gui-state Alice");
            server.await(line("[harness] gui state done"), before, WAIT);
        }

        void sync() throws Exception {
            count++;
            server.command("bot command Alice nsk-gui-sync " + count);
            server.await(line("[harness] gui sync " + count), mark, WAIT);
        }
    }

    private static Path addonJar(ScenarioContext context) throws IOException {
        for (Path jar : context.officialAddonJars()) {
            if (ADDON.equals(NeoSkriptOfficialAddonsScenario.properties(jar).getProperty("name"))) {
                return jar;
            }
        }
        throw new HarnessFailure("The harness wasn't given " + ADDON + "'s jar (addons/gui)");
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }

    /** What Skript with skript-gui does on Paper, and NeoSkript must do on Paper and Folia. */
    static List<String> expected() {
        return List.of(
                "[harness] gui made: chest inventory gui named Shop with 3 rows and layout \"ABCDEFGHIJKLMNOPQRSTUVWXYZ[\"",
                "[harness] gui next: D",
                "[harness] gui open: Alice",
                "[harness] gui click: 0 0 A left mouse button Alice 1",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container pickup all -1",
                "[harness] gui click: 0 0 A right mouse button Alice 2",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container pickup half -1",
                "[harness] gui click: 0 0 A left mouse button with shift Alice 3",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container instant move -1",
                "[harness] gui click: 0 0 A double click using mouse Alice 4",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container nothing -1",
                "[harness] gui click: 0 0 A number key Alice 5",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container swap with hotbar 0",
                "[harness] gui click: 0 0 A swap offhand Alice 6",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container swap with hotbar -1",
                "[harness] gui click: 0 0 A drop key Alice 7",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container drop one from slot -1",
                "[harness] gui click: 0 0 A middle mouse button Alice 8",
                "[harness] gui click item: diamond named Buy",
                "[harness] gui click values: container nothing -1",
                "[harness] gui edit: editing",
                "[harness] gui next slot: D",
                "[harness] gui has gui: true",
                "[harness] gui player diamonds: 0",
                "[harness] gui player stone: 0",
                "[harness] gui player dirt: 1",
                "[harness] gui player cobblestone: 5",
                "[harness] gui player paper: 0",
                "[harness] gui player glass: 0",
                "[harness] gui cursor: 0 airs",
                "[harness] gui gui diamonds: 1",
                "[harness] gui gui stone: 1",
                "[harness] gui gui dirt: 0",
                "[harness] gui gui cobblestone: 0",
                "[harness] gui gui paper: 0",
                "[harness] gui gui glass: 0",
                "[harness] gui state done",
                "[harness] gui close: Alice",
                "[harness] gui sticky close: <none>",
                "[harness] gui sticky click: 0",
                "[harness] gui sticky player: Alice",
                "[harness] gui sticky close: true",
                "[harness] gui layout: xxxxxxxxxx-------xxxxxxxxxx",
                "[harness] gui global: chest inventory gui named Layout with 3 rows and layout \"xxxxxxxxxx-------xxxxxxxxxx\"",
                "[harness] gui id: layout",
                "[harness] gui locked: true",
                "[harness] gui unlocked: true",
                "[harness] gui name: Layout Two",
                "[harness] gui has gui: true",
                "[harness] gui player diamonds: 0",
                "[harness] gui player stone: 0",
                "[harness] gui player dirt: 1",
                "[harness] gui player cobblestone: 5",
                "[harness] gui player paper: 0",
                "[harness] gui player glass: 0",
                "[harness] gui cursor: paper",
                "[harness] gui gui diamonds: 0",
                "[harness] gui gui stone: 0",
                "[harness] gui gui dirt: 0",
                "[harness] gui gui cobblestone: 0",
                "[harness] gui gui paper: 6",
                "[harness] gui gui glass: 20",
                "[harness] gui state done",
                "[harness] gui page 2: 4, 5 and 6",
                "[harness] gui pages 1 and 3: 1, 2, 3 and 7",
                "[bot:Alice] Alice joined the game",
                "[bot:Alice] Alice has made the advancement [Stone Age]",
                "[bot:Alice] [screen] Shop",
                "[bot:Alice] [screen] Sticky",
                "[bot:Alice] [screen] Sticky",
                "[bot:Alice] [screen closed]",
                "[bot:Alice] [screen] Layout Two",
                "[harness] gui error 5: You can't make a GUI slot outside of a GUI section.",
                "[harness] gui error 6: You can't use 'the gui slot' outside of a GUI section.",
                "[harness] gui error 7: GUI open/close sections can only be put within GUI creation or editing sections.",
                "[harness] gui error 9: Canceling or uncanceling the closing of a GUI can only be done within a GUI close section.",
                "[harness] gui error 10: The 'next gui slot' expression must have a GUI specified unless it is used in a GUI section.",
                "[harness] gui error 11: You cannot paginate a single value.",
                "[harness] gui error 15: You can't use 'the gui slot' in a GUI creation section.",
                "[harness] gui error 17: Canceling or uncanceling the closing of a GUI can only be done within a GUI close section.",
                "[harness] gui error 19: You can't use 'the gui click type' in a GUI open/close section.");
    }
}
