/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.TextTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The parts that need no database: settings, secrets, placeholders, and how a query is put together. */
class PartsTest {

    @Test
    void settingsAreSkriptDbsWithAThirtySecondQueryTimeout() {
        List<String> warnings = new ArrayList<>();
        Settings settings = Settings.read(key -> null, warnings);
        assertEquals(List.of(), warnings);
        assertEquals(10, settings.poolSize());
        assertEquals(30, settings.queryTimeoutSeconds());
        assertEquals(30_000, settings.connectionTimeoutMillis());
        assertNull(settings.driverClassName());
        assertEquals(30_000 + 30_000 + 5_000, settings.waitMillis());
        assertTrue(settings.workerThreads() >= 2);
    }

    @Test
    void eachBadSettingFallsBackOnItsOwn() {
        Map<String, Object> config = new HashMap<>();
        config.put("pool-size", 0);
        config.put("connection-timeout", "fast");
        config.put("query-timeout", 0);
        config.put("max-cells", List.of(1));
        config.put("thread-pool-size", "4");
        config.put("sql-driver-class-name", "org.mariadb.jdbc.Driver");
        List<String> warnings = new ArrayList<>();
        Settings settings = Settings.read(config::get, warnings);
        assertEquals(10, settings.poolSize());
        assertEquals(30_000, settings.connectionTimeoutMillis());
        assertEquals(0, settings.queryTimeoutSeconds());
        assertEquals(Settings.UNLIMITED_WAIT_MILLIS, settings.waitMillis());
        assertEquals(1_000_000, settings.maxCells());
        assertEquals(4, settings.workerThreads());
        assertEquals("org.mariadb.jdbc.Driver", settings.driverClassName());
        assertEquals(List.of("pool-size is 0, but it has to be from 1 to 1024: using 10",
                "connection-timeout is \"fast\", which isn't a whole number: using 30000",
                "max-cells is a list, which isn't a whole number: using 1000000"), warnings);
    }

    @Test
    void passwordsAreMaskedAndHidden() {
        String url = "jdbc:mysql://admin:s3cret!@localhost:3306/db?user=admin&password=hunter2%21&useSSL=false";
        assertEquals("jdbc:mysql://admin:***@localhost:3306/db?user=admin&password=***&useSSL=false",
                Secrets.mask(url));
        String said = Secrets.hide("Access denied for admin using password hunter2! and s3cret! at " + url, url);
        assertFalse(said.contains("hunter2"), said);
        assertFalse(said.contains("s3cret"), said);
        assertEquals("jdbc:sqlite:plugins/x.db", Secrets.mask("jdbc:sqlite:plugins/x.db"));
        assertEquals(new Secrets.Split("jdbc:mysql://localhost:3306/db?user=admin&useSSL=false", "admin", "s3cret!"),
                Secrets.split("jdbc:mysql://admin:s3cret%21@localhost:3306/db?password=s3cret%21&user=admin&useSSL=false"));
        assertEquals(new Secrets.Split("jdbc:sqlite:x.db", null, null), Secrets.split("jdbc:sqlite:x.db"));
        // A password with an @ or a / in it, not encoded: all of it, to the last @ before the host.
        String awkward = "jdbc:mysql://root:p@ss/w@host/db?user=root";
        assertEquals("jdbc:mysql://root:***@host/db?user=root", Secrets.mask(awkward));
        assertEquals(new Secrets.Split("jdbc:mysql://host/db?user=root", "root", "p@ss/w"), Secrets.split(awkward));
        assertFalse(Secrets.hide("Access denied (p@ss/w) for " + awkward, awkward).contains("ss/w"));
        assertEquals("jdbc:mysql://host:3306/db", Secrets.mask("jdbc:mysql://host:3306/db"));
        // Short passwords too.
        String shortOne = "jdbc:mariadb://h/db?user=u&password=q7";
        assertFalse(Secrets.hide("bad password q7 for u", shortOne).contains("q7"));
    }

    @Test
    void placeholdersAreCountedAsSkriptDbCountsThem() {
        assertEquals(2, Placeholders.count("SELECT * FROM t WHERE a = ? AND b = ?"));
        assertEquals(1, Placeholders.count("SELECT '?', \"?\", `?` FROM t WHERE a = ? -- ?\n/* ? */"));
        assertEquals(0, Placeholders.count("SELECT a ?? b, \\? FROM t"));
    }

    private static TextTemplate.Hole hole(String element, String written, List<Object> values) {
        return new TextTemplate.Hole(element, written, values, values.stream().map(String::valueOf).toList());
    }

    @Test
    void expressionsInAQueryAreParametersAndUnsafeOnesText() {
        TextTemplate template = new TextTemplate(
                List.of("SELECT * FROM ", " WHERE name = ", " AND id IN (", ") AND x = ", ""),
                List.of(hole(DbSyntax.UNSAFE, "unsafe {_table}", List.of("homes")),
                        hole(null, "{_name}", List.of("Steve' OR '1'='1")),
                        hole(null, "{_ids::*}", List.of(1L, 2L, 3L)),
                        hole(null, "{_nothing}", List.of())));
        Query query = DbSyntax.render(template);
        assertEquals("SELECT * FROM homes WHERE name = ? AND id IN (?, ?, ?) AND x = ?", query.sql());
        List<Object> expected = new ArrayList<>(List.of("Steve' OR '1'='1", 1L, 2L, 3L));
        expected.add(null);
        assertEquals(expected, query.parameters());
        assertEquals(Query.of("DROP TABLE x"), DbSyntax.render("DROP TABLE x"));
        assertEquals("SELECT homes, Steve' OR '1'='1, 1, 2 and 3, <none>", DbSyntax.text(new TextTemplate(
                List.of("SELECT ", ", ", ", ", ", ", ""), template.holes())));
    }

    @Test
    void urlsAreNormalisedAndDriversPickedAsSkriptDbPicksThem() {
        assertEquals("jdbc:mysql://h/db", Drivers.normalise(" mysql://h/db "));
        assertEquals("jdbc:sqlite:x.db", Drivers.normalise("JDBC:sqlite:x.db"));
        Drivers drivers = new Drivers(getClass().getClassLoader());
        assertEquals(Drivers.MARIADB, drivers.resolve("jdbc:mariadb://h/db", null).driver());
        assertEquals(Drivers.POSTGRESQL, drivers.resolve("jdbc:postgresql://h/db", null).driver());
        assertEquals(Drivers.SQLITE, drivers.resolve("jdbc:sqlite:x.db", null).driver());
        // No MySQL driver here: MariaDB's, told to take mysql: URLs.
        assertEquals(new Drivers.Resolution("jdbc:mysql://h/db?permitMysqlScheme=true", Drivers.MARIADB),
                drivers.resolve("jdbc:mysql://h/db", null));
        assertEquals(Drivers.MARIADB, drivers.resolve("jdbc:x:y", "org.mariadb.jdbc.Driver").driver());
    }
}
