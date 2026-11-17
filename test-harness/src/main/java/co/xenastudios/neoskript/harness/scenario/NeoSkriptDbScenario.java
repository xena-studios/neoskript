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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * neoskript-db against the original: {@code scripts/neoskript-db.sk} runs on Skript 2.17.0 with skript-db 2.0.0
 * (pinned by SHA-256) and on NeoSkript with NeoSkriptDb, each against a fresh SQLite file. What the script prints,
 * the load warnings and errors of {@code neoskript-db-errors.sk}, and the tables afterwards must match, and match
 * {@code neoskript-db.expected}, which is also what Folia and Paper 1.21.4 (no Skript) are held to.
 *
 * <p>NeoSkript only: a statement in an {@code on unload} trigger is written as the server stops (skript-db's is
 * lost: it stops before Skript runs the trigger), {@code synchronously}, a console command's trigger, a slow
 * statement whose script is reloaded while it runs, and no password in the console.
 */
public final class NeoSkriptDbScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    /** skript-db 2.0.0, the fork that runs on Paper 26.x, from Hangar. */
    private static final String SKRIPT_DB_URL =
            "https://hangarcdn.papermc.io/plugins/4w3/skript-db/versions/2.0.0/PAPER/skript-db-2.0.0.jar";
    private static final String SKRIPT_DB_SHA256 = "ab7b06f2d503163471860ef4e129a1335ee3933760b9818fc5fbf481345e90dc";
    private static final Pattern CHECK = Pattern.compile("\\[dbtest] .*$");
    private static final Pattern ERRORS_SCRIPT = Pattern.compile("\\(neoskript-db-errors\\.sk\\)");
    /** A console line's prefix: the time and level, then Skript's or NeoSkript's tag. */
    private static final Pattern PREFIX = Pattern.compile("^\\[\\d\\d:\\d\\d:\\d\\d [A-Z]+]: (\\[(Neo)?Skript] )?");

    /** What one server printed and left in the database. */
    private record Run(List<String> checks, List<String> loadMessages, List<String> tables, boolean unloadWritten) {}

    @Override
    public String name() {
        return "neoskript-db";
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
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Path addon = context.officialAddonJars().stream()
                .filter(jar -> jar.getFileName().toString().startsWith("NeoSkriptDb-")).findFirst()
                .orElseThrow(() -> new HarnessFailure("NeoSkriptDb's jar wasn't given to the harness"));
        // The original first, where it runs: it's what NeoSkriptDb is held to.
        Run skript = context.environment().skriptReference() ? run(context, runs.resolve("skript"),
                context.skriptJar(), context.download(SKRIPT_DB_URL, SKRIPT_DB_SHA256, "skript-db-2.0.0.jar"),
                "Skript") : null;
        Run neoskript = run(context, runs.resolve("neoskript"), context.neoskriptJar(), addon, "NeoSkript");
        List<String> expected = Files.readAllLines(context.config().scriptsDir().resolve("neoskript-db.expected"));
        List<String> got = report(neoskript);
        if (skript != null) {
            List<String> original = report(skript);
            context.summary("skript-db's unload write", skript.unloadWritten());
            if (!original.equals(got)) {
                Files.write(runs.resolve("skript.txt"), original);
                Files.write(runs.resolve("neoskript.txt"), got);
                throw new HarnessFailure("NeoSkriptDb and skript-db differ. Compare " + runs.resolve("neoskript.txt")
                        + " with " + runs.resolve("skript.txt"));
            }
            context.summary("matches skript-db", true);
        }
        if (!expected.equals(got)) {
            Files.write(runs.resolve("neoskript.txt"), got);
            throw new HarnessFailure("NeoSkriptDb's results aren't those in scripts/neoskript-db.expected. Compare "
                    + runs.resolve("neoskript.txt") + " with it");
        }
        if (!neoskript.unloadWritten()) {
            throw new HarnessFailure("The on unload trigger's statement wasn't written as the server stopped");
        }
        context.summary("checks", neoskript.checks().size());
    }

    /** One run as lines to compare: the checks, the load messages, then the tables. */
    private static List<String> report(Run run) {
        List<String> lines = new ArrayList<>(run.checks());
        run.loadMessages().forEach(line -> lines.add("[load] " + line));
        run.tables().forEach(line -> lines.add("[table] " + line));
        return lines;
    }

    private Run run(ScenarioContext context, Path runDir, Path plugin, Path addon, String folder) throws Exception {
        boolean neo = folder.equals("NeoSkript");
        var builder = context.server(runDir)
                .plugin(plugin)
                .plugin(addon)
                .file("plugins/dbtest/README.txt", "The neoskript-db scenario's SQLite file goes here.\n")
                .file("plugins/" + folder + "/scripts/neoskript-db.sk", context.script("neoskript-db.sk"))
                .file("plugins/" + folder + "/scripts/neoskript-db-errors.sk", context.script("neoskript-db-errors.sk"));
        if (neo) {
            builder.file("plugins/NeoSkript/scripts/neoskript-db-extra.sk", context.script("neoskript-db-extra.sk"));
        }
        List<String> checks;
        List<String> loadMessages;
        try (ServerProcess server = builder.start()) {
            server.await(line("[dbtest] done"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            if (neo) {
                extras(server);
            }
            checks = server.linesMatching(CHECK).stream().map(CHECK::matcher).filter(Matcher::find)
                    .map(Matcher::group).toList();
            loadMessages = loadMessages(server.lines(0));
            server.stop();
            List<String> leaked = server.linesMatching(Pattern.compile("hunter22"));
            if (neo && !leaked.isEmpty()) {
                throw new HarnessFailure("A password reached the console: " + leaked + ". See " + server.logFile());
            }
            if (neo) {
                List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
                if (!errors.isEmpty()) {
                    throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
                }
            }
        }
        Path database = runDir.resolve("plugins/dbtest/test.db");
        if (!Files.exists(database)) {
            throw new HarnessFailure(folder + " left no database at " + database);
        }
        List<String> tables = new ArrayList<>();
        boolean unload;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            tables.addAll(dump(connection, "SELECT id, name, score, ratio FROM players ORDER BY id"));
            unload = !dump(connection, "SELECT what FROM log WHERE what = 'unload'").isEmpty();
        }
        return new Run(checks, loadMessages, tables, unload);
    }

    /** NeoSkript only: synchronously, a console command, a reload while a statement runs, a masked password. */
    private static void extras(ServerProcess server) throws Exception {
        int mark = server.mark();
        server.command("dbextra sync");
        server.await(line("[dbextra] sync 7"), mark, WAIT);
        server.command("dbextra shown");
        server.await(line("[dbextra] shown jdbc:mysql://localhost:1/x?user=admin&password=***"), mark, WAIT);
        server.command("dbextra slow");
        server.await(line("[dbextra] slow start"), mark, WAIT);
        // Reloaded while the statement runs: the trigger carries on with its old code.
        server.command("sk reload neoskript-db-extra.sk");
        server.await(line("[dbextra] slow done 3000000"), mark, WAIT);
    }

    /** The warnings and errors loading neoskript-db-errors.sk gave: each one's line, message and code. */
    private static List<String> loadMessages(List<String> lines) {
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (ERRORS_SCRIPT.matcher(lines.get(i)).find()) {
                for (int j = i; j < Math.min(i + 3, lines.size()); j++) {
                    messages.add(PREFIX.matcher(lines.get(j)).replaceFirst("").strip());
                }
            }
        }
        return messages;
    }

    private static List<String> dump(Connection connection, String query) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                StringBuilder row = new StringBuilder();
                for (int column = 1; column <= columns; column++) {
                    row.append(column == 1 ? "" : " | ").append(result.getString(column));
                }
                rows.add(row.toString());
            }
        } catch (SQLException noTable) {
            if (noTable.getMessage() != null && noTable.getMessage().contains("no such table")) {
                rows.add("(no table: " + query + ")");
                return rows;
            }
            throw noTable;
        }
        return rows;
    }

    private static Pattern line(String text) {
        return Pattern.compile(Pattern.quote(text) + "$");
    }
}
