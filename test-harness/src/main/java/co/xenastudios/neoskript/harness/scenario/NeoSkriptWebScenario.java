/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessConfig;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoSkriptWeb against the community addons it replaces (issue #18): the same scripts, {@code scripts/web-reqn.sk}
 * and {@code scripts/web-skjson.sk}, run on Skript 2.17.0 with Reqn 1.2.3 and, on another server, with skJson 6.0.0
 * (each pinned by SHA-256; the two clash on Skript, so each runs alone), and on NeoSkript with NeoSkriptWeb, all
 * against a local test server ({@link WebTestServer}). Every {@code [web] <suite> <label>: <value>} line must match,
 * except where {@code corpus/web/known-differences.txt} says NeoSkript differs on purpose, and there it must give what
 * the file says. On Folia, NeoSkript is compared with Skript on Paper.
 *
 * <p>Then NeoSkript's own checks ({@code scripts/web-neoskript.sk}): slow, silent, endless and compressed-bomb answers,
 * redirect loops, hostile JSON, hundreds of requests and a flood past the limits. The test server's connections must
 * all be closed after the requests that gave up; a trigger waiting on a request while its script reloads carries on
 * with its old code; and the server stops cleanly with a request still running. Nothing may reach the log as a stack
 * trace. The originals' jars are in NeoSkript's {@code plugins/} too: Paper refuses them, as they need Skript, and
 * NeoSkriptWeb loads and says to remove them (D21). Skript's results are cached by their inputs in
 * {@code .harness-cache/web}.
 */
public final class NeoSkriptWebScenario implements Scenario {

    /** Bump when the way results are gathered changes, so cached results of the originals go stale. */
    private static final int VERSION = 1;
    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final Pattern LINE = Pattern.compile("\\[web] (reqn|skjson) ([^:]+): (.*)$");
    private static final Pattern NEO = Pattern.compile("\\[web] neo ([^:]+): (.*)$");
    /** Anything thrown that reached NeoSkript's log. */
    private static final Pattern EXCEPTION = Pattern.compile("Exception|\\tat [a-z]|#!#!");

    static final PinnedPlugin REQN = new PinnedPlugin("reqn-1.2.3.jar",
            "https://github.com/btk5h/reqn/releases/download/v1.2.3/reqn-1.2.3.jar",
            "17031d2d35950424574f4ba4f2c00763a0492432a2234192be5ba0818a462cc8");
    static final PinnedPlugin SKJSON = new PinnedPlugin("skjson-6.0.0.jar",
            "https://github.com/cooffeeRequired/skJson/releases/download/6.0.0/skjson.jar",
            "42ccb1d64706322b13076b9e0e27a7e25168b462217cd2673c7bfb04014c5e5d");

    /** A plugin jar pinned to one build. */
    record PinnedPlugin(String fileName, String url, String sha256) {}

    private static List<Environment> environments;

    @Override
    public String name() {
        return "neoskript-web";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.minecraftVersion().equals("26.3");
    }

    /** Where the originals run: this server, or Paper of the same version for Folia. */
    private static Environment reference(Environment environment) throws IOException {
        if (environment.skriptReference()) {
            return environment;
        }
        if (environments == null) {
            environments = Environment.loadAll(HarnessConfig.fromSystemProperties().environmentsDir());
        }
        return environments.stream()
                .filter(e -> e.skriptReference() && e.minecraftVersion().equals(environment.minecraftVersion()))
                .findFirst().orElseThrow(() -> new HarnessFailure("No Paper " + environment.minecraftVersion()
                        + " to run Skript on"));
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        try (WebTestServer web = new WebTestServer()) {
            Ports ports = new Ports(web.port(), deadPort(), web.silentPort());
            Map<String, Map<String, List<String>>> original = originals(context, runs, ports);
            Map<String, Map<String, List<String>>> ours = neoskript(context, runs, web, ports);
            compare(context, original, ours);
        }
    }

    /** The test server's ports, and one nothing listens on. */
    private record Ports(int web, int dead, int silent) {
        String fill(String script) {
            return script.replace("@PORT@", Integer.toString(web)).replace("@DEAD@", Integer.toString(dead))
                    .replace("@SILENT@", Integer.toString(silent));
        }

        /** A value with this run's ports written as their names, so runs compare. */
        String normalise(String value) {
            return value.replace("127.0.0.1:" + web, "127.0.0.1:PORT").replace("127.0.0.1:" + dead, "127.0.0.1:DEAD")
                    .replace("127.0.0.1:" + silent, "127.0.0.1:SILENT");
        }
    }

    private static int deadPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** What Skript with each original gives, from the cache if these inputs ran before. */
    private Map<String, Map<String, List<String>>> originals(ScenarioContext context, Path runs, Ports ports)
            throws Exception {
        Environment reference = reference(context.environment());
        String reqnScript = context.script("web-reqn.sk");
        String skjsonScript = context.script("web-skjson.sk");
        String key = cacheKey(context, reference, reqnScript + "\n" + skjsonScript);
        Path cached = context.config().cacheDir().resolve("web").resolve(key + ".json");
        Gson gson = new Gson();
        if (Files.exists(cached)) {
            return gson.fromJson(Files.readString(cached),
                    new TypeToken<Map<String, Map<String, List<String>>>>() {}.getType());
        }
        Map<String, Map<String, List<String>>> results = new TreeMap<>();
        ServerBuilder reqn = context.server(reference, runs.resolve("skript-reqn"))
                .plugin(context.skriptJar())
                .plugin(context.download(REQN.url(), REQN.sha256(), REQN.fileName()))
                .file("plugins/Skript/scripts/web-reqn.sk", ports.fill(reqnScript));
        try (ServerProcess server = reqn.start()) {
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();
            server.command("webreqn");
            server.await(Pattern.compile("\\[web] reqn done$"), mark, WAIT);
            server.command("webreqnnone");
            // Reqn stops the trigger for an address that's none, so nothing comes after it: give it time to show.
            Thread.sleep(3_000);
            results.put("reqn", collect(server.lines(mark), ports).getOrDefault("reqn", Map.of()));
            server.stop();
        }
        ServerBuilder skjson = context.server(reference, runs.resolve("skript-skjson"))
                .plugin(context.skriptJar())
                .plugin(context.download(SKJSON.url(), SKJSON.sha256(), SKJSON.fileName()))
                .file("plugins/Skript/scripts/web-skjson.sk", ports.fill(skjsonScript));
        try (ServerProcess server = skjson.start()) {
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();
            server.command("webskjson");
            server.await(Pattern.compile("\\[web] skjson done$"), mark, WAIT);
            results.put("skjson", collect(server.lines(mark), ports).getOrDefault("skjson", Map.of()));
            server.stop();
        }
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, gson.toJson(results));
        return results;
    }

    /** What NeoSkript gives, and its own checks. */
    private Map<String, Map<String, List<String>>> neoskript(ScenarioContext context, Path runs, WebTestServer web,
            Ports ports) throws Exception {
        Path jar = context.officialAddonJars().stream()
                .filter(p -> p.getFileName().toString().startsWith("NeoSkriptWeb-")).findFirst()
                .orElseThrow(() -> new HarnessFailure("NeoSkriptWeb's jar wasn't built"));
        String neoScript = ports.fill(context.script("web-neoskript.sk"));
        // The originals' jars too: they need Skript, so Paper refuses them, and NeoSkriptWeb loads and says to remove
        // them (D21).
        ServerBuilder builder = context.server(runs.resolve("neoskript"))
                .plugin(context.neoskriptJar())
                .plugin(jar)
                .plugin(context.download(REQN.url(), REQN.sha256(), REQN.fileName()))
                .plugin(context.download(SKJSON.url(), SKJSON.sha256(), SKJSON.fileName()))
                .file("plugins/NeoSkriptWeb/config.yml", """
                        timeout-seconds: 3
                        connect-timeout-seconds: 3
                        max-response-kilobytes: 1024
                        max-redirects: 5
                        max-requests-in-flight: 64
                        max-requests-per-minute: 2000
                        block-private-addresses: false
                        """)
                .file("plugins/NeoSkript/scripts/web-reqn.sk", ports.fill(context.script("web-reqn.sk")))
                .file("plugins/NeoSkript/scripts/web-skjson.sk", ports.fill(context.script("web-skjson.sk")))
                .file("plugins/NeoSkript/scripts/web-neoskript.sk", neoScript);
        Path scripts = builder.runDir().resolve("plugins/NeoSkript/scripts");
        Map<String, Map<String, List<String>>> results;
        try (ServerProcess server = builder.start()) {
            // Paper refuses the originals first, in whatever order it lists plugins/; everything waited for comes
            // after both refusals. Paper refuses them for their missing dependency; Folia, first, as they aren't
            // marked as supporting Folia.
            Map<String, Pattern> refusals = new LinkedHashMap<>();
            for (PinnedPlugin original : List.of(REQN, SKJSON)) {
                String name = original == REQN ? "Reqn" : "SkJson";
                refusals.put(name, Pattern.compile("Could not load 'plugins/(\\.paper-remapped/)?"
                        + Pattern.quote(original.fileName() + "'") + "|" + Pattern.quote("Could not load plugin '" + name
                        + " v")));
            }
            Pattern anyRefusal = Pattern.compile(String.join("|", refusals.values().stream().map(Pattern::pattern)
                    .toList()));
            java.util.Set<String> refused = new java.util.HashSet<>();
            int after = 0;
            while (refused.size() < refusals.size()) {
                server.await(anyRefusal, after, WAIT);
                List<String> lines = server.lines(0);
                for (int i = after; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (anyRefusal.matcher(line).find()) {
                        refusals.forEach((name, pattern) -> {
                            if (pattern.matcher(line).find()) {
                                refused.add(name);
                            }
                        });
                        after = i + 1;
                        break;
                    }
                }
            }
            server.await(ServerProcess.DONE, after, WAIT);
            for (PinnedPlugin original : List.of(REQN, SKJSON)) {
                String name = original == REQN ? "Reqn" : "SkJson";
                if (server.linesMatching(Pattern.compile(Pattern.quote("plugins/" + original.fileName() + " is " + name
                        + ", which needs Skript, so it doesn't load with NeoSkript. NeoSkriptWeb replaces it"))).isEmpty()) {
                    throw new HarnessFailure("NeoSkriptWeb didn't say to remove " + original.fileName() + " (D21). See "
                            + server.logFile());
                }
            }
            // Paper's errors about the originals it refused are before this; nothing may be thrown after it.
            int started = server.mark();
            int mark = server.mark();
            server.command("webreqn");
            server.await(Pattern.compile("\\[web] reqn done$"), mark, WAIT);
            server.command("webreqnnone");
            server.await(Pattern.compile("\\[web] reqn none url: "), mark, WAIT);
            server.command("webskjson");
            server.await(Pattern.compile("\\[web] skjson done$"), mark, WAIT);
            results = collect(server.lines(mark), ports);

            int neo = server.mark();
            server.command("webneo");
            server.await(Pattern.compile("\\[web] neo done$"), neo, WAIT);
            // The requests that gave up (slow, silent, endless, too big) closed their connections; the ones that
            // were answered one after another kept one alive, for the next.
            Thread.sleep(2_000);
            int open = web.openConnections();
            if (open > 2) {
                throw new HarnessFailure(open + " connections to the test server are still open after the requests"
                        + " that gave up: they weren't closed. See " + server.logFile());
            }
            context.summary("open connections", open);
            server.command("webneo flood");
            server.await(Pattern.compile("\\[web] neo flood done$"), neo, WAIT);
            // A flood leaves up to max-requests-in-flight connections idle in the JDK client's pool, which closes
            // them once they've been idle for its keep-alive time (30 seconds).
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (web.openConnections() > 2 && System.nanoTime() < deadline) {
                Thread.sleep(1_000);
            }
            if (web.openConnections() > 2) {
                throw new HarnessFailure(web.openConnections() + " connections to the test server were still open 90"
                        + " seconds after a flood of requests. See " + server.logFile());
            }
            context.summary("requests", web.requests());

            // A trigger waiting on a request while its script reloads carries on with its old code.
            int reload = server.mark();
            server.command("webneo hang");
            server.await(Pattern.compile("\\[web] neo hanging$"), reload, WAIT);
            Files.writeString(scripts.resolve("web-neoskript.sk"), neoScript.replace("old code carried on", "new code"));
            server.command("sk reload web-neoskript");
            server.await(Pattern.compile("\\[web] neo after hang: old code carried on$"), reload, WAIT);

            // The server stops cleanly with a request still running.
            int stopping = server.mark();
            server.command("webneo hang");
            server.await(Pattern.compile("\\[web] neo hanging$"), stopping, WAIT);
            long before = System.nanoTime();
            int exit = server.stop();
            long seconds = Duration.ofNanos(System.nanoTime() - before).toSeconds();
            if (exit != 0) {
                throw new HarnessFailure("The server exited with " + exit + " with a request running. See "
                        + server.logFile());
            }
            context.summary("stop seconds", seconds);

            List<String> failed = new ArrayList<>();
            int checks = 0;
            for (String line : server.lines(neo)) {
                Matcher matcher = NEO.matcher(line);
                if (matcher.find() && !matcher.group(1).startsWith("after hang") && !matcher.group(1).equals("hanging")) {
                    checks++;
                    if (!matcher.group(2).equals("ok") && !matcher.group(1).contains("done")) {
                        failed.add(matcher.group(1) + ": " + matcher.group(2));
                    }
                }
            }
            if (!failed.isEmpty()) {
                throw new HarnessFailure("NeoSkriptWeb's own checks failed: " + failed + ". See " + server.logFile());
            }
            context.summary("own checks", checks);
            List<String> thrown = new ArrayList<>(server.lines(started).stream()
                    .filter(line -> EXCEPTION.matcher(line).find()).toList());
            thrown.addAll(server.linesMatching(NeoSkriptRegionsScenario.THREAD_VIOLATION));
            if (!thrown.isEmpty()) {
                throw new HarnessFailure("Exceptions or thread violations reached the log: " + thrown + ". See "
                        + server.logFile());
            }
        }
        return results;
    }

    /** Each suite's labels with their values, in the order they came, ports written as names. */
    private static Map<String, Map<String, List<String>>> collect(List<String> lines, Ports ports) {
        Map<String, Map<String, List<String>>> results = new TreeMap<>();
        for (String line : lines) {
            Matcher matcher = LINE.matcher(line);
            if (matcher.find() && !matcher.group(2).equals("done")) {
                results.computeIfAbsent(matcher.group(1), k -> new LinkedHashMap<>())
                        .computeIfAbsent(matcher.group(2), k -> new ArrayList<>())
                        .add(ports.normalise(matcher.group(3)));
            }
        }
        return results;
    }

    /** A difference NeoSkript makes on purpose: what it gives instead, and why. */
    record Known(String neoskript, String why) {}

    private void compare(ScenarioContext context, Map<String, Map<String, List<String>>> original,
            Map<String, Map<String, List<String>>> ours) throws IOException {
        Map<String, Known> known = known(context);
        List<String> problems = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        int same = 0;
        int differences = 0;
        for (String suite : List.of("reqn", "skjson")) {
            Map<String, List<String>> theirs = original.getOrDefault(suite, Map.of());
            Map<String, List<String>> mine = ours.getOrDefault(suite, Map.of());
            if (theirs.isEmpty()) {
                problems.add(suite + ": the original gave nothing");
            }
            java.util.Set<String> labels = new java.util.TreeSet<>(theirs.keySet());
            labels.addAll(mine.keySet());
            labels.addAll(known.keySet().stream().filter(k -> k.startsWith(suite + " "))
                    .map(k -> k.substring(suite.length() + 1)).toList());
            for (String label : labels) {
                List<String> a = theirs.getOrDefault(label, List.of());
                List<String> b = mine.getOrDefault(label, List.of());
                Known difference = known.get(suite + " " + label);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("original", a);
                entry.put("neoskript", b);
                if (difference == null) {
                    if (!sorted(a).equals(sorted(b))) {
                        problems.add(suite + " " + label + ": the original gave " + a + ", NeoSkript " + b);
                        entry.put("result", "different");
                    } else {
                        same++;
                        entry.put("result", "same");
                    }
                } else {
                    differences++;
                    entry.put("result", "known difference");
                    entry.put("why", difference.why());
                    if (!b.equals(List.of(difference.neoskript()))) {
                        problems.add(suite + " " + label + ": known-differences.txt says NeoSkript gives "
                                + difference.neoskript() + ", it gave " + b);
                    }
                    if (sorted(a).equals(sorted(b))) {
                        problems.add(suite + " " + label + ": listed in known-differences.txt, but now the same as"
                                + " the original: remove it");
                    }
                }
                report.put(suite + " " + label, entry);
            }
        }
        context.writeReport("web-comparison", report);
        context.summary("same", same);
        context.summary("known differences", differences);
        if (!problems.isEmpty()) {
            throw new HarnessFailure("NeoSkriptWeb differs from the originals:\n  " + String.join("\n  ", problems));
        }
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    /** {@code corpus/web/known-differences.txt}: {@code suite label | what NeoSkript gives | why}. */
    private static Map<String, Known> known(ScenarioContext context) throws IOException {
        Path file = context.config().realWorldCorpus().resolveSibling("web").resolve("known-differences.txt");
        Map<String, Known> known = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(" \\| ", 3);
            if (parts.length != 3) {
                throw new HarnessFailure("known-differences.txt: '" + line + "' isn't 'suite label | value | why'");
            }
            known.put(parts[0].strip(), new Known(parts[1].strip(), parts[2].strip()));
        }
        return known;
    }

    private static String cacheKey(ScenarioContext context, Environment reference, String scripts)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(("v" + VERSION + " " + context.referenceSkript().version() + " " + reference.name() + " "
                + reference.build() + " " + REQN.sha256() + " " + SKJSON.sha256() + "\n" + scripts)
                .getBytes(StandardCharsets.UTF_8));
        try {
            digest.update(Files.readAllBytes(context.config().scriptsDir().resolveSibling(
                    "src/main/java/co/xenastudios/neoskript/harness/scenario/WebTestServer.java")));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 24);
    }
}
