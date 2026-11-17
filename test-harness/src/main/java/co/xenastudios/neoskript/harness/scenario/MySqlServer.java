/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.HarnessFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A MariaDB server in Docker, pinned by digest, for the MySQL scenarios: started fresh, killed and started again to
 * stand for an outage, and removed at the end. Its data lives in the container, so it survives a kill.
 */
final class MySqlServer implements AutoCloseable {

    /** MariaDB 11.4 (LTS), which has the ucs2 character set Skript's table uses. */
    static final String IMAGE =
            "mariadb:11.4@sha256:5e73baf8179b29f115101bb102763a47a017361f3e9792daccba6646c3ab1532";
    static final String DATABASE = "skript";
    static final String USER = "skript";
    static final String PASSWORD = "harness-secret-7f3c";

    private final String container;
    private int port;

    private MySqlServer(String container) {
        this.container = container;
    }

    /** Whether Docker is there to run one. */
    static boolean available() {
        try {
            return run(Duration.ofSeconds(20), "docker", "info").exit == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    static MySqlServer start() throws IOException, InterruptedException, SQLException {
        String name = "neoskript-harness-mysql-" + ProcessHandle.current().pid() + "-" + System.nanoTime() % 100000;
        int port;
        // A fixed port, so it's the same after a restart.
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Result started = run(Duration.ofMinutes(5), "docker", "run", "-d", "--name", name, "-p",
                "127.0.0.1:" + port + ":3306",
                "-e", "MARIADB_ROOT_PASSWORD=" + PASSWORD, "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER, "-e", "MARIADB_PASSWORD=" + PASSWORD, IMAGE);
        if (started.exit != 0) {
            throw new HarnessFailure("Couldn't start MariaDB in Docker: " + started.output);
        }
        MySqlServer server = new MySqlServer(name);
        try {
            server.awaitUp();
        } catch (IOException | SQLException | RuntimeException e) {
            server.close();
            throw e;
        }
        return server;
    }

    int port() {
        return port;
    }

    String url() {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url(), USER, PASSWORD);
    }

    /** Kills the server, as a crash or a pulled cable would: no goodbye to the clients. */
    void kill() throws IOException, InterruptedException {
        run(Duration.ofMinutes(1), "docker", "kill", container);
    }

    /** Pauses it: connections still open, but nothing answers, as a database that hangs. */
    void pause() throws IOException, InterruptedException {
        run(Duration.ofMinutes(1), "docker", "pause", container);
    }

    void unpause() throws IOException, InterruptedException {
        run(Duration.ofMinutes(1), "docker", "unpause", container);
    }

    /** Starts it again after a kill, with its data. */
    void restart() throws IOException, InterruptedException, SQLException {
        Result result = run(Duration.ofMinutes(1), "docker", "start", container);
        if (result.exit != 0) {
            throw new HarnessFailure("Couldn't start MariaDB again: " + result.output);
        }
        awaitUp();
    }

    private void awaitUp() throws IOException, InterruptedException, SQLException {
        Result mapped = run(Duration.ofSeconds(30), "docker", "port", container, "3306/tcp");
        String text = mapped.output.strip().lines().findFirst().orElse("");
        port = Integer.parseInt(text.substring(text.lastIndexOf(':') + 1).strip());
        // The first start initialises the database, which can take a few minutes on a busy machine.
        long until = System.currentTimeMillis() + 300_000;
        SQLException last = null;
        while (System.currentTimeMillis() < until) {
            try (Connection c = connect()) {
                return;
            } catch (SQLException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw last;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        run(Duration.ofMinutes(1), "docker", "unpause", container);
        run(Duration.ofMinutes(1), "docker", "rm", "-f", "-v", container);
    }

    private record Result(int exit, String output) {}

    private static Result run(Duration timeout, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of(command)).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try {
                for (String line : new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .split("\n")) {
                    lines.add(line);
                }
            } catch (IOException e) {
                // The process is gone.
            }
        });
        reader.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException(String.join(" ", command) + " took longer than " + timeout);
        }
        reader.join(5_000);
        return new Result(process.exitValue(), String.join("\n", lines));
    }
}
