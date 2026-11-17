/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.PausingEffect;
import co.xenastudios.neoskript.api.element.PausingSection;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TemplateAware;
import co.xenastudios.neoskript.api.element.TextTemplate;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Warnings;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * skript-db's syntax, as skript-db 2.0.0 has it, and the older forks' {@code synchronously}:
 *
 * <pre>
 * [(quickly|synchronously)] execute %string% (in|on) %datasource% [with [batch] arg[ument][s] %-objects%]
 *     [and store [the] [generated keys] [(output|result)[s]] (to|in) [the] [var[iable]] %-objects%]
 * [the] data(base|[ ]source) [(of|at)] %string% [with [a] [max[imum]] [connection] life[ ]time of %-timespan%]
 *     [[(using|with)] [a] driver %-string%]
 * [the] [last] (sql|db|data(base|[ ]source)) error
 * unsafe %string%
 * [(sql|database)] transaction (on|in) %datasource%:
 * </pre>
 */
final class DbSyntax {

    static final String EXECUTE = "NeoSkriptDbEffExecute";
    static final String DATABASE = "NeoSkriptDbExprDatabase";
    static final String LAST_ERROR = "NeoSkriptDbExprLastError";
    static final String UNSAFE = "NeoSkriptDbExprUnsafe";
    static final String TRANSACTION = "NeoSkriptDbSecTransaction";

    /** The placeholders of {@code execute}. */
    private static final int QUERY = 0;
    private static final int SOURCE = 1;
    private static final int ARGUMENTS = 2;
    private static final int TARGET = 3;

    private DbSyntax() {}

    static void register(Registrar registrar, Engine engine) {
        registrar.type("datasource", "data source", Database.class);
        registrar.alias("datasource", "data source", "data sources");
        registrar.text(Database.class, Database::shown);
        registrar.storage("datasource", Database.class, new DatabaseCodec(engine));

        registrar.pausingEffect(EXECUTE, info -> new Execute(engine, info),
                "[(quickly|synchronously)] execute %string% (in|on) %datasource% [with [batch:batch] arg[ument][s]"
                        + " %-objects%] [and store [the] [keys:generated keys] [(output|result)[s]] (to|in) [the]"
                        + " [var[iable]] %-objects%]");
        registrar.timeout(EXECUTE, Duration.ofMillis(engine.settings().waitMillis()));

        registrar.expression(DATABASE, "datasource", info -> (context, arguments) -> {
            if (!(arguments[0] instanceof String url)) {
                return null;
            }
            long lifetime = ScriptValues.timespanMillis(arguments[1]);
            return engine.database(url, lifetime == Long.MIN_VALUE ? null : lifetime,
                    arguments[2] instanceof String driver ? driver : null);
        }, "[the] data(base|[ ]source) [(of|at)] %string% [with [a] [max[imum]] [connection] life[ ]time of"
                + " %-timespan%] [[(using|with)] [a] driver %-string%]");
        registrar.affinity(DATABASE, ThreadAffinity.ANY);

        registrar.expression(LAST_ERROR, "string",
                info -> (context, arguments) -> engine.errors().get(scope(context)),
                "[the] [last] (sql|db|data(base|[ ]source)) error");
        registrar.affinity(LAST_ERROR, ThreadAffinity.ANY);

        registrar.expression(UNSAFE, "string", info -> (context, arguments) -> arguments[0], "unsafe %string%");
        registrar.affinity(UNSAFE, ThreadAffinity.ANY);

        registrar.pausingSection(TRANSACTION, info -> new Transaction(engine),
                "[(sql|database)] transaction (on|in) %datasource%");
        registrar.timeout(TRANSACTION, Duration.ofMillis(engine.settings().waitMillis()));
    }

    /** Which trigger a statement belongs to: its event, or the trigger itself where it has none. */
    static Object scope(TriggerContext context) {
        Object event = context.event();
        return event != null ? event : context;
    }

    /** {@code execute ... in ... [with arguments ...] [and store the result in ...]}. */
    static final class Execute implements PausingEffect, TemplateAware, KeyAware {

        private final Engine engine;
        private final boolean batch;
        private final boolean keys;
        private final boolean arguments;
        private final Statements.Target target;

        Execute(Engine engine, ElementInfo info) {
            this.engine = engine;
            this.batch = info.hasTag("batch");
            this.keys = info.hasTag("keys");
            this.arguments = isWritten(info, ARGUMENTS);
            TextTemplate text = info.argumentText(QUERY);
            if (text == null && !UNSAFE.equals(info.argumentElement(QUERY))) {
                throw new SyntaxError("Database statements must be string literals. If you must use an expression,"
                        + " you may use \"%unsafe (your expression)%\", but keep in mind that you may be vulnerable"
                        + " to SQL injection attacks!");
            }
            if (text != null) {
                quoteWarnings(text);
            }
            if (arguments) {
                if (text != null && !text.holes().isEmpty()) {
                    Warnings.warn("Your query string contains expressions, but you also provided arguments. The"
                            + " embedded expressions are inserted as plain text; consider using only question marks.");
                }
                if (batch) {
                    String written = Warnings.argument(ARGUMENTS);
                    if (written != null && !(written.startsWith("{") && written.endsWith("::*}"))) {
                        throw new SyntaxError("Batch arguments must be a list variable holding one list per row,"
                                + " for example {_rows::*}");
                    }
                    if (keys) {
                        throw new SyntaxError("Generated keys cannot be stored when executing a batch");
                    }
                }
            }
            if (isWritten(info, TARGET)) {
                String written = Warnings.argument(TARGET);
                if (written != null && !written.startsWith("{")) {
                    throw new SyntaxError(written + " is not a variable");
                }
                target = info.isList(TARGET) ? Statements.Target.LIST : Statements.Target.SINGLE;
            } else {
                target = Statements.Target.NONE;
            }
        }

        /** Whether an optional placeholder was written: one left out is a default. */
        private static boolean isWritten(ElementInfo info, int index) {
            return !info.isDefault(index);
        }

        /** skript-db's warnings for {@code '%x%'}: the parameter needs no quotes, and unsafe text maybe none. */
        private static void quoteWarnings(TextTemplate text) {
            for (int i = 0; i < text.holes().size(); i++) {
                if (!text.pieces().get(i).endsWith("'") || !text.pieces().get(i + 1).startsWith("'")) {
                    continue;
                }
                TextTemplate.Hole hole = text.holes().get(i);
                if (UNSAFE.equals(hole.element())) {
                    String raw = hole.written().replaceFirst("(?i)^unsafe\\s+", "");
                    Warnings.warn("Unsafe may have been used unnecessarily. Try replacing 'unsafe " + raw + "' with "
                            + raw);
                } else {
                    Warnings.warn("Do not surround expressions with quotes!");
                }
            }
        }

        @Override
        public boolean template(int index) {
            return index == QUERY;
        }

        @Override
        public boolean recursiveKeys() {
            return batch;
        }

        @Override
        public int target() {
            return TARGET;
        }

        @Override
        public CompletionStage<?> start(TriggerContext context, Object[] args) {
            if (!(args[SOURCE] instanceof Database database)) {
                return CompletableFuture.failedFuture(new IllegalStateException("The data source is not set"));
            }
            Object scope = scope(context);
            Query query;
            List<List<Object>> rows = null;
            if (batch && arguments) {
                query = Query.of(text(args[QUERY]));
                rows = rows(args[ARGUMENTS]);
            } else if (arguments) {
                String sql = text(args[QUERY]);
                Object[] values = ScriptValues.values(args[ARGUMENTS]);
                int expected = Placeholders.count(sql);
                if (expected != values.length) {
                    engine.logger().warning(String.format("Your query has %d question marks, but you provided %d"
                            + " arguments.", expected, values.length));
                    values = Arrays.copyOf(values, expected);
                }
                List<Object> parameters = new ArrayList<>(values.length);
                for (Object value : values) {
                    parameters.add(value == null ? null : JdbcValues.toJdbc(value, String.valueOf(value)));
                }
                query = new Query(sql, parameters);
            } else {
                query = render(args[QUERY]);
            }
            Transactions.Transaction transaction = engine.transactions().find(scope, database);
            AtomicReference<Statement> running = new AtomicReference<>();
            List<List<Object>> batchRows = rows;
            return engine.run(() -> {
                try {
                    Object value = transaction != null
                            ? transaction.use(connection -> run(connection, query, batchRows, true, running))
                            : runLeased(database, query, batchRows, running);
                    engine.errors().record(scope, null);
                    return target == Statements.Target.NONE ? PausingEffect.UNCHANGED : value;
                } catch (SQLException | RuntimeException e) {
                    String message = Engine.message(e, database);
                    engine.errors().record(scope, message);
                    if (transaction != null) {
                        // skript-db rolls the transaction back and skips the rest of its section.
                        transaction.rollback();
                        engine.transactions().end(scope, transaction);
                        return PausingSection.LEAVE;
                    }
                    return PausingEffect.UNCHANGED;
                }
            }, running);
        }

        private Object runLeased(Database database, Query query, List<List<Object>> rows,
                AtomicReference<Statement> running) throws SQLException {
            try (Database.Lease lease = database.lease()) {
                return run(lease.connection(), query, rows, false, running);
            }
        }

        private Object run(java.sql.Connection connection, Query query, List<List<Object>> rows, boolean inTransaction,
                AtomicReference<Statement> running) throws SQLException {
            if (rows != null) {
                long affected = engine.statements().executeBatch(connection, query.sql(), rows, !inTransaction, running);
                return switch (target) {
                    case SINGLE -> affected;
                    case LIST -> new LinkedHashMap<String, Object>();
                    case NONE -> null;
                };
            }
            return engine.statements().execute(connection, query, keys, target, running);
        }

        /** Batch rows: each index of the list is a row, its own list its cells, or its value one cell. */
        private static List<List<Object>> rows(Object argument) {
            Object[][] keyed = ScriptValues.keyed(argument);
            List<List<Object>> rows = new ArrayList<>();
            if (keyed == null) {
                return rows;
            }
            Map<String, List<Object>> byRow = new LinkedHashMap<>();
            for (int i = 0; i < keyed[0].length; i++) {
                String key = String.valueOf(keyed[1][i]);
                int separator = key.indexOf("::");
                String row = separator < 0 ? key : key.substring(0, separator);
                Object value = keyed[0][i];
                byRow.computeIfAbsent(row, ignored -> new ArrayList<>())
                        .add(JdbcValues.toJdbc(value, String.valueOf(value)));
            }
            rows.addAll(byRow.values());
            return rows;
        }
    }

    /**
     * A query as written: text in quotes with each expression in it bound as a parameter, as skript-db does, so
     * no value can change what the query does; an {@code unsafe} one's text put in as it is. Anything else (the
     * text of {@code unsafe {_query}}) is the SQL as it is.
     */
    static Query render(Object query) {
        if (!(query instanceof TextTemplate template)) {
            return Query.of(query == null ? "" : String.valueOf(query));
        }
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < template.holes().size(); i++) {
            sql.append(template.pieces().get(i));
            TextTemplate.Hole hole = template.holes().get(i);
            if (UNSAFE.equals(hole.element())) {
                StringJoiner text = new StringJoiner(", ");
                hole.texts().forEach(text::add);
                sql.append(text);
            } else if (hole.values().isEmpty()) {
                sql.append('?');
                parameters.add(null);
            } else {
                for (int v = 0; v < hole.values().size(); v++) {
                    sql.append(v == 0 ? "?" : ", ?");
                    parameters.add(JdbcValues.toJdbc(hole.values().get(v), hole.texts().get(v)));
                }
            }
        }
        sql.append(template.pieces().getLast());
        return new Query(sql.toString(), parameters);
    }

    /** A query as plain text, its expressions put in as text would show them, for {@code with arguments}. */
    static String text(Object query) {
        if (!(query instanceof TextTemplate template)) {
            return query == null ? "" : String.valueOf(query);
        }
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < template.holes().size(); i++) {
            sql.append(template.pieces().get(i));
            List<String> texts = template.holes().get(i).texts();
            if (texts.isEmpty()) {
                sql.append("<none>");
            }
            for (int t = 0; t < texts.size(); t++) {
                sql.append(t == 0 ? "" : t == texts.size() - 1 ? " and " : ", ").append(texts.get(t));
            }
        }
        return sql.append(template.pieces().getLast()).toString();
    }

    /** {@code transaction on %datasource%:}. */
    static final class Transaction implements PausingSection {

        private final Engine engine;

        Transaction(Engine engine) {
            this.engine = engine;
        }

        @Override
        public CompletionStage<?> start(TriggerContext context, Object[] args) {
            if (!(args[0] instanceof Database database)) {
                return CompletableFuture.failedFuture(new IllegalStateException("The data source is not set"));
            }
            Object scope = scope(context);
            if (engine.transactions().find(scope, database) != null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "There is already an open transaction on " + database.shown() + " in this trigger"));
            }
            return engine.run(() -> {
                try {
                    engine.transactions().begin(scope, database, this);
                    engine.errors().record(scope, null);
                    return Boolean.TRUE;
                } catch (SQLException | RuntimeException e) {
                    engine.errors().record(scope, Engine.message(e, database));
                    return Boolean.FALSE;
                }
            }, new AtomicReference<>());
        }

        @Override
        public CompletionStage<?> end(TriggerContext context) {
            Object scope = scope(context);
            Transactions.Transaction transaction = engine.transactions().findOwned(scope, this);
            if (transaction == null) {
                return null;
            }
            return engine.run(() -> {
                try {
                    transaction.commit();
                    engine.errors().record(scope, null);
                } catch (SQLException | RuntimeException e) {
                    transaction.rollback();
                    engine.errors().record(scope, Engine.message(e, transaction.database()));
                } finally {
                    engine.transactions().end(scope, transaction);
                }
                return null;
            }, new AtomicReference<>());
        }
    }

    /**
     * A database in a saved variable, as skript-db saves it: its URL, and the lifetime and driver a script gave
     * it. It loads as the same database, without connecting.
     */
    private record DatabaseCodec(Engine engine) implements VariableCodec<Database> {

        @Override
        public void encode(Database database, BinaryWriter out) {
            Long lifetime = database.maxLifetimeMillis();
            String driver = database.driver();
            out.beginObject(1 + (lifetime != null ? 1 : 0) + (driver != null ? 1 : 0));
            out.stringField("jdbcurl", database.url());
            if (lifetime != null) {
                out.boxedField("maxlifetime", lifetime);
            }
            if (driver != null) {
                out.stringField("driver", driver);
            }
        }

        @Override
        public Database decode(BinaryReader in) {
            int fields = in.beginObject();
            String url = in.stringField("jdbcurl");
            Long lifetime = null;
            String driver = null;
            if (fields == 3 || fields == 2) {
                Object value = in.anyField(fields == 3 ? "maxlifetime" : "maxlifetime");
                lifetime = value instanceof Number number ? number.longValue() : null;
            }
            if (fields == 3) {
                driver = in.stringField("driver");
            }
            return engine.database(url, lifetime, driver);
        }
    }
}
