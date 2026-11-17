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
import co.xenastudios.neoskript.harness.VelocityProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * neoskript-proxy on a real network: a Velocity 4.3.1 proxy (pinned by SHA-256) in front of three servers, and one
 * Redis (pinned by digest, in Docker). Server {@code a} runs Skript 2.17.0 with the community addons, SkProxy 2.4,
 * Skelocity 1.1 and SkRedis 2.3.5 (each pinned); {@code b} and {@code c} run NeoSkript with NeoSkriptProxy, on this
 * scenario's environment (Folia's servers sit behind the same proxy as Paper's). Players join through the proxy,
 * as clients do, from the harness bot.
 *
 * <p>{@code a} and {@code b} run the same script ({@code scripts/neoskript-proxy.sk}) and must print the same, with
 * Redis messages going both ways between SkRedis and NeoSkriptProxy. {@code b} and {@code c} check NeoSkriptProxy's
 * own syntax, a message signed and forwarded through the proxy, questions with nobody online refused, a message
 * that waited for a player, Redis going down and back with a message waiting, and, with SkProxy's jar in
 * {@code c}'s plugins, D21's warning.
 */
public final class NeoSkriptProxyScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final String REDIS_IMAGE =
            "redis@sha256:02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952";
    private static final List<String[]> ORIGINALS = List.of(
            new String[] {"https://cdn.modrinth.com/data/HaSv4ey2/versions/awO490UD/SkProxy-2.4.jar",
                    "2beebccd6c62b977ccf31a0e014bb35cb4edecc40b3c3a2656a6110bf54ad04f", "SkProxy-2.4.jar"},
            new String[] {"https://cdn.modrinth.com/data/URMrn6g1/versions/SZo2l06P/Skelocity-1.0.jar",
                    "0f0045172c6adc69ecd6aef9bcbe1b496801e6aae471b17ddfd019a85f341367", "Skelocity-1.1.jar"},
            new String[] {"https://cdn.modrinth.com/data/3R33dfbs/versions/8UvBzK3L/SkRedis-2.3.5.jar",
                    "5ffa8aab2cdb6d8bd0629f11885d8b81405d2c39e4086ca52230fbdae82037de", "SkRedis-2.3.5.jar"});
    private static final Pattern CHECK = Pattern.compile("\\[proxytest] .*$");
    private static final Pattern FIRST = Pattern.compile("\\[proxyfirst] .*$");
    private static final String SECRET = "harness-network-secret";

    @Override
    public String name() {
        return "neoskript-proxy";
    }

    @Override
    public Group group() {
        // Docker, a proxy and three servers: weekly, and when asked.
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.minecraftVersion().equals("26.3");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Environment paper = Environment.loadAll(context.config().environmentsDir()).stream()
                .filter(environment -> environment.name().equals("paper-26.3")).findFirst()
                .orElseThrow(() -> new HarnessFailure("No paper-26.3 environment for Skript's server"));
        Path addon = context.officialAddonJars().stream()
                .filter(jar -> jar.getFileName().toString().startsWith("NeoSkriptProxy-")).findFirst()
                .orElseThrow(() -> new HarnessFailure("NeoSkriptProxy's jar wasn't given to the harness"));
        List<Path> originals = new ArrayList<>();
        for (String[] original : ORIGINALS) {
            originals.add(context.download(original[0], original[1], original[2]));
        }
        int proxyPort = ServerBuilder.freePort();
        int portA = ServerBuilder.freePort();
        int portB = ServerBuilder.freePort();
        int portC = ServerBuilder.freePort();
        int redisPort = ServerBuilder.freePort();
        String redis = "neoskript-harness-redis-" + UUID.randomUUID();
        docker("run", "-d", "--name", redis, "-p", "127.0.0.1:" + redisPort + ":6379", REDIS_IMAGE);
        try {
            awaitRedis(redis);
            ServerBuilder a = context.server(paper, runs.resolve("a")).port(portA)
                    .plugin(context.skriptJar()).plugin(context.botJar())
                    .file("plugins/Skript/scripts/neoskript-proxy.sk", context.script("neoskript-proxy.sk"))
                    .file("plugins/SkRedis/config.yml", skRedisConfig(redisPort));
            originals.forEach(a::plugin);
            ServerBuilder b = context.server(runs.resolve("b")).port(portB)
                    .plugin(context.neoskriptJar()).plugin(addon)
                    .file("plugins/NeoSkript/scripts/neoskript-proxy.sk", context.script("neoskript-proxy.sk"))
                    .file("plugins/NeoSkript/scripts/neoskript-proxy-own.sk", context.script("neoskript-proxy-own.sk"))
                    .file("plugins/NeoSkriptProxy/config.yml", config(true, redisPort));
            ServerBuilder c = context.server(runs.resolve("c")).port(portC)
                    .plugin(context.neoskriptJar()).plugin(addon).plugin(originals.getFirst())
                    .file("plugins/NeoSkript/scripts/neoskript-proxy-own.sk", context.script("neoskript-proxy-own.sk"))
                    .file("plugins/NeoSkriptProxy/config.yml", config(false, redisPort));
            Map<String, Integer> servers = new LinkedHashMap<>();
            servers.put("a", portA);
            servers.put("b", portB);
            servers.put("c", portC);
            try (ServerProcess serverA = a.start(); ServerProcess serverB = b.start(); ServerProcess serverC = c.start();
                    ServerProcess proxy = VelocityProxy.start(context, runs.resolve("velocity"), proxyPort, servers)) {
                // SkProxy doesn't say it needs Skript, so Paper loads it on c, and it fails to enable without Skript.
                serverC.allowFailure(Pattern.compile("Error occurred while enabling SkProxy"));
                for (ServerProcess server : List.of(serverA, serverB, serverC)) {
                    server.await(ServerProcess.DONE, WAIT);
                }
                proxy.await(VelocityProxy.DONE, WAIT);
                // D21: SkProxy's jar is in c's plugins; it can't work without Skript, so NeoSkriptProxy loads.
                serverC.await(Pattern.compile(Pattern.quote("is SkProxy, which needs Skript, so it doesn't load with"
                        + " NeoSkript. NeoSkriptProxy replaces it")), WAIT);

                // Players join through the proxy, each to its server by its forced host.
                join(serverA, serverA, "Alice", proxyPort, "a");
                join(serverA, serverB, "Bob", proxyPort, "b");
                join(serverA, serverC, "Carol", proxyPort, "c");
                // The proxy opens the BungeeCord channel on each connection just after it joins.
                Thread.sleep(2_000);

                // The same script on Skript with the originals and on NeoSkript.
                int markA = serverA.mark();
                int markB = serverB.mark();
                serverA.command("proxytest");
                serverA.await(line("[proxytest] done"), markA, WAIT);
                serverB.command("proxytest");
                serverB.await(line("[proxytest] done"), markB, WAIT);
                // Each server hears both servers' Redis messages.
                for (ServerProcess server : List.of(serverA, serverB)) {
                    awaitCount(server, line("[proxytest] redis ping on net"), 2);
                    awaitCount(server, line("[proxytest] custom {\"kind\":\"custom\"} on net"), 2);
                }
                List<String> original = checks(serverA, markA);
                List<String> ours = checks(serverB, markB);
                context.summary("skproxy's first answers, original and ours",
                        first(serverA, markA) + " / " + first(serverB, markB));
                if (!original.equals(ours)) {
                    Files.write(runs.resolve("original.txt"), original);
                    Files.write(runs.resolve("neoskript.txt"), ours);
                    throw new HarnessFailure("NeoSkriptProxy and the originals differ. Compare " + runs.resolve("neoskript.txt")
                            + " with " + runs.resolve("original.txt"));
                }
                expect(ours, "[proxytest] skproxy b 1, status Online, total 3", "[proxytest] status of nowhere Offline",
                        "[proxytest] skelocity b 1, on a Alice");
                // A player's own server: a on a, b on b.
                if (!side(serverA, markA).equals("[proxyside] a") || !side(serverB, markB).equals("[proxyside] b")) {
                    throw new HarnessFailure("A player's server: " + side(serverA, markA) + " on a, " + side(serverB, markB)
                            + " on b");
                }

                // NeoSkriptProxy's own syntax, from c to b, signed and forwarded through the proxy.
                int markC = serverC.mark();
                markB = serverB.mark();
                serverC.command("neotest");
                serverC.await(line("[neotest] sent"), markC, WAIT);
                serverB.await(line("[neotest] got hello from c on events"), markB, WAIT);
                List<String> own = serverC.lines(markC).stream().map(Pattern.compile("\\[neotest] .*$")::matcher)
                        .filter(Matcher::find).map(Matcher::group).toList();
                expect(own, "[neotest] servers a, b and c", "[neotest] count 3 on b 1",
                        "[neotest] Bob is on b, nobody on <none>", "[neotest] this is c");

                // Nobody on c: questions are refused at once, and a message waits for the next player.
                serverA.command("bot quit Carol");
                serverC.await(Pattern.compile("Carol left the game"), markC, WAIT);
                // The quit event comes before the player is gone from the server's list.
                Thread.sleep(1_000);
                markC = serverC.mark();
                serverC.command("neoquiet");
                serverC.await(Pattern.compile(Pattern.quote("Nobody is online, so the proxy can't be asked")), markC, WAIT);
                serverC.await(line("[neotest] asked with nobody online: 0"), markC, WAIT);
                markB = serverB.mark();
                Thread.sleep(2_000);
                if (!serverB.linesMatching(line("[neotest] got waited from c on events")).isEmpty()) {
                    throw new HarnessFailure("The message from c arrived with nobody on c to carry it");
                }
                join(serverA, serverC, "Carol", proxyPort, "c");
                serverB.await(line("[neotest] got waited from c on events"), markB, WAIT);

                // Redis goes away with a message waiting on b; it reaches SkRedis on a once Redis is back.
                docker("stop", redis);
                markA = serverA.mark();
                serverB.command("redissend while-down");
                Thread.sleep(3_000);
                docker("start", redis);
                serverA.await(line("[proxytest] redis while-down on net"), markA, WAIT);

                for (ServerProcess server : List.of(serverB, serverC)) {
                    List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
                    if (!errors.isEmpty()) {
                        throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
                    }
                }
                proxy.command("shutdown");
                proxy.awaitExit(Duration.ofSeconds(60));
                serverB.stop();
                serverC.stop();
                serverA.stop();
                context.summary("checks", original.size() + own.size());
            }
        } finally {
            docker("rm", "-f", redis);
        }
    }

    /** A bot joins through the proxy from server {@code via}'s bot plugin, for {@code server}'s forced host. */
    private static void join(ServerProcess via, ServerProcess server, String name, int proxyPort, String serverName)
            throws Exception {
        int mark = server.mark();
        via.command("bot connect " + name + " " + proxyPort + " " + serverName + ".test");
        server.await(Pattern.compile(Pattern.quote(name) + " joined the game"), mark, WAIT);
    }

    /** The checks a server printed since {@code mark}: in order, with Redis messages (which race) sorted last. */
    private static List<String> checks(ServerProcess server, int mark) {
        List<String> lines = server.lines(mark).stream().map(CHECK::matcher).filter(Matcher::find).map(Matcher::group)
                .toList();
        List<String> ordered = new ArrayList<>(lines.stream()
                .filter(l -> !l.startsWith("[proxytest] redis ") && !l.startsWith("[proxytest] custom ")).toList());
        ordered.addAll(lines.stream().filter(l -> l.startsWith("[proxytest] redis ") || l.startsWith("[proxytest] custom "))
                .sorted().toList());
        return ordered;
    }

    private static String side(ServerProcess server, int mark) {
        Pattern side = Pattern.compile("\\[proxyside] .*$");
        return server.lines(mark).stream().map(side::matcher).filter(Matcher::find).map(Matcher::group).findFirst()
                .orElse("none");
    }

    private static String first(ServerProcess server, int mark) {
        return server.lines(mark).stream().map(FIRST::matcher).filter(Matcher::find).map(Matcher::group).findFirst()
                .orElse("none");
    }

    private static void expect(List<String> got, String... wanted) {
        for (String line : wanted) {
            if (!got.contains(line)) {
                throw new HarnessFailure("Expected \"" + line + "\" in " + got);
            }
        }
    }

    private static void awaitCount(ServerProcess server, Pattern pattern, int count) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (server.linesMatching(pattern).size() < count) {
            if (System.nanoTime() > deadline) {
                throw new HarnessFailure("Expected " + count + " lines matching " + pattern + ". See " + server.logFile());
            }
            Thread.sleep(100);
        }
    }

    private static String config(boolean redis, int redisPort) {
        return """
                secret: "%s"
                plugin-messaging:
                  timeout: 3000
                redis:
                  enabled: %s
                  host: "127.0.0.1"
                  port: %d
                """.formatted(SECRET, redis, redisPort);
    }

    private static String skRedisConfig(int redisPort) {
        return """
                config-version: 1
                update-check:
                  enabled: false
                  notify-admins: false
                  check-interval: 3600
                redis:
                  enable-default-connection: true
                  host: "127.0.0.1"
                  port: %d
                  username: ""
                  password: ""
                  database: 0
                  connectionPoolSize: 10
                  connectionTimeout: 2000
                  ssl: false
                  allowSelfSignedCertificates: false
                  trustStorePath: ""
                  trustStorePassword: ""
                """.formatted(redisPort);
    }

    private static void awaitRedis(String container) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (docker("logs", container).contains("Ready to accept connections")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new HarnessFailure("Redis didn't start in Docker");
    }

    private static String docker(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly();
            if (!arguments[0].equals("rm")) {
                throw new HarnessFailure("docker " + String.join(" ", arguments) + " failed: " + output);
            }
        }
        return output;
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
