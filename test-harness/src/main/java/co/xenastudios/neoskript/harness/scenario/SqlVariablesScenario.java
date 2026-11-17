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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Skript 2.17.0, with SQLibrary, and NeoSkript take turns on the same SQLite file, as Skript's {@code databases:}
 * section sets it up: {@code db_} variables in SQLite, the rest in CSV.
 *
 * <ol>
 *   <li>Skript saves variables of many kinds, items and locations included, to the SQLite file.
 *   <li>NeoSkript takes over the install: it copies the {@code databases:} section from config.sk (the file is
 *       outside Skript's folder, so it's used where it is), reads every variable as Skript saved it, and changes some.
 *       The rows it didn't change are byte for byte what Skript wrote, still marked as Skript's.
 *   <li>Skript reads what NeoSkript wrote, deletions included.
 * </ol>
 */
public final class SqlVariablesScenario implements Scenario {

    static final Duration WAIT = Duration.ofMinutes(3);

    /** SQLibrary 7.1, the plugin Skript's SQLite and MySQL storage needs, from CurseForge. */
    static final String SQLIBRARY_URL = "https://mediafilez.forgecdn.net/files/767/49/SQLibrary-7.1.jar";
    static final String SQLIBRARY_SHA256 = "bafc1d5ecfdda708173129c581732be2f16f50f00d26193dad7d0761a9c8055d";

    @Override
    public String name() {
        return "neoskript-sqlite-variables";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Path shared = runs.resolve("shared").toAbsolutePath();
        deleteTree(shared);
        Files.createDirectories(shared);
        Path db = shared.resolve("variables.db");
        String databases = String.join("\n",
                "\tsqlite:",
                "\t\ttype: SQLite",
                "\t\tpattern: db_.*",
                "\t\tmonitor changes: false",
                "\t\tmonitor interval: 20 seconds",
                "\t\tfile: " + db,
                "\t\tbackup interval: 0",
                "",
                "\tdefault:",
                "\t\ttype: CSV",
                "\t\tpattern: .*",
                "\t\tfile: ./plugins/Skript/variables.csv",
                "\t\tbackup interval: 2 hours",
                "\t\tbackups to keep: -1",
                "");
        String configSk = skriptConfig(context.skriptJar(), databases);
        Path sqlibrary = context.download(SQLIBRARY_URL, SQLIBRARY_SHA256, "SQLibrary-7.1.jar");

        // 1. Skript saves its variables to SQLite.
        ServerBuilder skript = context.server(runs.resolve("1-skript-writes"))
                .plugin(context.skriptJar())
                .plugin(sqlibrary)
                .file("plugins/Skript/config.sk", configSk)
                .file("plugins/Skript/scripts/write.sk", context.script("sql-variables-skript-write.sk"));
        List<String> written = stopAfter(skript, Pattern.compile(".*\\[harness] sql written.*"));
        expectNo(written, "SQLibrary", skript);
        Map<String, Row> skriptRows = rows(db);
        if (!skriptRows.containsKey("db_loc") || skriptRows.containsKey("plain") || skriptRows.containsKey("-db_memory")) {
            throw new HarnessFailure("Skript didn't save to SQLite as its pattern says: " + skriptRows.keySet());
        }
        String csv = Files.readString(skript.runDir().resolve("plugins/Skript/variables.csv"), StandardCharsets.UTF_8);
        context.summary("skript rows", skriptRows.size());

        // 2. NeoSkript takes over, reads them all, and changes some.
        ServerBuilder neoskript = context.server(runs.resolve("2-neoskript"))
                .plugin(context.neoskriptJar())
                .file("plugins/Skript/config.sk", configSk)
                .file("plugins/Skript/variables.csv", csv)
                .file("plugins/Skript/scripts/read.sk", context.script("sql-variables-neoskript-read.sk"));
        List<String> read = stopAfter(neoskript, Pattern.compile(".*\\[harness] sql changed.*"));
        line(read, "sql read: ", "hello ✓ 日本 | 42 | 2.5 | true | 5 minutes | a, b and ten | creative | in the csv");
        line(read, "sql read items: ", "diamond sword | x | 2 | 32 | a | x: 1, y: 2, z: 3");
        String loc = find(read, "sql read loc: ");
        if (!loc.contains("1.5") || !loc.contains("64") || !loc.contains("-2")) {
            throw new HarnessFailure("NeoSkript read Skript's location as '" + loc + "'");
        }
        String config = Files.readString(neoskript.runDir().resolve("plugins/NeoSkript/config.yml"));
        if (!config.contains("sqlite:") || !config.contains(db.toString())) {
            throw new HarnessFailure("NeoSkript didn't copy the databases section from config.sk:\n" + config);
        }
        Map<String, Row> neoskriptRows = rows(db);
        Row unchanged = neoskriptRows.get("db_unchanged");
        if (unchanged == null || !unchanged.equals(skriptRows.get("db_unchanged"))) {
            throw new HarnessFailure("NeoSkript rewrote a row it didn't change: " + unchanged + " was "
                    + skriptRows.get("db_unchanged"));
        }
        for (String name : List.of("db_item", "db_items::1", "db_vector", "db_list::10")) {
            if (!Arrays.equals(neoskriptRows.get(name).value(), skriptRows.get(name).value())) {
                throw new HarnessFailure("NeoSkript changed the bytes of " + name);
            }
        }
        if (neoskriptRows.containsKey("db_deleted") || !neoskriptRows.containsKey("db_new item")
                || neoskriptRows.get("db_text").guid().equals(skriptRows.get("db_text").guid())) {
            throw new HarnessFailure("NeoSkript's changes aren't in the table as Skript writes them: "
                    + neoskriptRows.keySet());
        }
        String neoskriptCsv = Files.readString(neoskript.runDir().resolve("plugins/NeoSkript/variables.csv"));
        if (!neoskriptCsv.contains("\nplain, string, ") || neoskriptCsv.contains("db_")) {
            throw new HarnessFailure("NeoSkript's variables.csv doesn't hold just the CSV database's variables:\n"
                    + neoskriptCsv);
        }

        // 3. Skript reads what NeoSkript wrote.
        ServerBuilder skriptReads = context.server(runs.resolve("3-skript-reads"))
                .plugin(context.skriptJar())
                .plugin(sqlibrary)
                .file("plugins/Skript/config.sk", configSk)
                .file("plugins/Skript/variables.csv", neoskriptCsv)
                .file("plugins/Skript/scripts/read.sk", context.script("sql-variables-skript-read.sk"));
        List<String> skriptRead = stopAfter(skriptReads, Pattern.compile(".*\\[harness] skript sql items: .*"));
        line(skriptRead, "skript sql read: ", "from neoskript ✓ | 43 | skript wrote this | deleted | csv from neoskript");
        line(skriptRead, "skript sql items: ", "5 | n | x | 10");
    }

    /** A row of Skript's table. */
    record Row(String type, byte[] value, String guid) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Row row && java.util.Objects.equals(type, row.type)
                    && Arrays.equals(value, row.value) && guid.equals(row.guid);
        }

        @Override
        public int hashCode() {
            return guid.hashCode();
        }

        @Override
        public String toString() {
            return type + "/" + (value == null ? "null" : java.util.HexFormat.of().formatHex(value)) + "/" + guid;
        }
    }

    static Map<String, Row> rows(Path db) throws Exception {
        Map<String, Row> rows = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT name, type, value, update_guid FROM variables21")) {
            while (r.next()) {
                rows.put(r.getString(1), new Row(r.getString(2), r.getBytes(3), r.getString(4)));
            }
        }
        return rows;
    }

    /** Skript's own default config.sk, from its jar, with {@code databases} as its databases: section. */
    static String skriptConfig(Path skriptJar, String databases) throws Exception {
        String config;
        try (ZipFile jar = new ZipFile(skriptJar.toFile())) {
            config = new String(jar.getInputStream(jar.getEntry("config.sk")).readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = config.indexOf("\ndatabases:\n");
        int end = config.indexOf("\n# ==== Settings that should not be changed", start);
        if (start < 0 || end < 0) {
            throw new HarnessFailure("Skript's config.sk has no databases: section to replace");
        }
        return config.substring(0, start) + "\ndatabases:\n" + databases + "\n" + config.substring(end);
    }

    static void expectNo(List<String> lines, String text, ServerBuilder server) {
        for (String line : lines) {
            if (line.contains(text) && (line.contains("ERROR") || line.contains("You need the plugin"))) {
                throw new HarnessFailure("The server logged: " + line + ". See " + server.runDir());
            }
        }
    }

    static String find(List<String> lines, String marker) {
        return lines.stream().filter(l -> l.contains("[harness] " + marker)).findFirst()
                .map(l -> l.substring(l.indexOf("[harness] " + marker) + ("[harness] " + marker).length()))
                .orElseThrow(() -> new HarnessFailure("No '[harness] " + marker + "' line"));
    }

    static void line(List<String> lines, String marker, String expected) {
        String actual = find(lines, marker);
        if (!actual.strip().equals(expected)) {
            throw new HarnessFailure("'" + marker + "' was '" + actual + "', expected '" + expected + "'");
        }
    }

    /** Starts the server, waits for {@code marker}, stops it cleanly, and returns its console lines. */
    static List<String> stopAfter(ServerBuilder builder, Pattern marker) throws Exception {
        try (ServerProcess server = builder.start()) {
            server.await(marker, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            // Skript commits its SQL writes every half a second.
            Thread.sleep(2_000);
            int exit = server.stop();
            if (exit != 0) {
                throw new HarnessFailure("Server exited with code " + exit + ". See " + server.logFile());
            }
            return server.linesMatching(Pattern.compile(".*"));
        }
    }

    static void deleteTree(Path dir) throws java.io.IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
