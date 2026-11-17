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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reloads a varied set of scripts a few hundred times and checks nothing from the old versions stays: events,
 * commands with aliases and a GUI, functions and a function reference, list variables, {@code every} timers, a
 * timer whose trigger is still waiting when the reload comes, a command waiting across reloads, {@code auto
 * reload}, a script disabled and enabled again, and one broken on every other reload, which keeps its last version. After warming up, the harness bot forces a full GC and samples
 * the loaded class count, the heap, and NeoSkript's listeners, commands (in Brigadier's tree too) and scheduled
 * tasks every 50 reloads. They must plateau, not grow. Then every script is disabled, and the classes and heap
 * must fall back, and the listeners, commands and tasks be what they were before any script loaded.
 */
public final class NeoSkriptReloadSoakScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final String[] SCRIPTS = {"soak-events.sk", "soak-functions.sk", "soak-commands.sk", "soak-extras.sk",
        "soak-flaky.sk"};
    private static final int WARM_UP = 50;
    private static final int SAMPLE_EVERY = 50;
    private static final Pattern STATS = Pattern.compile("\\[bot] stats (\\S+) (.*)$");
    private static final Pattern RELOADED = Pattern.compile("Successfully reloaded all scripts\\. \\(\\d+ms\\)");
    private static final Pattern FAILED_RELOAD = Pattern.compile("Encountered 1 error while reloading all scripts!");

    /** How much the loaded classes may grow over the measured reloads: some of the JDK's and the server's are lazy. */
    private static final long CLASS_SLACK = 400;
    /** How much the heap after a full GC may grow over the measured reloads, in KB. */
    private static final long HEAP_SLACK_KB = 24 * 1024;
    /** How many times Bukkit's {@code /reload confirm} runs. */
    private static final int PLUGIN_RELOADS = 8;
    /**
     * How many NeoSkript instances may be alive after each: the current one and up to five old ones, which are let
     * go of a few reloads later. (The harness bot's plugin is let go of at once; what keeps NeoSkript's is still to
     * be found.)
     */
    private static final long PLUGIN_RELOAD_LIMIT = 6;
    /** Waits in flight from {@code every 5 ticks} and {@code /soakdelay} are tasks too, for up to 3 seconds. */
    private static final long TASK_SLACK = 30;

    @Override
    public String name() {
        return "neoskript-reload-soak";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The harness bot, which samples the server, speaks the 26.x protocol.
        return !environment.minecraftVersion().startsWith("1.");
    }

    private static int reloads() {
        return Math.max(SAMPLE_EVERY, Integer.parseInt(System.getProperty("harness.soak.reloads", "300").strip()));
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String script : SCRIPTS) {
            sources.put(script, context.script("reload-soak/" + script));
        }
        // Soft references are cleared by every GC, so what's alive after one is held for good.
        ServerBuilder builder = context.server().plugin(context.neoskriptJar()).plugin(context.botJar())
                .jvmArg("-XX:SoftRefLRUPolicyMSPerMB=0");
        // Disabled at first, to sample NeoSkript without scripts.
        sources.forEach((script, source) -> builder.file("plugins/NeoSkript/scripts/-" + script, version(source, 0)));
        Path scripts = builder.runDir().resolve("plugins/NeoSkript/scripts");
        int reloads = reloads();
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            Map<String, Long> empty = sample(server, "empty");
            int start = server.mark();
            server.command("sk enable all");
            server.await(line("[harness] soak extras 0"), start, WAIT);
            server.command("gamerule doMobSpawning false");
            server.command("bot join soaker");
            server.await(line("[bot] soaker joined"), WAIT);

            List<Map<String, Long>> samples = new ArrayList<>();
            int total = WARM_UP + reloads;
            for (int round = 1; round <= total; round++) {
                reload(server, scripts, sources, round);
                if (round == WARM_UP || (round > WARM_UP && (round - WARM_UP) % SAMPLE_EVERY == 0)) {
                    samples.add(sample(server, "r" + round));
                }
            }
            check(server, samples);

            // Timers didn't pile up: every 1 tick runs once a tick, whatever was reloaded.
            int mark = server.mark();
            server.command("soakrate");
            Matcher rate = server.await(Pattern.compile("\\[harness] soak rate (\\d+)$"), mark, WAIT);
            int ticks = Integer.parseInt(rate.group(1));
            context.summary("ticks in 2 s", ticks);
            if (ticks > 45) {
                throw new HarnessFailure("'every 1 tick' ran " + ticks + " times in 2 seconds after " + total
                        + " reloads: old timers are still running. See " + server.logFile());
            }
            mark = server.mark();
            server.command("soakstate");
            server.await(line("[harness] soak state " + total + " 200 100 5 9"), mark, WAIT);

            // Disabled, nothing of the scripts is left.
            Map<String, Long> loaded = samples.getLast();
            mark = server.mark();
            server.command("sk disable all");
            server.await(line("Successfully disabled all scripts."), mark, WAIT);
            // The waits in flight finish first.
            Thread.sleep(5_000);
            Map<String, Long> disabled = sample(server, "disabled");
            report(context, empty, samples, disabled, reloads);
            for (String key : List.of("listeners", "brigadier", "tasks")) {
                if (!disabled.get(key).equals(empty.get(key))) {
                    throw new HarnessFailure("After /sk disable all, " + key + " are " + disabled.get(key) + ", "
                            + empty.get(key) + " before any script loaded. See " + server.logFile());
                }
            }
            if (disabled.get("commands") != 0) {
                throw new HarnessFailure(disabled.get("commands") + " script commands are still registered after "
                        + "/sk disable all. See " + server.logFile());
            }
            if (disabled.get("classes") >= loaded.get("classes")) {
                throw new HarnessFailure("The disabled scripts' classes weren't unloaded: " + disabled.get("classes")
                        + " classes, " + loaded.get("classes") + " with the scripts. See " + server.logFile());
            }
            if (!server.linesMatching(Pattern.compile("Exception|\\[harness] soak delayed .*Error")).isEmpty()) {
                throw new HarnessFailure("Something threw during the soak. See " + server.logFile());
            }
            pluginReloads(context, server);
            shutdown(server, builder.runDir());
        }
    }

    /**
     * Bukkit's {@code /reload confirm}, which disables NeoSkript and enables it again from a new class loader, a few
     * times with the scripts loaded. The old class loaders must be let go: the count of those still alive after a
     * full GC (soft references cleared, see {@link #run}) must stay low, not grow with each reload.
     */
    private static void pluginReloads(ScenarioContext context, ServerProcess server) throws Exception {
        int mark = server.mark();
        server.command("sk enable all");
        server.await(line("Successfully enabled & parsed all previously disabled scripts."), mark, WAIT);
        List<Long> loaders = new ArrayList<>();
        for (int i = 1; i <= PLUGIN_RELOADS; i++) {
            mark = server.mark();
            server.command("bukkit:reload confirm");
            server.await(Pattern.compile("Reload complete\\."), mark, WAIT);
            server.await(Pattern.compile("\\[harness] soak extras \\d+$"), mark, WAIT);
            loaders.add(sample(server, "plugin-reload-" + i).get("loaders"));
        }
        context.summary("NeoSkript instances alive over /reload", loaders);
        if (java.util.Collections.max(loaders) > PLUGIN_RELOAD_LIMIT) {
            throw new HarnessFailure("NeoSkript's old class loaders stay loaded after /reload confirm: " + loaders
                    + " alive after each. See " + server.logFile());
        }
    }

    /**
     * Stops the server with a trigger waiting and variables just changed: it must stop by itself in time (no thread of
     * NeoSkript's keeps it running), with nothing thrown on the way, and the variables saved.
     */
    private static void shutdown(ServerProcess server, Path runDir) throws Exception {
        int mark = server.mark();
        server.command("soakdelay");
        server.command("soak saved 1");
        server.await(Pattern.compile("\\[harness] soak saved 2 "), mark, WAIT);
        int stopping = server.mark();
        int code = server.stop();
        List<String> thrown = server.lines(stopping).stream()
                .filter(line -> line.contains("Exception") || line.contains("Error") && !line.contains("[harness]"))
                .toList();
        if (!thrown.isEmpty()) {
            throw new HarnessFailure("Stopping the server threw: " + thrown + ". See " + server.logFile());
        }
        if (code != 0) {
            throw new HarnessFailure("The server stopped with code " + code + ". See " + server.logFile());
        }
        String saved = Files.readString(runDir.resolve("plugins/NeoSkript/variables.csv"));
        if (!saved.contains("soak::list::200,") || !saved.contains("soak::saved,")) {
            throw new HarnessFailure("The variables weren't saved when the server stopped. See " + server.logFile());
        }
    }

    /** Reloads every script once, with something running across it now and then. */
    private static void reload(ServerProcess server, Path scripts, Map<String, String> sources, int round)
            throws Exception {
        for (Map.Entry<String, String> script : sources.entrySet()) {
            Files.writeString(scripts.resolve(script.getKey()), version(script.getValue(), round));
        }
        if (round % 10 == 0) {
            server.command("soakdelay");
            server.command("soak hello " + round);
            server.command("bot command soaker soakgui");
            server.command("bot chat soaker hi " + round);
        }
        if (round % 25 == 0) {
            int mark = server.mark();
            server.command("sk disable soak-extras");
            server.await(Pattern.compile("Successfully disabled soak-extras\\.sk\\."), mark, WAIT);
            server.command("sk enable soak-extras");
            server.await(Pattern.compile("Successfully enabled & parsed soak-extras\\.sk\\."), mark, WAIT);
        }
        int mark = server.mark();
        server.command("sk reload scripts");
        server.await(round % 2 == 1 ? FAILED_RELOAD : RELOADED, mark, WAIT);
    }

    /** A script's text for a round: odd rounds break soak-flaky.sk. */
    private static String version(String source, int round) {
        return source.replace("VERSION", Integer.toString(round))
                .replace("BROKEN", round % 2 == 1 ? "this line is not valid syntax" : "add 1 to {-soak::flakyLoads}");
    }

    private static Map<String, Long> sample(ServerProcess server, String label) throws Exception {
        int mark = server.mark();
        server.command("bot stats " + label);
        Matcher matcher = server.await(STATS, mark, WAIT);
        Map<String, Long> values = new LinkedHashMap<>();
        for (String pair : matcher.group(2).split(" ")) {
            String[] parts = pair.split("=", 2);
            values.put(parts[0], Long.parseLong(parts[1]));
        }
        return values;
    }

    /** The samples after warming up must stay within their slack of the first, and the last must not be the top of a climb. */
    private static void check(ServerProcess server, List<Map<String, Long>> samples) {
        Map<String, Long> first = samples.getFirst();
        Map<String, Long> last = samples.getLast();
        List<String> problems = new ArrayList<>();
        if (last.get("classes") - first.get("classes") > CLASS_SLACK) {
            problems.add("loaded classes grew from " + first.get("classes") + " to " + last.get("classes"));
        }
        if (last.get("heapKb") - first.get("heapKb") > HEAP_SLACK_KB) {
            problems.add("the heap after GC grew from " + first.get("heapKb") + " KB to " + last.get("heapKb") + " KB");
        }
        for (String key : List.of("listeners", "commands", "brigadier")) {
            for (Map<String, Long> sample : samples) {
                if (!sample.get(key).equals(first.get(key))) {
                    problems.add(key + " went from " + first.get(key) + " to " + sample.get(key));
                    break;
                }
            }
        }
        for (Map<String, Long> sample : samples) {
            if (sample.get("tasks") > first.get("tasks") + TASK_SLACK) {
                problems.add("scheduled tasks went from " + first.get("tasks") + " to " + sample.get("tasks"));
                break;
            }
            // The server's own pools grow on demand, so only NeoSkript's threads count.
            if (sample.get("pluginThreads") > first.get("pluginThreads")) {
                problems.add("NeoSkript's threads went from " + first.get("pluginThreads") + " to " + sample.get("pluginThreads"));
                break;
            }
        }
        if (!problems.isEmpty()) {
            throw new HarnessFailure("Reloading leaks: " + String.join("; ", problems) + ". Samples: " + samples
                    + ". See " + server.logFile());
        }
    }

    private static void report(ScenarioContext context, Map<String, Long> empty, List<Map<String, Long>> samples,
            Map<String, Long> disabled, int reloads) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reloads", reloads);
        report.put("warmUp", WARM_UP);
        report.put("empty", empty);
        report.put("samples", samples);
        report.put("disabled", disabled);
        context.writeReport("neoskript-reload-soak", report);
        Map<String, Long> first = samples.getFirst();
        Map<String, Long> last = samples.getLast();
        context.summary("classes", first.get("classes") + " -> " + last.get("classes") + " (disabled " + disabled.get("classes") + ")");
        context.summary("heap MB", first.get("heapKb") / 1024 + " -> " + last.get("heapKb") / 1024);
        context.summary("listeners", first.get("listeners") + " -> " + last.get("listeners"));
        context.summary("tasks", first.get("tasks") + " -> " + last.get("tasks"));
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
