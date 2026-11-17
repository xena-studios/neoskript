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
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Skript's config.sk settings on NeoSkript: Skript's default config.sk, then the same with settings changed, each
 * given to the reference Skript and to NeoSkript moving over from it, with Vault and EssentialsX for Vault's hook.
 * NeoSkript must copy the changed settings to its config.yml, {@code scripts/config-settings.sk} must write the same
 * {@code logs/cfg.log} on both (the timestamps in the date format too) and show the harness bot's player the same
 * things, and {@code scripts/config-warnings.sk} must get the same warnings and errors when it loads. Lines the
 * verbosity logs must be in both consoles, and lines it hides in neither.
 *
 * <p>Skript's results are cached by their inputs in {@code .harness-cache/config-settings}.
 */
public final class ConfigSettingsScenario implements Scenario {

    /** Bump when the way results are gathered changes, so cached results of the reference Skript go stale. */
    private static final int VERSION = 2;
    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final Pattern LOG_LINE = Pattern.compile("^\\[(.+?)] (.*)$");
    private static final Pattern BOT_LINE = Pattern.compile("(\\[bot:Alice] .*)$");

    /**
     * A config.sk: Skript's options changed from its default config ({@code runtime errors.total errors per frame}
     * for one in a section), what NeoSkript's config.yml must then have, the date format the log's timestamps are
     * in, and the lines where NeoSkript doesn't follow Skript on purpose, with what each gives.
     */
    private record Variant(String name, Map<String, String> changes, List<String> configYml, Pattern timestamp,
            Map<String, List<String>> differences, List<Pattern> logged, List<Pattern> hidden) {

        Variant(String name, Map<String, String> changes, List<String> configYml, Pattern timestamp,
                Map<String, List<String>> differences) {
            this(name, changes, configYml, timestamp, differences, List.of(), List.of());
        }
    }

    /** The log's timestamps with Skript's default date format, in the harness's locale. */
    private static final Pattern DEFAULT_TIMESTAMP = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d\\d.[AP]M");
    /** Skript has its 'case sensitive' option the wrong way round in 'positions of': off, it finds text with case. */
    private static final Map<String, List<String>> POSITIONS = Map.of("positions", List.of("positions: 4", "positions: 1 and 4"));

    private static final List<Variant> VARIANTS = List.of(
            new Variant("default", Map.of(), List.of("^language: [\"']?english[\"']?$", "^  number-accuracy: 2$",
                    "^  player-variable-fix: true$", "^  priority: [\"']?high[\"']?$"), DEFAULT_TIMESTAMP, POSITIONS),
            new Variant("text", ordered(
                    "language", "turkish",
                    "date format", "yyyy-MM-dd HH:mm 'Uhr'",
                    "number accuracy", "4",
                    "case sensitive", "true",
                    "color codes reset formatting", "false",
                    "safe tags", "decorations",
                    "parse links in chat messages", "strict",
                    "maximum target block distance", "5",
                    "player name regex pattern", "[a-z]{3,10}",
                    "player variable fix", "false",
                    "plugin priority", "low",
                    "listen to cancelled events by default", "true",
                    "runtime errors.total errors per frame", "3"),
                    List.of("^language: [\"']?turkish[\"']?$",
                            "^  date-format: (yyyy-MM-dd HH:mm 'Uhr'|\"yyyy-MM-dd HH:mm 'Uhr'\"|'yyyy-MM-dd HH:mm ''Uhr''')$",
                            "^  number-accuracy: 4$", "^  case-sensitive: true$",
                            "^  color-codes-reset-formatting: false$",
                            "^  safe-tags:(\\s*-\\s*|\\s*\\[)[\"']?decorations[\"']?",
                            "^  parse-links: [\"']?strict[\"']?$", "^  target-block-distance: 5$",
                            "^  player-name-pattern: [\"']\\[a-z]\\{3,10}[\"']$", "^  player-variable-fix: false$",
                            "^  priority: [\"']?low[\"']?$", "^  listen-to-cancelled: true$",
                            "^  total-errors-per-frame: 3$"),
                    Pattern.compile("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d Uhr"),
                    // Skript has its 'case sensitive' option the wrong way round here: on, it finds text without case.
                    Map.of("positions", List.of("positions: 1 and 4", "positions: 4"))),
            new Variant("warnings", ordered(
                    "verbosity", "low",
                    "disable variable will not be saved warnings", "true",
                    "disable variable missing and/or warnings", "true",
                    "disable starting a variable's name with an expression warnings", "true",
                    "disable single colon in variable name warnings", "true",
                    "disable unreachable code warnings", "true",
                    "enable legacy tab completion event compatibility", "false",
                    "hooks.vault", "true"),
                    List.of("^verbosity: [\"']?low[\"']?$", "^  variable-not-saved: false$", "^  missing-and-or: false$",
                            "^  variable-starting-with-expression: false$",
                            "^  single-colon-in-variable-name: false$", "^  unreachable-code: false$",
                            "^  legacy-tab-completion: false$", "^  vault: false$"),
                    DEFAULT_TIMESTAMP, POSITIONS, List.of(),
                    // What Skript only says from normal on: how many scripts and variables loaded.
                    List.of(Pattern.compile("Loaded \\d+ (scripts?|variables?)"))),
            new Variant("verbose", ordered("verbosity", "very high"), List.of("^verbosity: [\"']?very-high[\"']?$"),
                    DEFAULT_TIMESTAMP, POSITIONS,
                    List.of(Pattern.compile("loading trigger 'on join'$"),
                            Pattern.compile("loaded 3 structures from 'config-settings\\.sk'$"),
                            Pattern.compile("Successfully hooked into Vault$"),
                            Pattern.compile("== org\\.bukkit\\.event\\.player\\.PlayerJoinEvent with priority HIGH ==$")),
                    List.of()));

    /**
     * What one plugin did: its log lines without their timestamps, what the player was shown, what
     * {@code config-warnings.sk} got when it loaded, and the variant's logged and hidden lines its console had.
     */
    record Results(List<String> log, List<String> player, List<String> problems, List<String> console) {}

    private static final Pattern PREFIX = Pattern.compile("^\\[\\d\\d:\\d\\d:\\d\\d \\w+]: (?:\\[[\\w ]+] )?");
    private static final Pattern PROBLEM = Pattern.compile("^Line (\\d+): \\(config-warnings\\.sk\\)$");

    @Override
    public String name() {
        return "config-settings";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        String stock = stockConfig(context);
        String script = context.script("config-settings.sk");
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Gson gson = new Gson();
        List<String> failures = new ArrayList<>();
        for (Variant variant : VARIANTS) {
            String configSk = change(stock, variant.changes());
            Path cached = context.config().cacheDir().resolve("config-settings")
                    .resolve(key(context, configSk, script) + ".json");
            Results skript;
            if (Files.exists(cached)) {
                skript = gson.fromJson(Files.readString(cached), Results.class);
            } else {
                skript = play(context, runs.resolve(variant.name()).resolve("skript"), context.skriptJar(), "Skript",
                        configSk, script, variant);
                Files.createDirectories(cached.getParent());
                Files.writeString(cached, gson.toJson(skript));
            }
            Path neoDir = runs.resolve(variant.name()).resolve("neoskript");
            Results neoskript = play(context, neoDir, context.neoskriptJar(), "NeoSkript", configSk, script, variant);

            String configYml = Files.readString(neoDir.resolve("plugins/NeoSkript/config.yml"));
            for (String expected : variant.configYml()) {
                if (!Pattern.compile(expected, Pattern.MULTILINE).matcher(configYml).find()) {
                    failures.add(variant.name() + ": config.yml doesn't have " + expected);
                }
            }
            compare(variant, skript, neoskript, failures);
            context.summary(variant.name(), skript.log().size() + " log lines");
        }
        if (!failures.isEmpty()) {
            throw new HarnessFailure("NeoSkript didn't follow Skript's config.sk:\n  " + String.join("\n  ", failures)
                    + "\nSee " + runs);
        }
    }

    private static void compare(Variant variant, Results skript, Results neoskript, List<String> failures) {
        Map<String, String> skriptLines = byLabel(skript.log());
        Map<String, String> neoLines = byLabel(neoskript.log());
        if (!skriptLines.keySet().equals(neoLines.keySet())) {
            failures.add(variant.name() + ": the log lines differ: Skript " + skriptLines.keySet() + ", NeoSkript "
                    + neoLines.keySet());
        }
        for (Map.Entry<String, String> line : skriptLines.entrySet()) {
            String neo = neoLines.get(line.getKey());
            List<String> difference = variant.differences().get(line.getKey());
            if (difference != null) {
                if (!difference.equals(List.of(line.getValue(), String.valueOf(neo)))) {
                    failures.add(variant.name() + ": expected the known difference " + difference + ", but Skript gave '"
                            + line.getValue() + "' and NeoSkript '" + neo + "'");
                }
            } else if (!line.getValue().equals(neo)) {
                failures.add(variant.name() + ": Skript logged '" + line.getValue() + "', NeoSkript '" + neo + "'");
            }
        }
        if (!skript.problems().equals(neoskript.problems())) {
            failures.add(variant.name() + ": config-warnings.sk got " + skript.problems() + " on Skript and "
                    + neoskript.problems() + " on NeoSkript");
        }
        List<String> expected = new ArrayList<>();
        variant.logged().forEach(pattern -> expected.add("logged " + pattern));
        for (Results results : List.of(skript, neoskript)) {
            if (!results.console().equals(expected)) {
                failures.add(variant.name() + ": " + (results == skript ? "Skript" : "NeoSkript") + "'s console had "
                        + results.console() + ", not " + expected);
            }
        }
        if (!skript.player().equals(neoskript.player())) {
            failures.add(variant.name() + ": the player was shown " + skript.player() + " by Skript and "
                    + neoskript.player() + " by NeoSkript");
        }
    }

    /** Log lines by what's before their first {@code :}. */
    private static Map<String, String> byLabel(List<String> lines) {
        Map<String, String> byLabel = new LinkedHashMap<>();
        for (String line : lines) {
            int colon = line.indexOf(':');
            byLabel.put(colon < 0 ? line : line.substring(0, colon), line);
        }
        return byLabel;
    }

    /** Runs the script with the config.sk on one plugin, and returns what it logged and showed the player. */
    private static Results play(ScenarioContext context, Path runDir, Path plugin, String folder, String configSk,
            String script, Variant variant) throws Exception {
        // NeoSkript gets them where Skript keeps them, and copies them over on its first start.
        var builder = context.server(runDir)
                .plugin(plugin)
                .plugin(context.botJar())
                .file("plugins/Skript/config.sk", configSk)
                .file("plugins/Skript/scripts/config-settings.sk", script)
                .file("plugins/Skript/scripts/config-warnings.sk", context.script("config-warnings.sk"));
        // Vault with an economy, for Vault's hook.
        for (NeoSkriptHooksScenario.PinnedPlugin pinned : NeoSkriptHooksScenario.PLUGINS) {
            if (pinned.fileName().startsWith("Vault") || pinned.fileName().startsWith("EssentialsX")) {
                builder.plugin(context.download(pinned.url(), pinned.sha256(), pinned.fileName()));
            }
        }
        try (ServerProcess server = builder.start()) {
            server.await(line("[harness] cfg loaded"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            server.command("difficulty peaceful");
            int mark = server.mark();
            server.command("bot join Alice");
            server.await(line("[bot:Alice] first"), mark, WAIT);
            // The second use within the cooldown gets the language's cooldown message.
            server.command("bot command Alice cfgcool");
            server.await(line("[bot:Alice] cooled"), mark, WAIT);
            int cooled = server.mark();
            server.command("bot command Alice cfgcool");
            server.await(Pattern.compile("\\[bot:Alice] (?!cooled).+$"), cooled, WAIT);
            // Joining again: the player in the variable is the one now online only with the player variable fix.
            int quit = server.mark();
            server.command("bot quit Alice");
            server.await(line("[bot] Alice left"), quit, WAIT);
            server.command("bot join Alice");
            server.await(line("[bot:Alice] marker"), quit, WAIT);
            quit = server.mark();
            server.command("bot quit Alice");
            server.await(line("[bot] Alice left"), quit, WAIT);
            server.stop();

            List<String> problems = new ArrayList<>();
            List<String> all = server.lines(0);
            for (int i = 0; i + 1 < all.size(); i++) {
                Matcher prefix = PREFIX.matcher(all.get(i));
                Matcher problem = PROBLEM.matcher(prefix.find() ? all.get(i).substring(prefix.end()) : all.get(i));
                if (problem.matches()) {
                    Matcher next = PREFIX.matcher(all.get(i + 1));
                    problems.add(problem.group(1) + ": "
                            + (next.find() ? all.get(i + 1).substring(next.end()) : all.get(i + 1)).strip());
                }
            }
            List<String> console = new ArrayList<>();
            for (Pattern logged : variant.logged()) {
                if (all.stream().anyMatch(text -> logged.matcher(text).find())) {
                    console.add("logged " + logged);
                }
            }
            for (Pattern hidden : variant.hidden()) {
                all.stream().filter(text -> hidden.matcher(text).find()).findFirst()
                        .ifPresent(text -> console.add("not hidden: " + text));
            }
            List<String> player = new ArrayList<>();
            for (String text : server.lines(mark)) {
                Matcher matcher = BOT_LINE.matcher(text);
                if (matcher.find()) {
                    player.add(matcher.group(1));
                }
            }
            List<String> log = new ArrayList<>();
            for (String text : Files.readAllLines(runDir.resolve("plugins/" + folder + "/logs/cfg.log"))) {
                Matcher matcher = LOG_LINE.matcher(text);
                if (!matcher.matches() || !variant.timestamp().matcher(matcher.group(1)).matches()) {
                    throw new HarnessFailure(folder + " (" + variant.name() + "): a log line isn't stamped in the date"
                            + " format: " + text);
                }
                log.add(matcher.group(2));
            }
            return new Results(log, player, problems, console);
        }
    }

    /** Skript's config.sk as it ships in the reference Skript's jar, as Skript writes it on its first start. */
    private static String stockConfig(ScenarioContext context) throws IOException, InterruptedException {
        try (ZipFile jar = new ZipFile(context.skriptJar().toFile());
                InputStream in = jar.getInputStream(jar.getEntry("config.sk"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("@version@", context.referenceSkript().version())
                    .replace("@release-channel@", "none");
        }
    }

    /** The config with each option's value changed, in its place. */
    static String change(String config, Map<String, String> changes) {
        for (Map.Entry<String, String> change : changes.entrySet()) {
            String key = change.getKey().substring(change.getKey().lastIndexOf('.') + 1);
            Pattern option = Pattern.compile("(?m)^([ \\t]*)" + Pattern.quote(key) + ":.*$");
            Matcher matcher = option.matcher(config);
            if (!matcher.find()) {
                throw new IllegalStateException("Skript's config.sk has no '" + change.getKey() + "'");
            }
            config = config.substring(0, matcher.start()) + matcher.group(1) + key + ": " + change.getValue()
                    + config.substring(matcher.end());
        }
        return config;
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String key(ScenarioContext context, String configSk, String script) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String part : List.of(String.valueOf(VERSION), context.referenceSkript().version(),
                context.environment().name(), configSk, script, context.script("config-warnings.sk"))) {
            digest.update(part.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest(), 0, 12);
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
