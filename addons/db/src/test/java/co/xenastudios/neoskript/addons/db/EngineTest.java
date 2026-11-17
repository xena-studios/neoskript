/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.PausingEffect;
import co.xenastudios.neoskript.api.element.PausingSection;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TextTemplate;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Statements against a real SQLite file, as scripts run them. */
class EngineTest {

    @TempDir
    Path folder;

    private Engine engine;
    private String url;
    private final Object event = new Object();
    private final TriggerContext context = () -> event;

    @BeforeEach
    void start() throws Exception {
        url = "sqlite:" + folder.resolve("test.db");
        engine = engine(Settings.DEFAULTS);
        try (Connection connection = DriverManager.getConnection("jdbc:" + url)) {
            connection.createStatement().execute("CREATE TABLE homes (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " owner TEXT, x REAL, Visits INTEGER)");
            connection.createStatement().execute("INSERT INTO homes (owner, x, visits) VALUES ('Steve', 1.5, 3),"
                    + " ('Alex', -2.25, NULL)");
        }
    }

    private Engine engine(Settings settings) {
        return new Engine(settings, Logger.getLogger("test"), getClass().getClassLoader(), () -> true);
    }

    @AfterEach
    void stop() {
        engine.close(Duration.ofSeconds(1));
    }

    private Database database() {
        return engine.database(url, null, null);
    }

    /** An execute as a script writes it, and the query it's given when it runs. */
    private record Call(DbSyntax.Execute effect, TextTemplate query) {}

    private Call execute(TextTemplate query, boolean list, boolean target, Set<String> tags, boolean arguments) {
        return new Call(element(query, list, target, tags, arguments), query);
    }

    private DbSyntax.Execute element(TextTemplate query, boolean list, boolean target, Set<String> tags,
            boolean arguments) {
        long defaults = (arguments ? 0 : 1L << 2) | (target ? 0 : 1L << 3);
        List<TextTemplate> texts = Arrays.asList(query, null, null, null);
        return new DbSyntax.Execute(engine, new ElementInfo(0, 0, tags, 0, list ? 1L << 3 : 0, List.of(),
                Arrays.asList("string", "datasource", null, null), 0, Arrays.asList(null, null, null, null), null,
                List.of(), null, defaults, 0, null, texts, Arrays.asList(null, null, null, null)));
    }

    private static TextTemplate text(String sql) {
        return new TextTemplate(List.of(sql), List.of());
    }

    private static TextTemplate text(String before, Object value, String after) {
        return new TextTemplate(List.of(before, after), List.of(new TextTemplate.Hole(null, "{_x}",
                value == null ? List.of() : List.of(value), value == null ? List.of() : List.of(value.toString()))));
    }

    /** Runs a call: its query with these values for the other placeholders (the database, the arguments). */
    private Object run(Call call, Object... rest) throws Exception {
        Object[] arguments = new Object[4];
        arguments[0] = call.query();
        System.arraycopy(rest, 0, arguments, 1, Math.min(rest.length, 3));
        return call.effect().start(context, arguments).toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** Runs a call with another query, as a template with values. */
    private Object runWith(Call call, TextTemplate query, Object... rest) throws Exception {
        return run(new Call(call.effect(), query), rest);
    }

    @Test
    void aQueryFillsAListByColumnAndRowAsSkriptDbDoes() throws Exception {
        Object result = run(execute(text("SELECT owner, x, visits AS Visits FROM homes ORDER BY id"), true, true,
                Set.of(), false), database());
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("owner", "owner");
        expected.put("x", "x");
        expected.put("visits", "Visits");
        expected.put("owner::1", "Steve");
        expected.put("x::1", 1.5);
        expected.put("visits::1", 3L);
        expected.put("owner::2", "Alex");
        expected.put("x::2", -2.25);
        assertEquals(expected, result);
        assertNull(engine.errors().get(event));
    }

    @Test
    void aSingleVariableGetsTheRowCountOrTheRowsChanged() throws Exception {
        Call count = execute(text("SELECT * FROM homes"), false, true, Set.of(), false);
        assertEquals(2L, run(count, database()));
        Call update = execute(text("UPDATE homes SET visits = 0"), false, true, Set.of(), false);
        assertEquals(2L, run(update, database()));
        Call insert = execute(text("INSERT INTO homes (owner) VALUES ('Zoe')"), false, true,
                Set.of("keys"), false);
        assertEquals(3L, run(insert, database()));
        // Nothing to store: the target, if any, is left as it is.
        assertEquals(PausingEffect.UNCHANGED, run(execute(text("DELETE FROM homes WHERE owner = 'Zoe'"), false,
                false, Set.of(), false), database()));
    }

    @Test
    void scriptTextIsAParameterSoItCantChangeTheQuery() throws Exception {
        Call select = execute(text("SELECT owner FROM homes WHERE owner = ", null, ""), false, true,
                Set.of(), false);
        String attack = "' OR '1'='1";
        assertEquals(0L, runWith(select, text("SELECT owner FROM homes WHERE owner = ", attack, ""), database()));
        String drop = "x'); DROP TABLE homes; --";
        runWith(execute(text("INSERT INTO homes (owner) VALUES (", null, ")"), false, false, Set.of(), false),
                text("INSERT INTO homes (owner) VALUES (", drop, ")"), database());
        assertEquals(3L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
        assertEquals(1L, runWith(select, text("SELECT owner FROM homes WHERE owner = ", drop, ""), database()));
    }

    @Test
    void aBadQueryIsTheLastErrorAndLeavesTheTargetAlone() throws Exception {
        Object result = run(execute(text("SELEKT nonsense"), true, true, Set.of(), false), database());
        assertEquals(PausingEffect.UNCHANGED, result);
        String error = engine.errors().get(event);
        assertNotNull(error);
        assertTrue(error.contains("syntax error"), error);
        // A trigger that ran nothing sees the last error of any.
        assertEquals(error, engine.errors().get(new Object()));
        run(execute(text("SELECT 1"), false, false, Set.of(), false), database());
        assertNull(engine.errors().get(event));
    }

    @Test
    void noDatabaseIsARuntimeError() {
        CompletionStage<?> stage = element(text("SELECT 1"), false, false, Set.of(), false)
                .start(context, new Object[] {text("SELECT 1"), null, null, null});
        Exception failure = assertThrows(Exception.class, () -> stage.toCompletableFuture().get());
        assertEquals("The data source is not set", failure.getCause().getMessage());
    }

    @Test
    void aStatementMustBeWrittenAsText() {
        SyntaxError error = assertThrows(SyntaxError.class, () -> new DbSyntax.Execute(engine, new ElementInfo(0, 0,
                Set.of(), 0, 0, List.of(), Arrays.asList(null, "datasource", null, null), 0,
                Arrays.asList(null, null, null, null), null, List.of(), null, 0b1100, 0, null,
                Arrays.asList(null, null, null, null), Arrays.asList(null, null, null, null))));
        assertTrue(error.getMessage().startsWith("Database statements must be string literals."));
    }

    @Test
    void aListGetsAtMostMaxRows() throws Exception {
        engine.close(Duration.ZERO);
        engine = engine(new Settings(10, 2, 1_800_000, 30_000, 30, 30, null, 1, 600));
        Object result = run(execute(text("SELECT * FROM homes"), true, true, Set.of(), false), database());
        assertEquals(PausingEffect.UNCHANGED, result);
        assertTrue(engine.errors().get(event).startsWith("The result has more than 1 rows"));
        // Counting needs no rows kept, so it has no limit.
        assertEquals(2L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
    }

    @Test
    void argumentsAreBoundToQuestionMarks() throws Exception {
        Call insert = execute(text("INSERT INTO homes (owner, visits) VALUES (?, ?)"), false, true,
                Set.of(), true);
        assertEquals(1L, run(insert, database(), new Object[] {"Robin", 9L}));
        assertEquals(1L, run(execute(text("SELECT * FROM homes WHERE owner = 'Robin' AND visits = 9"), false, true,
                Set.of(), false), database()));
    }

    @Test
    void aTransactionCommitsAtItsEndAndAFailedStatementRollsItBackAndLeavesIt() throws Exception {
        DbSyntax.Transaction section = new DbSyntax.Transaction(engine);
        assertEquals(Boolean.TRUE, section.start(context, new Object[] {database()}).toCompletableFuture().get());
        run(execute(text("DELETE FROM homes"), false, false, Set.of(), false), database());
        section.end(context).toCompletableFuture().get();
        assertEquals(0L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
        assertEquals(0, engine.transactions().open());

        run(execute(text("INSERT INTO homes (owner) VALUES ('A')"), false, false, Set.of(), false), database());
        assertEquals(Boolean.TRUE, section.start(context, new Object[] {database()}).toCompletableFuture().get());
        run(execute(text("DELETE FROM homes"), false, false, Set.of(), false), database());
        assertEquals(PausingSection.LEAVE, run(execute(text("NOT SQL"), false, false, Set.of(), false),
                database()));
        assertNotNull(engine.errors().get(event));
        assertNull(section.end(context), "rolled back already: nothing to wait for");
        assertEquals(1L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
        assertEquals(0, engine.transactions().open());
    }

    @Test
    void anAbandonedTransactionIsRolledBack() throws Exception {
        engine.close(Duration.ZERO);
        engine = engine(new Settings(10, 2, 1_800_000, 30_000, 30, 1, null, 100, 600));
        DbSyntax.Transaction section = new DbSyntax.Transaction(engine);
        section.start(context, new Object[] {database()}).toCompletableFuture().get();
        run(execute(text("DELETE FROM homes"), false, false, Set.of(), false), database());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (engine.transactions().open() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(0, engine.transactions().open());
        assertEquals(2L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
    }

    @Test
    void batchArgumentsRunOncePerRow() throws Exception {
        Call batch = execute(text("INSERT INTO homes (owner, visits) VALUES (?, ?)"), false, true,
                Set.of("batch"), true);
        assertTrue(batch.effect().recursiveKeys());
        Object rows = keyed(new Object[] {"P", 1L, "Q", 2L}, new String[] {"1::1", "1::2", "2::1", "2::2"});
        assertEquals(2L, run(batch, database(), rows));
        assertEquals(4L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
    }

    /** NeoSkript's keyed values, as the runtime has them, for a test without the runtime. */
    private static Object keyed(Object[] values, String[] keys) {
        return new co.xenastudios.neoskript.runtime.KeyedValues(values, keys);
    }

    @Test
    void aDatabaseThatIsDownFailsTheStatementWithoutItsPassword() throws Exception {
        engine.close(Duration.ZERO);
        engine = engine(new Settings(2, 2, 1_800_000, 500, 30, 30, null, 100, 600));
        Database down = engine.database("mariadb://127.0.0.1:1/game?user=admin&password=hunter22", null, null);
        assertEquals("jdbc:mariadb://127.0.0.1:1/game?user=admin&password=***", down.shown());
        long started = System.nanoTime();
        Object result = run(execute(text("SELECT 1"), false, false, Set.of(), false), down);
        assertEquals(PausingEffect.UNCHANGED, result);
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10));
        String error = engine.errors().get(event);
        assertNotNull(error);
        assertFalse(error.contains("hunter22"), error);
        assertEquals(0, down.inUse());
    }

    @Test
    void aStatementGivenUpOnBeforeItStartsNeverRuns() throws Exception {
        engine.close(Duration.ZERO);
        engine = engine(new Settings(1, 1, 1_800_000, 30_000, 30, 30, null, 100, 600));
        CompletableFuture<Object> block = new CompletableFuture<>();
        // The only thread is busy, so the next statement waits, and is given up on.
        engine.run(block::get, Running.untimed());
        CompletionStage<?> waiting = element(text("DELETE FROM homes"), false, false, Set.of(), false)
                .start(context, new Object[] {text("DELETE FROM homes"), database(), null, null});
        waiting.toCompletableFuture().cancel(true);
        block.complete(null);
        Thread.sleep(200);
        assertEquals(2L, run(execute(text("SELECT * FROM homes"), false, true, Set.of(), false), database()));
    }

    @Test
    void anIdleDatabaseClosesItsConnectionsAndOpensThemAgain() throws Exception {
        Database database = database();
        run(execute(text("SELECT 1"), false, false, Set.of(), false), database);
        assertTrue(database.isOpen());
        assertTrue(database.closeIfIdle(0));
        assertFalse(database.isOpen());
        run(execute(text("SELECT 1"), false, false, Set.of(), false), database);
        assertTrue(database.isOpen());
    }

    @Test
    void stoppingClosesEverythingAndLeavesNoThreads() throws Exception {
        DbSyntax.Transaction section = new DbSyntax.Transaction(engine);
        section.start(context, new Object[] {database()}).toCompletableFuture().get();
        run(execute(text("DELETE FROM homes"), false, false, Set.of(), false), database());
        engine.close(Duration.ofSeconds(1));
        // The transaction was rolled back.
        try (Connection connection = DriverManager.getConnection("jdbc:" + url);
                ResultSet rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM homes")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
        CompletionStage<?> after = element(text("SELECT 1"), false, false, Set.of(), false)
                .start(context, new Object[] {text("SELECT 1"), database(), null, null});
        assertThrows(Exception.class, () -> after.toCompletableFuture().get());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        List<String> left = new ArrayList<>();
        do {
            left.clear();
            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
                    .map(Thread::getName).filter(name -> name.startsWith("NeoSkriptDb")).forEach(left::add);
            if (left.isEmpty()) {
                break;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(List.of(), left);
    }

    @Test
    void theSameUrlIsTheSameDatabase() {
        Map<String, Object> seen = new HashMap<>();
        seen.put("a", engine.database(url, null, null));
        assertInstanceOf(Database.class, seen.get("a"));
        assertTrue(seen.get("a") == engine.database(" jdbc:" + url, null, null));
    }

    @Test
    void aSlowStatementIsStoppedAtTheQueryTimeout() throws Exception {
        engine.close(Duration.ZERO);
        engine = engine(new Settings(2, 2, 1_800_000, 30_000, 1, 30, null, 100, 600));
        long started = System.nanoTime();
        Object result = run(execute(text("WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c)"
                + " SELECT COUNT(*) FROM c"), false, true, Set.of(), false), database());
        assertEquals(PausingEffect.UNCHANGED, result);
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(15));
        assertNotNull(engine.errors().get(event));
        assertEquals(0, database().inUse());
    }
}
