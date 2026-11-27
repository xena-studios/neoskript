/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import static co.xenastudios.neoskript.harness.scenario.NeoSkriptProxyScenario.WAIT;
import static co.xenastudios.neoskript.harness.scenario.NeoSkriptProxyScenario.docker;
import static co.xenastudios.neoskript.harness.scenario.NeoSkriptProxyScenario.join;
import static co.xenastudios.neoskript.harness.scenario.NeoSkriptProxyScenario.line;
import static co.xenastudios.neoskript.harness.scenario.NeoSkriptProxyScenario.started;

import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import co.xenastudios.neoskript.harness.VelocityProxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Network variables ({@code {network::...}}) on a real network: two NeoSkript servers with NeoSkriptProxy behind a
 * Velocity proxy, sharing one MariaDB (in Docker, pinned by digest) through NeoSkript's {@code databases} section
 * with {@code monitor-changes} and a {@code monitor-interval} of five minutes, so only NeoSkriptProxy's notices can
 * make a change show on the other server sooner. Notices go over Redis (in Docker) and, while Redis is stopped, over
 * plugin messaging through the players online.
 *
 * <p>Checks that a change on one server shows on the other in seconds; that 500 additions on each server at once
 * come to 1000 on both; that 30 values added to one list on each server at once are all kept; that additions on both
 * servers while MariaDB is killed are all there once it's back; and that the list's rows are all in the database.
 */
public final class NetworkVariablesScenario implements Scenario {

    /** Each server's monitor interval: far longer than any check here waits. */
    private static final String MONITOR_INTERVAL = "5 minutes";
    /** How soon a change must show on the other server: well under the monitor interval. */
    private static final Duration TOLD_WITHIN = Duration.ofSeconds(30);
    private static final String SECRET = "harness-network-secret";

    @Override
    public String name() {
        return "neoskript-network-variables";
    }

    @Override
    public Group group() {
        // Docker twice, a proxy and two servers: weekly, and when asked.
        return Group.BASELINE;
    }

    @Override
    public boolean supports(co.xenastudios.neoskript.harness.Environment environment) {
        return environment.minecraftVersion().equals("26.3");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Path addon = context.officialAddonJars().stream()
                .filter(jar -> jar.getFileName().toString().startsWith("NeoSkriptProxy-")).findFirst()
                .orElseThrow(() -> new HarnessFailure("NeoSkriptProxy's jar wasn't given to the harness"));
        if (!NeoSkriptProxyScenario.dockerAvailable() || !MySqlServer.available()) {
            context.summary("skipped", "Docker isn't available");
            return;
        }
        int proxyPort = ServerBuilder.freePort();
        int portB = ServerBuilder.freePort();
        int portC = ServerBuilder.freePort();
        int redisPort = ServerBuilder.freePort();
        String redis = "neoskript-harness-redis-" + UUID.randomUUID();
        docker("run", "-d", "--name", redis, "-p", "127.0.0.1:" + redisPort + ":6379",
                NeoSkriptProxyScenario.REDIS_IMAGE);
        try (MySqlServer mysql = MySqlServer.start()) {
            NeoSkriptProxyScenario.awaitRedis(redis);
            ServerBuilder b = server(context, runs.resolve("b"), portB, addon, mysql, redisPort).plugin(context.botJar());
            ServerBuilder c = server(context, runs.resolve("c"), portC, addon, mysql, redisPort);
            Map<String, Integer> servers = new LinkedHashMap<>();
            servers.put("b", portB);
            servers.put("c", portC);
            // One after another: servers of an environment unpack it into one cache, which two at once can garble.
            try (ServerProcess serverB = started(b, null); ServerProcess serverC = started(c, null);
                    ServerProcess proxy = VelocityProxy.start(context, runs.resolve("velocity"), proxyPort, servers)) {
                proxy.await(VelocityProxy.DONE, WAIT);
                // Players carry plugin messages, for when Redis is stopped.
                join(serverB, serverB, "Bob", proxyPort, "b");
                join(serverB, serverC, "Carol", proxyPort, "c");
                Thread.sleep(2_000);
                awaitState(serverB, "available");
                awaitState(serverC, "available");

                // A change on b shows on c in seconds, though c reads the database only every five minutes.
                long start = System.nanoTime();
                serverB.command("nvset motd hello");
                awaitValue(serverC, "motd", "hello", TOLD_WITHIN);
                long told = (System.nanoTime() - start) / 1_000_000;
                context.summary("told over Redis within ms", told);

                // 500 additions on each server at once, their writes crossing: every one counts, on both.
                both(serverB, serverC, "nvadd coins 1 500", "[nv] added coins");
                awaitValue(serverB, "coins", "1000", WAIT);
                awaitValue(serverC, "coins", "1000", WAIT);
                // Not by luck, one write after the other: the servers' writes met, and were merged.
                long merged = merged(serverB) + merged(serverC);
                if (merged == 0) {
                    throw new HarnessFailure("The additions came to 1000 without a single merged write: the check"
                            + " didn't exercise two servers' writes meeting");
                }
                context.summary("writes merged", merged);

                // 30 values added to one list on each server at once, at the same indices: all 60 are kept.
                int markB = serverB.mark();
                int markC = serverC.mark();
                serverB.command("nvappend queue b 30");
                serverC.command("nvappend queue c 30");
                serverB.await(line("[nv] appended queue"), markB, WAIT);
                serverC.await(line("[nv] appended queue"), markC, WAIT);
                awaitSize(serverB, "queue", 60);
                awaitSize(serverC, "queue", 60);

                // MariaDB killed: both servers keep running and adding, and once it's back, every addition counts.
                mysql.kill();
                both(serverB, serverC, "nvadd coins 1 50", "[nv] added coins");
                awaitState(serverB, "not available");
                awaitState(serverC, "not available");
                mysql.restart();
                awaitValue(serverB, "coins", "1100", WAIT);
                awaitValue(serverC, "coins", "1100", WAIT);
                awaitState(serverB, "available");

                // Redis stopped: the notice goes through a player, over plugin messaging, instead.
                docker("stop", redis);
                Thread.sleep(3_000);
                start = System.nanoTime();
                serverB.command("nvset motd via-plugin-messaging");
                awaitValue(serverC, "motd", "via-plugin-messaging", TOLD_WITHIN);
                context.summary("told over plugin messaging within ms", (System.nanoTime() - start) / 1_000_000);
                docker("start", redis);

                // What the database has: the 60 values, each in a row of its own.
                int rows;
                try (Connection connection = mysql.connect(); Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM variables21 WHERE name LIKE"
                                + " 'network::queue::%' AND value IS NOT NULL")) {
                    result.next();
                    rows = result.getInt(1);
                }
                if (rows != 60) {
                    throw new HarnessFailure("The database has " + rows + " rows for {network::queue::*}, not 60");
                }

                for (ServerProcess server : List.of(serverB, serverC)) {
                    // The console says MariaDB went and came back; nothing else may be an error.
                    List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR).stream()
                            .filter(error -> !error.contains("database 'network'")).toList();
                    if (!errors.isEmpty()) {
                        throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
                    }
                }
                proxy.command("shutdown");
                proxy.awaitExit(Duration.ofSeconds(60));
                serverB.stop();
                serverC.stop();
                context.summary("coins", 1100);
                context.summary("queue rows", rows);
            }
        } finally {
            docker("rm", "-f", redis);
        }
    }

    private static ServerBuilder server(ScenarioContext context, Path dir, int port, Path addon, MySqlServer mysql,
            int redisPort) throws Exception {
        return context.server(dir).port(port)
                .plugin(context.neoskriptJar()).plugin(addon)
                .file("plugins/NeoSkript/config.yml", neoskriptConfig(mysql))
                .file("plugins/NeoSkript/scripts/network-variables.sk",
                        context.script("neoskript-network-variables.sk"))
                .file("plugins/NeoSkriptProxy/config.yml", """
                        secret: "%s"
                        redis:
                          enabled: true
                          host: "127.0.0.1"
                          port: %d
                        """.formatted(SECRET, redisPort));
    }

    /** The documented setup: network variables in MariaDB, the rest in variables.csv. */
    private static String neoskriptConfig(MySqlServer mysql) {
        return String.join("\n",
                "databases:",
                "  network:",
                "    type: mysql",
                "    pattern: \"network::.*\"",
                "    monitor-changes: true",
                "    monitor-interval: " + MONITOR_INTERVAL,
                "    host: 127.0.0.1",
                "    port: " + mysql.port(),
                "    user: " + MySqlServer.USER,
                "    password: \"" + MySqlServer.PASSWORD + "\"",
                "    database: " + MySqlServer.DATABASE,
                "    table: variables21",
                "  default:",
                "    type: csv",
                "    pattern: \".*\"",
                "    file: variables.csv",
                "");
    }

    /** How many writes {@code /sk info} says were merged with another server's, on {@code server}. */
    private static long merged(ServerProcess server) throws Exception {
        Pattern count = Pattern.compile("\\((\\d+) merged\\)");
        int mark = server.mark();
        server.command("sk info");
        server.await(Pattern.compile("Database network"), mark, WAIT);
        for (String l : server.lines(mark)) {
            java.util.regex.Matcher m = count.matcher(l);
            if (l.contains("Database network") && m.find()) {
                return Long.parseLong(m.group(1));
            }
        }
        return 0;
    }

    /** Runs {@code command} on both servers at once, and waits for each to print {@code done}. */
    private static void both(ServerProcess one, ServerProcess two, String command, String done) throws Exception {
        int markOne = one.mark();
        int markTwo = two.mark();
        one.command(command);
        two.command(command);
        one.await(line(done), markOne, WAIT);
        two.await(line(done), markTwo, WAIT);
    }

    /** Asks for {@code {network::<name>}} until it's {@code expected}. */
    private static void awaitValue(ServerProcess server, String name, String expected, Duration within)
            throws Exception {
        awaitAnswer(server, "nvget " + name, "[nv] " + name + " = " + expected, within);
    }

    private static void awaitSize(ServerProcess server, String name, int expected) throws Exception {
        awaitAnswer(server, "nvsize " + name, "[nv] size " + name + " = " + expected, WAIT);
    }

    /** Asks whether network variables are available until the answer is {@code expected}. */
    private static void awaitState(ServerProcess server, String expected) throws Exception {
        awaitAnswer(server, "nvstate", "[nv] " + expected, WAIT);
    }

    private static void awaitAnswer(ServerProcess server, String command, String expected, Duration within)
            throws Exception {
        Pattern wanted = line(expected);
        Pattern any = Pattern.compile("\\[nv] .*$");
        long deadline = System.nanoTime() + within.toNanos();
        String last = "nothing";
        while (System.nanoTime() < deadline) {
            int mark = server.mark();
            server.command(command);
            long until = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < until) {
                List<String> lines = server.lines(mark);
                if (lines.stream().anyMatch(l -> wanted.matcher(l).find())) {
                    return;
                }
                for (String l : lines) {
                    java.util.regex.Matcher m = any.matcher(l);
                    if (m.find()) {
                        last = m.group();
                    }
                }
                Thread.sleep(100);
            }
        }
        throw new HarnessFailure("Expected \"" + expected + "\" from /" + command + " within " + within.toSeconds()
                + " s, last \"" + last + "\". See " + server.logFile());
    }
}
