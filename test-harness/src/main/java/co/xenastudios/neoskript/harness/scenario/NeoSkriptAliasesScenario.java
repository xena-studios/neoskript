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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Alias files, as Skript reads them from {@code plugins/Skript/aliases}: the files in {@code scripts/aliases} go in
 * {@code plugins/NeoSkript/aliases}, and {@code scripts/aliases-probe.sk} prints what each name stands for, how its
 * items are written, their raw names and components, and the block states an alias places. Where the reference Skript
 * runs, the same files and script run on Skript 2.17.0 too, and the alias files' problems and the probe's output must
 * be the same, line for line.
 *
 * <p>Then, on NeoSkript: {@code /sk reload aliases} reloads the alias files and the scripts that use the names that
 * changed (a script that had errors, as an alias was missing, loads; one that doesn't use them isn't reloaded); a
 * script whose alias went away keeps its last version (D12); alias files that stop Skript loading aliases are errors
 * of their own lines; {@code load-defaults: false} leaves Skript's default aliases out; and reloading the aliases
 * again and again leaves the server as it was.
 */
public final class NeoSkriptAliasesScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final Pattern PROBE = Pattern.compile("\\[aliasprobe] .*$");
    private static final Pattern PROBE_DONE = Pattern.compile("\\[aliasprobe] done$");
    private static final Pattern PROBLEM = Pattern.compile("\\[Skript] Line (\\d+): \\(([^)]+)\\)$");
    private static final Pattern FILE_PROBLEM = Pattern.compile("\\[Skript] {5}('[^']+\\.sk' .+)$");
    private static final List<String> ALIAS_FILES = List.of("bad.sk", "custom.sk", "more.sk", "empty.sk", "crash.sk");
    private static final int RELOADS = 30;

    @Override
    public String name() {
        return "neoskript-aliases";
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
        Path base = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Result neoskript = neoskript(context, base.resolve("neoskript"));
        if (!context.environment().skriptReference()) {
            context.summary("compared", "no reference Skript on " + context.environment().name());
            return;
        }
        Result skript = skript(context, base.resolve("skript"));
        List<String> differences = new ArrayList<>();
        compare("alias file problem", skript.problems(), neoskript.problems(), differences);
        compare("probe line", skript.probes(), neoskript.probes(), differences);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("skriptProblems", skript.problems());
        report.put("neoskriptProblems", neoskript.problems());
        report.put("skriptProbes", skript.probes());
        report.put("neoskriptProbes", neoskript.probes());
        report.put("differences", differences);
        Path file = context.writeReport(name(), report);
        context.summary("probes", skript.probes().size());
        context.summary("problems", skript.problems().size());
        if (!differences.isEmpty()) {
            throw new HarnessFailure("NeoSkript's aliases differ from Skript's in " + differences.size() + " place"
                    + (differences.size() == 1 ? "" : "s") + ":\n  " + String.join("\n  ", differences.subList(0,
                            Math.min(differences.size(), 30))) + "\nSee " + file);
        }
    }

    /** The alias files' problems at startup, and the probe's lines, as one server printed them. */
    private record Result(List<String> problems, List<String> probes) {}

    private static void compare(String what, List<String> skript, List<String> neoskript, List<String> differences) {
        for (int i = 0; i < Math.max(skript.size(), neoskript.size()); i++) {
            String expected = i < skript.size() ? skript.get(i) : "<nothing>";
            String actual = i < neoskript.size() ? neoskript.get(i) : "<nothing>";
            if (!expected.equals(actual)) {
                differences.add(what + " " + (i + 1) + ": Skript: " + expected + " | NeoSkript: " + actual);
            }
        }
    }

    private static ServerBuilder withAliases(ServerBuilder builder, ScenarioContext context, String folder)
            throws IOException {
        Path aliases = context.config().scriptsDir().resolve("aliases");
        try (Stream<Path> files = Files.walk(aliases)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                builder.file(folder + "/aliases/" + aliases.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
            }
        }
        return builder.file(folder + "/aliases/empty.sk", "")
                .file(folder + "/scripts/aliases-probe.sk", context.script("aliases-probe.sk"));
    }

    private Result skript(ScenarioContext context, Path runDir) throws Exception {
        ServerBuilder builder = withAliases(context.server(runDir).plugin(context.skriptJar()), context, "plugins/Skript");
        try (ServerProcess server = builder.start()) {
            server.await(PROBE_DONE, WAIT);
            Result result = result(server);
            server.stop();
            return result;
        }
    }

    private static Result result(ServerProcess server) {
        List<String> lines = server.lines(0);
        List<String> problems = new ArrayList<>();
        List<String> probes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher problem = PROBLEM.matcher(line);
            if (problem.find() && ALIAS_FILES.contains(problem.group(2)) && i + 1 < lines.size()) {
                problems.add(problem.group(2) + ":" + problem.group(1) + ": " + text(lines.get(i + 1)).strip());
                continue;
            }
            Matcher file = FILE_PROBLEM.matcher(line);
            if (file.find()) {
                problems.add(file.group(1).strip());
                continue;
            }
            Matcher probe = PROBE.matcher(line);
            if (probe.find()) {
                probes.add(probe.group());
                if (PROBE_DONE.matcher(line).find()) {
                    break;
                }
            }
        }
        return new Result(problems, probes);
    }

    /** A console line without its time and level. */
    private static String text(String line) {
        int start = line.indexOf("]: ");
        return start < 0 ? line : line.substring(start + 3);
    }

    private static final String USES_TOPAZ = """
            command /topazcheck:
            	trigger:
            		set {_item} to topaz
            		send "[aliases] topaz is %{_item}%" to console
            on load:
            	set {_item} to topaz
            	send "[aliases] topaz script loaded: %{_item}%" to console
            """;
    private static final String NEEDS_JADE = """
            on load:
            	set {_item} to jade
            	send "[aliases] jade script loaded: %{_item}%" to console
            """;
    /** Mentions topaz, but never as an item: a change to topaz doesn't reload it. */
    private static final String MENTIONS_TOPAZ = """
            # topaz is the shop's gem
            on load:
            	set {topaz} to "topaz"
            	send "[aliases] mentions script loaded" to console
            """;
    /** Names topaz through an option, which only the loaded script shows. */
    private static final String OPTION_TOPAZ = """
            options:
            	gem: topaz
            on load:
            	set {_item} to {@gem}
            	send "[aliases] option script loaded: %{_item}%" to console
            """;
    private static final String PLAIN = """
            on load:
            	send "[aliases] plain script loaded" to console
            """;

    private Result neoskript(ScenarioContext context, Path runDir) throws Exception {
        ServerBuilder builder = withAliases(context.server(runDir).plugin(context.neoskriptJar()), context,
                "plugins/NeoSkript")
                .file("plugins/NeoSkript/scripts/uses-topaz.sk", USES_TOPAZ)
                .file("plugins/NeoSkript/scripts/needs-jade.sk", NEEDS_JADE)
                .file("plugins/NeoSkript/scripts/plain.sk", PLAIN)
                .file("plugins/NeoSkript/scripts/mentions-topaz.sk", MENTIONS_TOPAZ)
                .file("plugins/NeoSkript/scripts/option-topaz.sk", OPTION_TOPAZ);
        Path aliases = builder.runDir().resolve("plugins/NeoSkript/aliases");
        try (ServerProcess server = builder.start()) {
            server.await(PROBE_DONE, WAIT);
            server.await(line("[aliases] topaz script loaded: topaz"), WAIT);
            server.await(line("[aliases] option script loaded: topaz"), WAIT);
            Result result = result(server);
            if (!server.linesMatching(line("[aliases] jade script loaded")).isEmpty()) {
                throw new HarnessFailure("A script using an alias no file defines loaded. See " + server.logFile());
            }

            // A fix: Skript never finds an alias written with capitals, as it looks names up in lower case.
            Files.writeString(aliases.resolve("sub/more.sk"), """
                    later:
                    	ruby = minecraft:redstone
                    	topaz = minecraft:gold_ingot
                    	Jade = minecraft:lime_dye
                    """);
            // Lines that stop Skript loading any aliases are errors of their own here.
            Files.writeString(aliases.resolve("crash.sk"), """
                    crashes:
                    	unknown var = {nope} minecraft:stone
                    	states only = [facing=north]
                    	no value = minecraft:anvil[facing]
                    	lone} brace = minecraft:stone
                    	bad components = minecraft:diamond {minecraft:nonsense=1}
                    	still fine = minecraft:flint
                    old:
                    	minecraft version = banana
                    """);
            int mark = server.mark();
            server.command("sk reload aliases");
            server.await(neo("Reloading the aliases..."), mark, WAIT);
            server.await(line("[aliases] jade script loaded: Jade"), mark, WAIT);
            server.await(Pattern.compile(" aliases/crash\\.sk - 6 errors$"), mark, WAIT);
            server.await(Pattern.compile("Line 6: Failed to apply component data to ItemStack: .*$"), mark, WAIT);
            server.await(Pattern.compile(Pattern.quote("[NeoSkript] Reloaded the aliases and 1 script with ")
                    + "\\d+ errors? and \\d+ warnings? in \\d+ ms\\..*$"), mark, WAIT);
            if (server.lines(mark).stream().anyMatch(each -> each.contains("[aliases] plain script loaded")
                    || each.contains("[aliases] mentions script loaded")
                    || each.contains("[aliases] topaz script loaded"))) {
                throw new HarnessFailure("/sk reload aliases reloaded a script that doesn't use the changed aliases. See "
                        + server.logFile());
            }
            mark = server.mark();
            server.command("aliasprobe still fine");
            server.await(line("[aliasprobe] still fine => still fine | raw: minecraft:flint | two: 2 still fine"
                    + " | model: 0 | name: <none>"), mark, WAIT);

            // An alias a running script uses goes away: the script keeps its last version (D12).
            Files.writeString(aliases.resolve("sub/more.sk"), "later:\n\truby = minecraft:redstone\n\tjade = minecraft:lime_dye\n");
            Files.delete(aliases.resolve("crash.sk"));
            mark = server.mark();
            server.command("sk reload aliases");
            server.await(Pattern.compile(" uses-topaz\\.sk - 2 errors$"), mark, WAIT);
            // Exactly the scripts whose items used topaz: the one that names it and the one with it in an option. The
            // probe script only reads it from text, and another script only mentions it.
            server.await(Pattern.compile(" option-topaz\\.sk - 1 error$"), mark, WAIT);
            server.await(Pattern.compile(Pattern.quote("[NeoSkript] Reloaded the aliases and 2 scripts with ") + ".* in \\d+"
                    + Pattern.quote(" ms. Scripts with errors keep their last version.") + "$"), mark, WAIT);
            if (server.lines(mark).stream().anyMatch(each -> each.contains("[aliases] mentions script loaded")
                    || each.contains("[aliasprobe] done"))) {
                throw new HarnessFailure("/sk reload aliases reloaded a script that only mentions topaz. See "
                        + server.logFile());
            }
            mark = server.mark();
            server.command("topazcheck");
            // Its last version still runs. The gold ingot it makes is called by its own name now, as no alias names it.
            server.await(line("[aliases] topaz is gold ingot"), mark, WAIT);

            // Without Skript's default aliases, only the files' and the items' own names.
            Path config = builder.runDir().resolve("plugins/NeoSkript/config.yml");
            String text = Files.readString(config);
            if (!text.contains("  load-defaults: true")) {
                throw new HarnessFailure("config.yml doesn't have aliases.load-defaults:\n" + text);
            }
            // /sk reload aliases reads the setting from config.yml itself: no /sk reload config first.
            Files.writeString(config, text.replace("  load-defaults: true", "  load-defaults: false"));
            mark = server.mark();
            server.command("sk reload aliases");
            server.await(neo("Reloading the aliases..."), mark, WAIT);
            server.await(Pattern.compile("\\[NeoSkript] Reloaded the aliases.* in \\d+ ms\\..*$"), mark, WAIT);
            mark = server.mark();
            server.command("aliasprobe speed potion");
            server.await(line("[aliasprobe] speed potion => <none>"), mark, WAIT);
            server.command("aliasprobe tipped arrow");
            server.await(line("[aliasprobe] tipped arrow => tipped arrow | raw: minecraft:tipped_arrow | two: 2 tipped"
                    + " arrows | model: 0 | name: <none>"), mark, WAIT);
            server.command("aliasprobe ruby");
            server.await(line("[aliasprobe] ruby => ruby or ruby | raw: minecraft:red_dye and minecraft:redstone | two:"
                    + " 2 rubys or ruby | model: 0 | name: <none>"), mark, WAIT);

            // Again and again: each reload works, and nothing is left behind.
            mark = server.mark();
            for (int i = 0; i < RELOADS; i++) {
                server.command("sk reload aliases");
            }
            Pattern reloaded = Pattern.compile("\\[NeoSkript] Reloaded the aliases.* in \\d+ ms\\..*$");
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (server.lines(mark).stream().filter(each -> reloaded.matcher(each).find()).count() < RELOADS) {
                if (System.nanoTime() > deadline) {
                    throw new HarnessFailure("Not every /sk reload aliases finished. See " + server.logFile());
                }
                Thread.sleep(200);
            }
            mark = server.mark();
            server.command("aliasprobe shiny gem");
            server.await(line("[aliasprobe] shiny gem => shiny gem | raw: minecraft:diamond | two: 2 shiny gems | model: 7"
                    + " | name: <none>"), mark, WAIT);
            server.stop();
            for (String bad : List.of("Exception", "Couldn't reload the aliases", "Couldn't load the aliases")) {
                if (!server.linesMatching(Pattern.compile(Pattern.quote(bad))).isEmpty()) {
                    throw new HarnessFailure("The log has '" + bad + "'. See " + server.logFile());
                }
            }
            return result;
        }
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }

    private static Pattern neo(String text) {
        return Pattern.compile("\\[NeoSkript] " + Pattern.quote(text) + "$");
    }
}
