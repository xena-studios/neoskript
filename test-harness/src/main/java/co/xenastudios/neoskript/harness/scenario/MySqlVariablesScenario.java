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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * MySQL variable storage against a real database (MariaDB in Docker), with Skript 2.17.0 and SQLibrary on the other
 * side. Skipped where Docker isn't available.
 *
 * <ol>
 *   <li>Skript and NeoSkript take turns on the same table, each reading what the other wrote, items and locations
 *       included, in Skript's exact layout ({@code ucs2}).
 *   <li>Two NeoSkript servers at once with {@code monitor changes}: each sees the other's changes and deletions.
 *   <li>One NeoSkript and one Skript server at once: each sees the other's changes.
 *   <li>The database killed in the middle of writes, and while the server stops; a wrong password; rows NeoSkript
 *       can't read. Nothing is lost, the server never stalls, and the password never shows in the console.
 * </ol>
 *
 * <p>Skript's MySQL storage can't work on current Paper as it ships: SQLibrary asks for
 * {@code com.mysql.jdbc.jdbc2.optional.MysqlDataSource}, which Paper's MySQL driver (9.x) doesn't have. Here the
 * reference Skript gets the last driver that has it, 5.1.49 from Maven Central, wrapped as a plugin.
 */
public final class MySqlVariablesScenario implements Scenario {

    private static final Duration WAIT = SqlVariablesScenario.WAIT;
    private static final String DRIVER_URL =
            "https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.49/mysql-connector-java-5.1.49.jar";
    private static final String DRIVER_SHA256 = "5bba9ff50e5e637a0996a730619dee19ccae274883a4d28c890d945252bb0e12";
    private static final String WRONG_PASSWORD = "not-the-password-91d2";

    private static Boolean docker;

    @Override
    public String name() {
        return "neoskript-mysql-variables";
    }

    @Override
    public Group group() {
        return Group.BASELINE;
    }

    @Override
    public boolean supports(Environment environment) {
        if (!environment.skriptReference()) {
            return false;
        }
        if (docker == null) {
            docker = MySqlServer.available();
            if (!docker) {
                System.out.println("[harness] neoskript-mysql-variables needs Docker, which isn't running: skipped");
            }
        }
        return docker;
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        SqlVariablesScenario.deleteTree(runs);
        Files.createDirectories(runs);
        try (MySqlServer mysql = MySqlServer.start()) {
            takeTurns(context, runs, mysql);
            twoNeoSkripts(context, runs, mysql);
            neoskriptAndSkript(context, runs, mysql);
            outages(context, runs, mysql);
            wrongPassword(context, runs, mysql);
            badRows(context, runs, mysql);
            passwordNeverShows(runs);
        }
    }

    // ---- 1. Taking turns ---------------------------------------------------------------------------------------

    private void takeTurns(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        String configSk = SqlVariablesScenario.skriptConfig(context.skriptJar(), skriptDatabases(mysql, "turns", false));
        ServerBuilder skript = skript(context, runs.resolve("1-skript-writes"), configSk)
                .file("plugins/Skript/scripts/write.sk", context.script("sql-variables-skript-write.sk"));
        List<String> written = SqlVariablesScenario.stopAfter(skript, Pattern.compile(".*\\[harness] sql written.*"));
        SqlVariablesScenario.expectNo(written, "SQLibrary", skript);
        SqlVariablesScenario.expectNo(written, "database", skript);
        Map<String, SqlVariablesScenario.Row> skriptRows = rows(mysql, "turns");
        if (!skriptRows.containsKey("db_loc") || skriptRows.containsKey("plain")) {
            throw new HarnessFailure("Skript didn't save to MySQL as its pattern says: " + skriptRows.keySet()
                    + ". See " + skript.runDir());
        }
        String create = showCreate(mysql, "turns");
        if (!create.contains("ucs2")) {
            throw new HarnessFailure("Skript's table isn't ucs2: " + create);
        }
        String csv = Files.readString(skript.runDir().resolve("plugins/Skript/variables.csv"), StandardCharsets.UTF_8);

        ServerBuilder neoskript = context.server(runs.resolve("2-neoskript"))
                .plugin(context.neoskriptJar())
                .file("plugins/Skript/config.sk", configSk)
                .file("plugins/Skript/variables.csv", csv)
                .file("plugins/Skript/scripts/read.sk", context.script("sql-variables-neoskript-read.sk"));
        List<String> read = SqlVariablesScenario.stopAfter(neoskript, Pattern.compile(".*\\[harness] sql changed.*"));
        SqlVariablesScenario.line(read, "sql read: ",
                "hello ✓ 日本 | 42 | 2.5 | true | 5 minutes | a, b and ten | creative | in the csv");
        SqlVariablesScenario.line(read, "sql read items: ", "diamond sword | x | 2 | 32 | a | x: 1, y: 2, z: 3");
        Map<String, SqlVariablesScenario.Row> neoskriptRows = rows(mysql, "turns");
        if (!neoskriptRows.get("db_unchanged").equals(skriptRows.get("db_unchanged"))) {
            throw new HarnessFailure("NeoSkript rewrote a row it didn't change");
        }
        for (String name : List.of("db_item", "db_items::1", "db_vector", "db_list::10")) {
            if (!Arrays.equals(neoskriptRows.get(name).value(), skriptRows.get(name).value())) {
                throw new HarnessFailure("NeoSkript changed the bytes of " + name);
            }
        }
        if (neoskriptRows.containsKey("db_deleted") || !neoskriptRows.containsKey("db_new item")) {
            throw new HarnessFailure("NeoSkript's changes aren't in the table: " + neoskriptRows.keySet());
        }
        String neoskriptCsv = Files.readString(neoskript.runDir().resolve("plugins/NeoSkript/variables.csv"));

        ServerBuilder skriptReads = skript(context, runs.resolve("3-skript-reads"), configSk)
                .file("plugins/Skript/variables.csv", neoskriptCsv)
                .file("plugins/Skript/scripts/read.sk", context.script("sql-variables-skript-read.sk"));
        List<String> skriptRead = SqlVariablesScenario.stopAfter(skriptReads,
                Pattern.compile(".*\\[harness] skript sql items: .*"));
        SqlVariablesScenario.line(skriptRead, "skript sql read: ",
                "from neoskript ✓ | 43 | skript wrote this | deleted | csv from neoskript");
        SqlVariablesScenario.line(skriptRead, "skript sql items: ", "5 | n | x | 10");
        context.summary("turns rows", neoskriptRows.size());
    }

    // ---- 2. Two NeoSkript servers --------------------------------------------------------------------------------

    private void twoNeoSkripts(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        try (ServerProcess one = neoskript(context, runs.resolve("4-neoskript-one"), mysql, "shared", true).start();
                ServerProcess two = neoskript(context, runs.resolve("5-neoskript-two"), mysql, "shared", true).start()) {
            one.await(ServerProcess.DONE, WAIT);
            two.await(ServerProcess.DONE, WAIT);
            command(one, "dbset a from-one", "dbset a");
            expectValue(two, "a", "from-one");
            command(two, "dbset a from-two", "dbset a");
            expectValue(one, "a", "from-two");
            command(one, "dbdel a", "dbdel a");
            expectValue(two, "a", "<none>");
            command(two, "dbset b bee", "dbset b");
            expectValue(one, "b", "bee");
            stop(one);
            stop(two);
        }
    }

    // ---- 3. One NeoSkript and one Skript -------------------------------------------------------------------------

    private void neoskriptAndSkript(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        String configSk = SqlVariablesScenario.skriptConfig(context.skriptJar(), skriptDatabases(mysql, "mixed", true));
        try (ServerProcess neo = neoskript(context, runs.resolve("6-neoskript-mixed"), mysql, "mixed", true).start();
                ServerProcess skript = skript(context, runs.resolve("7-skript-mixed"), configSk)
                        .file("plugins/Skript/scripts/commands.sk", context.script("sql-variables-commands.sk"))
                        .start()) {
            neo.await(ServerProcess.DONE, WAIT);
            skript.await(ServerProcess.DONE, WAIT);
            command(skript, "dbset c from-skript", "dbset c");
            expectValue(neo, "c", "from-skript");
            command(neo, "dbset c from-neoskript", "dbset c");
            expectValue(skript, "c", "from-neoskript");
            // Skript doesn't apply another server's deletion (it ignores a row without a value): what NeoSkript
            // writes for one doesn't trouble it.
            command(neo, "dbdel c", "dbdel c");
            Thread.sleep(6_000);
            context.summary("skript sees a deletion", value(skript, "c").equals("<none>"));
            stop(neo);
            stop(skript);
        }
    }

    // ---- 4. Outages ----------------------------------------------------------------------------------------------

    private void outages(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        ServerBuilder builder = neoskript(context, runs.resolve("8-neoskript-outage"), mysql, "outage", false);
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            int mark = server.mark();
            command(server, "dbfill k 3000", "dbfill k 3000");
            // Killed while those are being written.
            mysql.kill();
            server.await(Pattern.compile("Lost database 'mysql'"), mark, Duration.ofSeconds(60));
            command(server, "dbfill k2 500", "dbfill k2 500");
            expectInfo(server, "down since");
            // The server keeps ticking: a command answers straight away.
            long start = System.nanoTime();
            command(server, "dbcount k2", "dbcount k2 = 500");
            if (System.nanoTime() - start > 5_000_000_000L) {
                throw new HarnessFailure("The server stalled while the database was down");
            }
            mysql.restart();
            server.await(Pattern.compile("database 'mysql' \\(MySQL.*is back"), mark, Duration.ofSeconds(90));
            stop(server);
        }
        expectRows(mysql, "outage", "db_k::", 3000);
        expectRows(mysql, "outage", "db_k2::", 500);

        // Down when the server stops: what's waiting is kept, and written at the next start.
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            mysql.kill();
            command(server, "dbset kept across-restart", "dbset kept");
            int mark = server.mark();
            server.command("stop");
            server.awaitExit(WAIT);
            if (server.lines(mark).stream().noneMatch(l -> l.contains("pending/mysql.pending.csv"))) {
                throw new HarnessFailure("NeoSkript didn't say where it kept the changes it couldn't write. See "
                        + server.logFile());
            }
        }
        Path pending = builder.runDir().resolve("plugins/NeoSkript/pending/mysql.pending.csv");
        if (!Files.exists(pending)) {
            throw new HarnessFailure("No " + pending);
        }
        mysql.restart();
        try (ServerProcess server = builder.start()) {
            server.await(Pattern.compile("Applied \\d+ changes that couldn't be written"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            expectValue(server, "kept", "across-restart");
            stop(server);
        }
        if (Files.exists(pending) || !rows(mysql, "outage").containsKey("db_kept")) {
            throw new HarnessFailure("The kept changes weren't written");
        }
    }

    // ---- 5. A wrong password -------------------------------------------------------------------------------------

    private void wrongPassword(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        ServerBuilder builder = context.server(runs.resolve("9-wrong-password"))
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/config.yml", neoskriptConfig(mysql, "wrong", false, WRONG_PASSWORD))
                .file("plugins/NeoSkript/scripts/commands.sk", context.script("sql-variables-commands.sk"));
        try (ServerProcess server = builder.start()) {
            server.await(Pattern.compile("Couldn't reach database 'mysql'.*Access denied"), WAIT);
            server.await(ServerProcess.DONE, WAIT);
            expectInfo(server, "not reached yet");
            // Scripts run; their db_ variables stay in memory, and the CSV database still saves the rest.
            command(server, "dbset x 1", "dbset x");
            expectValue(server, "x", "1");
            stop(server);
        }
    }

    // ---- 6. Rows NeoSkript can't read ----------------------------------------------------------------------------

    private void badRows(ScenarioContext context, Path runs, MySqlServer mysql) throws Exception {
        String table = "bad";
        ServerBuilder builder = neoskript(context, runs.resolve("10-bad-rows"), mysql, table, false);
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            command(server, "dbset good good", "dbset good");
            stop(server);
        }
        try (Connection c = mysql.connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO " + table + " (name, type, value, update_guid) VALUES "
                    + "('db_garbage', 'nosuchtype', X'DEADBEEF', 'skript'), "
                    + "('db_badstring', 'string', X'FFFF', 'skript'), "
                    + "('db_tombstone', NULL, NULL, 'skript')");
        }
        try (ServerProcess server = builder.start()) {
            server.await(ServerProcess.DONE, WAIT);
            expectValue(server, "good", "good");
            command(server, "dbset new 1", "dbset new");
            stop(server);
        }
        Map<String, SqlVariablesScenario.Row> after = rows(mysql, table);
        if (!Arrays.equals(after.get("db_garbage").value(), new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE,
                (byte) 0xEF}) || !Arrays.equals(after.get("db_badstring").value(), new byte[] {(byte) 0xFF, (byte) 0xFF})
                || !after.containsKey("db_new")) {
            throw new HarnessFailure("NeoSkript lost or changed rows it couldn't read: " + after);
        }
    }

    /** The password is never in any console. */
    private static void passwordNeverShows(Path runs) throws IOException {
        try (var walk = Files.walk(runs)) {
            for (Path log : walk.filter(p -> p.getFileName().toString().equals("harness-console.log")).toList()) {
                String text = Files.readString(log, StandardCharsets.UTF_8);
                if (text.contains(MySqlServer.PASSWORD) || text.contains(WRONG_PASSWORD)) {
                    throw new HarnessFailure("A password is in " + log);
                }
            }
        }
    }

    // ---- Servers -------------------------------------------------------------------------------------------------

    private ServerBuilder skript(ScenarioContext context, Path dir, String configSk) throws Exception {
        Path sqlibrary = context.download(SqlVariablesScenario.SQLIBRARY_URL, SqlVariablesScenario.SQLIBRARY_SHA256,
                "SQLibrary-7.1.jar");
        return context.server(dir)
                .plugin(context.skriptJar())
                .plugin(sqlibrary)
                .plugin(driverPlugin(context))
                .file("plugins/Skript/config.sk", configSk);
    }

    private ServerBuilder neoskript(ScenarioContext context, Path dir, MySqlServer mysql, String table, boolean monitor)
            throws Exception {
        return context.server(dir)
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/config.yml", neoskriptConfig(mysql, table, monitor, MySqlServer.PASSWORD))
                .file("plugins/NeoSkript/scripts/commands.sk", context.script("sql-variables-commands.sk"));
    }

    private static String neoskriptConfig(MySqlServer mysql, String table, boolean monitor, String password) {
        return String.join("\n",
                "databases:",
                "  mysql:",
                "    type: mysql",
                "    pattern: \"db_.*\"",
                "    monitor-changes: " + monitor,
                "    monitor-interval: 2 seconds",
                "    host: 127.0.0.1",
                "    port: " + mysql.port(),
                "    user: " + MySqlServer.USER,
                "    password: \"" + password + "\"",
                "    database: " + MySqlServer.DATABASE,
                "    table: " + table,
                "  default:",
                "    type: csv",
                "    pattern: \".*\"",
                "    file: variables.csv",
                "");
    }

    private static String skriptDatabases(MySqlServer mysql, String table, boolean monitor) {
        return String.join("\n",
                "\tmysql:",
                "\t\ttype: MySQL",
                "\t\tpattern: db_.*",
                "\t\tmonitor changes: " + monitor,
                "\t\tmonitor interval: 2 seconds",
                "\t\thost: 127.0.0.1",
                "\t\tport: " + mysql.port(),
                "\t\tuser: " + MySqlServer.USER,
                "\t\tpassword: " + MySqlServer.PASSWORD,
                "\t\tdatabase: " + MySqlServer.DATABASE,
                "\t\ttable: " + table,
                "",
                "\tdefault:",
                "\t\ttype: CSV",
                "\t\tpattern: .*",
                "\t\tfile: ./plugins/Skript/variables.csv",
                "\t\tbackup interval: 0",
                "");
    }

    /** MySQL's driver 5.1.49 as a plugin, with a plugin class from the harness bot's jar. */
    private static Path driverPlugin(ScenarioContext context) throws Exception {
        Path driver = context.download(DRIVER_URL, DRIVER_SHA256, "mysql-connector-java-5.1.49.jar");
        Path plugin = driver.resolveSibling("MySQLDriver-5.1.49-plugin.jar");
        String shim = "co/xenastudios/neoskript/harness/bot/DriverShimPlugin.class";
        try (ZipFile source = new ZipFile(driver.toFile()); ZipFile bot = new ZipFile(context.botJar().toFile());
                OutputStream file = Files.newOutputStream(plugin); ZipOutputStream out = new ZipOutputStream(file)) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = source.getInputStream(entry)) {
                    in.transferTo(out);
                }
            }
            out.putNextEntry(new ZipEntry(shim));
            try (InputStream in = bot.getInputStream(bot.getEntry(shim))) {
                in.transferTo(out);
            }
            out.putNextEntry(new ZipEntry("plugin.yml"));
            out.write(("name: MySQLDriver\nversion: 5.1.49\nmain: co.xenastudios.neoskript.harness.bot.DriverShimPlugin\n"
                    + "api-version: '1.21'\nloadbefore: [SQLibrary, Skript]\n").getBytes(StandardCharsets.UTF_8));
        }
        return plugin;
    }

    // ---- Checks --------------------------------------------------------------------------------------------------

    private static void command(ServerProcess server, String command, String answer) throws Exception {
        int mark = server.mark();
        server.command(command);
        server.await(Pattern.compile(Pattern.quote("[harness] " + answer)), mark, Duration.ofSeconds(30));
    }

    private static String value(ServerProcess server, String key) throws Exception {
        int mark = server.mark();
        server.command("dbget " + key);
        Matcher matcher = server.await(Pattern.compile(Pattern.quote("[harness] dbget " + key + " = ") + "(.*)"), mark,
                Duration.ofSeconds(30));
        return matcher.group(1).strip();
    }

    /** Waits for another server's change to show: the monitor interval is 2 seconds. */
    private static void expectValue(ServerProcess server, String key, String expected) throws Exception {
        long until = System.currentTimeMillis() + 30_000;
        String last = null;
        while (System.currentTimeMillis() < until) {
            last = value(server, key);
            if (last.equals(expected)) {
                return;
            }
            Thread.sleep(500);
        }
        throw new HarnessFailure("{db_" + key + "} is '" + last + "', expected '" + expected + "'. See "
                + server.logFile());
    }

    private static void expectInfo(ServerProcess server, String text) throws Exception {
        int mark = server.mark();
        server.command("sk info");
        server.await(Pattern.compile("mysql.*" + Pattern.quote(text)), mark, Duration.ofSeconds(30));
    }

    private static void stop(ServerProcess server) throws Exception {
        int exit = server.stop();
        if (exit != 0) {
            throw new HarnessFailure("Server exited with code " + exit + ". See " + server.logFile());
        }
    }

    private static void expectRows(MySqlServer mysql, String table, String prefix, int count) throws Exception {
        long found = rows(mysql, table).entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && e.getValue().value() != null).count();
        if (found != count) {
            throw new HarnessFailure("The table has " + found + " " + prefix + " rows, expected " + count);
        }
    }

    private static Map<String, SqlVariablesScenario.Row> rows(MySqlServer mysql, String table) throws Exception {
        Map<String, SqlVariablesScenario.Row> rows = new LinkedHashMap<>();
        try (Connection c = mysql.connect(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT name, type, value, update_guid FROM " + table)) {
            while (r.next()) {
                rows.put(r.getString(1), new SqlVariablesScenario.Row(r.getString(2), r.getBytes(3), r.getString(4)));
            }
        }
        return rows;
    }

    private static String showCreate(MySqlServer mysql, String table) throws Exception {
        try (Connection c = mysql.connect(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SHOW CREATE TABLE " + table)) {
            r.next();
            return r.getString(2);
        }
    }
}
