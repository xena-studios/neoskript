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
 * NeoSkriptPlaceholders (addons/placeholders) against the addon it replaces: the same scripts run on Skript 2.17.0
 * with skript-placeholders 1.7.1 and on NeoSkript with ours, both with PlaceholderAPI 2.12.3, all pinned by
 * SHA-256. What they print must be the same: placeholders registered by scripts and read back through
 * PlaceholderAPI, for nobody, offline and online players and two players (relational), the errors for lines
 * skript-placeholders refuses, and scripts reloading to other prefixes and to none, after which PlaceholderAPI must
 * have only the prefixes still in use.
 *
 * <p>Skript only runs on Paper; on Folia, NeoSkript's output must be what Skript's is on Paper, and a placeholder
 * read from a thread that doesn't own its player is a runtime error, not a read of another region's player. With
 * skript-placeholders' jar in {@code plugins/} too, which Paper loads and which disables itself without Skript,
 * ours still loads and says to remove it (D21).
 */
public final class NeoSkriptPlaceholdersScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final String ADDON = "NeoSkriptPlaceholders";

    static final NeoSkriptHooksScenario.PinnedPlugin PLACEHOLDER_API = new NeoSkriptHooksScenario.PinnedPlugin(
            "PlaceholderAPI-2.12.3.jar",
            "https://cdn.modrinth.com/data/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar",
            "fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437");
    static final NeoSkriptHooksScenario.PinnedPlugin ORIGINAL = new NeoSkriptHooksScenario.PinnedPlugin(
            "skript-placeholders-1.7.1.jar",
            "https://github.com/APickledWalrus/skript-placeholders/releases/download/1.7.1/skript-placeholders-1.7.1.jar",
            "39435e44c4f9cdb39515d48d73e02ccab4b32030a33dc186b9442999e56b13fe");

    /** What the comparison looks at: the scripts' lines and the errors in the errors script. */
    private static final Pattern TRANSCRIPT = Pattern.compile("(\\[harness] ph .*)$");
    /** The start of an error in a script, as Skript (and NeoSkript) logs it. */
    private static final Pattern ERROR_AT = Pattern.compile("Line (\\d+): \\(placeholders-errors\\.sk\\)$");

    @Override
    public String name() {
        return "neoskript-placeholders";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // PlaceholderAPI 2.12.3 and the harness bot are for 26.x.
        return environment.minecraftVersion().startsWith("26.");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        boolean folia = context.environment().platform() == Environment.Platform.FOLIA;
        List<String> skript = context.environment().skriptReference()
                ? play(context, runs.resolve("skript"), false, folia)
                : null;
        List<String> neoskript = play(context, runs.resolve("neoskript"), true, folia);
        context.summary("lines", neoskript.size());
        Files.createDirectories(runs);
        Files.write(runs.resolve("neoskript-transcript.txt"), neoskript);
        if (skript != null) {
            Files.write(runs.resolve("skript-transcript.txt"), skript);
        }
        // Skript's output on Paper is the spec; on Folia, NeoSkript's is checked against the same lines.
        List<String> expected = skript != null ? skript : expected(folia);
        if (skript != null && !skript.equals(expected(false))) {
            throw new HarnessFailure("Skript with skript-placeholders didn't print what this scenario expects: the"
                    + " expectations are what's wrong. See " + runs.resolve("skript-transcript.txt"));
        }
        if (!neoskript.equals(expected)) {
            throw new HarnessFailure("NeoSkript with " + ADDON + " printed something else than "
                    + (skript != null ? "Skript with skript-placeholders" : "expected") + ". Compare "
                    + runs.resolve("neoskript-transcript.txt") + " with "
                    + (skript != null ? runs.resolve("skript-transcript.txt") : "the expected lines in " + getClass().getSimpleName()));
        }
        context.summary("matches Skript", skript != null);
    }

    /** Runs the scripts on Skript or NeoSkript, and returns the transcript. */
    private static List<String> play(ScenarioContext context, Path runDir, boolean neoskript, boolean folia)
            throws Exception {
        String folder = neoskript ? "NeoSkript" : "Skript";
        ServerBuilder builder = context.server(runDir)
                .plugin(neoskript ? context.neoskriptJar() : context.skriptJar())
                .plugin(context.botJar())
                .plugin(download(context, PLACEHOLDER_API))
                // On NeoSkript, the original's jar too: it can't run, and ours says to remove it (D21).
                .plugin(download(context, ORIGINAL))
                .file("plugins/" + folder + "/scripts/placeholders.sk", context.script("placeholders.sk"))
                .file("plugins/" + folder + "/scripts/placeholders-errors.sk", context.script("placeholders-errors.sk"))
                .file("plugins/" + folder + "/scripts/ph-reload.sk", reloadScript("nskone"));
        if (neoskript) {
            // skript-placeholders 1.6's syntax, which 1.7 no longer reads and NeoSkriptPlaceholders does.
            builder.plugin(addonJar(context)).file("plugins/NeoSkript/scripts/ph-legacy.sk", """
                    on placeholderapi request for the prefix "nsklegacy":
                    	set the result to "legacy %the identifier%"

                    command /nsk-placeholders-legacy:
                    	trigger:
                    		send "[harness] legacy %parse placeholder "nsklegacy_x"%" to console
                    """);
        }
        Path reload = runDir.resolve("plugins/" + folder + "/scripts/ph-reload.sk");
        String hooksLine;
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] ph loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            if (neoskript) {
                server.await(Pattern.compile(Pattern.quote("plugins/" + ORIGINAL.fileName() + " is skript-placeholders,"
                        + " which needs Skript, so it doesn't load with NeoSkript. " + ADDON + " replaces it:")), WAIT);
                server.await(Pattern.compile(Pattern.quote("Registered the syntax of " + ADDON + " ")), 0, WAIT);
            }
            server.command("difficulty peaceful");
            int mark = server.mark();
            if (neoskript) {
                server.command("nsk-placeholders-legacy");
                server.await(line("[harness] legacy legacy x"), mark, WAIT);
            }

            server.command("nsk-placeholders");
            server.await(line("[harness] ph done"), mark, WAIT);

            server.command("bot join Alice");
            server.await(line("[bot] Alice joined"), mark, WAIT);
            server.command("bot join Bob");
            server.await(line("[bot] Bob joined"), mark, WAIT);
            server.command("bot command Alice nsk-placeholders-online");
            server.await(line("[harness] ph online done"), mark, WAIT);
            int health = server.mark();
            server.command("nsk-placeholders-health");
            server.await(line("[harness] ph health done"), mark, WAIT);
            if (folia && neoskript) {
                // The console's global thread doesn't own Alice: reading her placeholder there is a runtime error.
                server.await(Pattern.compile(Pattern.quote("The player Alice is in another region")), health, WAIT);
                // PlaceholderAPI asking from that thread: the script's trigger runs there, and its read of Alice is
                // a runtime error too, after which the read gives nothing and the trigger carries on.
                int asked = server.mark();
                server.command("papi parse Alice health=%nsktest_health%");
                server.await(Pattern.compile(Pattern.quote("The player Alice is in another region")), asked, WAIT);
                server.await(Pattern.compile(Pattern.quote("health=<none>") + "$"), asked, WAIT);
            }

            // Reloading to another prefix, a few times, then to none.
            server.command("nsk-placeholders-reload");
            server.await(line("[harness] ph reload done"), mark, WAIT);
            for (int i = 0; i < 4; i++) {
                String prefix = i % 2 == 0 ? "nsktwo" : "nskone";
                Files.writeString(reload, reloadScript(prefix));
                int before = server.mark();
                server.command("sk reload ph-reload.sk");
                server.await(line("[harness] ph reloaded " + prefix), before, WAIT);
            }
            server.command("nsk-placeholders-reload");
            server.await(line("[harness] ph reload done"), mark, WAIT);
            Files.writeString(reload, reloadScript(null));
            int before = server.mark();
            server.command("sk reload ph-reload.sk");
            server.await(line("[harness] ph reloaded none"), before, WAIT);
            server.command("nsk-placeholders-reload");
            server.await(line("[harness] ph reload done"), before, WAIT);
            before = server.mark();
            server.command("papi list");
            server.await(Pattern.compile("A total of \\d+ placeholder hook\\(s\\) are active"), before, WAIT);
            server.await(Pattern.compile("^[a-z0-9, ]+$"), before, WAIT);
            String hooks = "";
            List<String> listed = server.lines(before);
            for (int i = 0; i < listed.size(); i++) {
                if (listed.get(i).contains("placeholder hook(s) are active") && i + 1 < listed.size()) {
                    hooks = listed.get(i + 1).strip();
                }
            }
            List<String> active = List.of(hooks.split(",\\s*"));
            for (String gone : List.of("nskone", "nsktwo")) {
                if (active.contains(gone)) {
                    throw new HarnessFailure(folder + ": PlaceholderAPI still has " + gone + " after the script that"
                            + " registered it reloaded without it: " + hooks + ". See " + server.logFile());
                }
            }
            if (neoskript && !active.contains("nsklegacy")) {
                throw new HarnessFailure("PlaceholderAPI is missing nsklegacy, from 1.6's syntax: " + hooks + ". See "
                        + server.logFile());
            }
            // Compared without the errors script's structure with a bad line: Skript loads it without that line, and
            // registers its prefix; NeoSkript leaves out a trigger with an error in it. And without 1.6's, which only
            // NeoSkript reads.
            hooksLine = "[harness] ph hooks: " + String.join(", ", active.stream()
                    .filter(h -> !h.equals("nskerrors") && !h.equals("nsklegacy")).toList());

            server.command("bot quit Alice");
            server.await(line("[bot] Alice left"), mark, WAIT);
            server.command("bot quit Bob");
            server.await(line("[bot] Bob left"), mark, WAIT);
            server.stop();

            if (neoskript) {
                List<String> violations = server.linesMatching(NeoSkriptRegionsScenario.THREAD_VIOLATION);
                if (!violations.isEmpty()) {
                    throw new HarnessFailure("Placeholders broke Folia's thread rules: " + violations + ". See "
                            + server.logFile());
                }
                List<String> errors = server.linesMatching(Pattern.compile("(?i)\\[" + ADDON + "].*(exception|error)"
                        + "|Error occurred while enabling " + ADDON + "|Could not load .*" + ADDON));
                if (!errors.isEmpty()) {
                    throw new HarnessFailure(ADDON + " logged errors: " + errors + ". See " + server.logFile());
                }
            }
            List<String> transcript = new ArrayList<>();
            for (String text : server.linesMatching(TRANSCRIPT)) {
                Matcher matcher = TRANSCRIPT.matcher(text);
                if (matcher.find()) {
                    transcript.add(matcher.group(1));
                }
            }
            transcript.add(hooksLine);
            transcript.addAll(errors(server.lines(0)));
            return transcript;
        }
    }

    /** The errors in the errors script: each as {@code [harness] ph error <line>: <message>}. */
    static List<String> errors(List<String> log) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i + 1 < log.size(); i++) {
            Matcher at = ERROR_AT.matcher(log.get(i));
            if (at.find()) {
                String message = log.get(i + 1);
                int text = message.indexOf("]: ");
                message = (text >= 0 ? message.substring(text + 3) : message).replaceAll("\u001B\\[[0-9;]*m", "")
                        .replaceFirst("^\\[(Skript|NeoSkript)] ?", "").strip();
                errors.add("[harness] ph error " + at.group(1) + ": " + message);
            }
        }
        // Skript reports a structure's errors before the code's in other structures; only what they are is compared.
        errors.sort(java.util.Comparator.comparingInt(error -> Integer.parseInt(error.split("[ :]")[3])));
        return errors;
    }

    /** The reloaded script: placeholders with {@code prefix}, or none. */
    static String reloadScript(String prefix) {
        String name = prefix == null ? "none" : prefix;
        StringBuilder script = new StringBuilder("on load:\n\tsend \"[harness] ph reloaded " + name + "\" to console\n");
        if (prefix != null) {
            script.append("\nplaceholderapi placeholder with the prefix \"").append(prefix).append("\":\n")
                    .append("\tset the result to \"").append(prefix).append(" %the identifier%\"\n");
        }
        return script.toString();
    }

    private static Path addonJar(ScenarioContext context) throws IOException {
        for (Path jar : context.officialAddonJars()) {
            if (ADDON.equals(NeoSkriptOfficialAddonsScenario.properties(jar).getProperty("name"))) {
                return jar;
            }
        }
        throw new HarnessFailure("The harness wasn't given " + ADDON + "'s jar (addons/placeholders)");
    }

    private static Path download(ScenarioContext context, NeoSkriptHooksScenario.PinnedPlugin plugin)
            throws IOException, InterruptedException {
        return context.download(plugin.url(), plugin.sha256(), plugin.fileName());
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }

    /**
     * What Skript with skript-placeholders prints on Paper, and NeoSkript must print there too. On Folia, the console
     * reading Alice's placeholder is a runtime error, as the global thread doesn't own her, so it has no value.
     */
    static List<String> expected(boolean folia) {
        List<String> lines = new ArrayList<>(List.of(
                "[harness] ph reloaded nskone",
                "[harness] ph loaded",
                "[harness] ph static: hello",
                "[harness] ph with percent: hello",
                "[harness] ph number: 5",
                "[harness] ph decimal: 1.5",
                "[harness] ph parts: nsktest_parts_a_b|nsktest|parts_a_b|nsktest|parts_a_b",
                "[harness] ph player: <none>/Alice",
                "[harness] ph no player: <none>/<none>",
                "[harness] ph deleted: <none>",
                "[harness] ph reset: <none>",
                "[harness] ph read: ab",
                "[harness] ph colour: <A>aGreen",
                "[harness] ph nested: <hello>",
                "[harness] ph empty: <none>",
                "[harness] ph unanswered: <none>",
                "[harness] ph second trigger: from the second",
                "[harness] ph unknown prefix: <none>",
                "[harness] ph case: case NskCase_x",
                "[harness] ph case upper: case NskCase_y",
                "[harness] ph several: hello and 5",
                "[harness] ph several players: <none>/Alice and <none>/Bob",
                "[harness] ph in text: Value: hello and 5 and %nskmissing_x%",
                "[harness] ph possessive: hello",
                "[harness] ph in: hello",
                "[harness] ph no underscore: <none>",
                "[harness] ph done",
                "[harness] ph online player: Alice/Alice",
                "[harness] ph relational: Alice vs Bob (Alice) x",
                "[harness] ph relational reversed: Bob vs Alice (Bob) y_z",
                "[harness] ph relational unknown: <none>",
                "[harness] ph relational as plain: <none>",
                "[harness] ph online done",
                "[harness] ph health: 10",
                "[harness] ph health done",
                "[harness] ph one: nskone x",
                "[harness] ph two: <none>",
                "[harness] ph reload done",
                "[harness] ph reloaded nsktwo",
                "[harness] ph reloaded nskone",
                "[harness] ph reloaded nsktwo",
                "[harness] ph reloaded nskone",
                "[harness] ph one: nskone x",
                "[harness] ph two: <none>",
                "[harness] ph reload done",
                "[harness] ph reloaded none",
                "[harness] ph one: <none>",
                "[harness] ph two: <none>",
                "[harness] ph reload done",
                "[harness] ph hooks: nskcase, nskrel, nsktest",
                "[harness] ph error 4: PlaceholderAPI prefixes cannot contain the character '_'",
                "[harness] ph error 7: A prefix cannot be blank",
                "[harness] ph error 10: PlaceholderAPI prefixes cannot contain the character '{'",
                "[harness] ph error 13: MVdWPlaceholderAPI placeholders can not be created because the plugin is not installed.",
                "[harness] ph error 17: 'the first player' can only be used in custom relational placeholders.",
                "[harness] ph error 20: 'the placeholder result' can only be used in custom placeholders",
                "[harness] ph error 21: 'the identifier' can only be used in custom placeholders",
                "[harness] ph error 22: There must be two players provided to use relational placeholders."));
        if (folia) {
            lines.set(lines.indexOf("[harness] ph health: 10"), "[harness] ph health: <none>");
        }
        return lines;
    }
}
