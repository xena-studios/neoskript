/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs statements and turns their results into what scripts store, as skript-db 2.0 does:
 * <ul>
 *   <li>in a list variable, {@code {_rows::*}}: each column's name (lower case) at {@code {_rows::<column>}}, and
 *       its value in row n at {@code {_rows::<column>::<n>}}, from 1; an array value is a list under that;</li>
 *   <li>in a single variable: the number of rows a query gave, or the rows a change affected; with
 *       {@code generated keys}, the first key.</li>
 * </ul>
 * Unlike skript-db, a list gets at most {@code max-rows} rows (a result with more fails, rather than running the
 * server out of memory), and rows aren't read at all where nothing stores them.
 */
final class Statements {

    /** Where a statement's result goes. */
    enum Target {
        /** Nowhere: {@code execute "..." in {db}}. */
        NONE,
        /** A single variable: a count, or the first generated key. */
        SINGLE,
        /** A list variable: every row. */
        LIST
    }

    private final Settings settings;

    Statements(Settings settings) {
        this.settings = settings;
    }

    /**
     * Runs a statement and gives what its target stores.
     *
     * @param connection the connection
     * @param query the SQL and its parameters
     * @param keys whether to give the generated keys rather than the result
     * @param target where the result goes
     * @param running set to the statement while it runs, so it can be cancelled
     * @return what to store: a map for a list (its indices), a {@code Long} or a key for a single variable, or
     *     {@code null} for nothing
     * @throws SQLException if it fails, or a list would get more than {@code max-rows} rows
     */
    Object execute(Connection connection, Query query, boolean keys, Target target, Running running)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.sql(),
                keys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            running.started(statement);
            bind(statement, query.parameters());
            if (settings.queryTimeoutSeconds() > 0) {
                statement.setQueryTimeout(settings.queryTimeoutSeconds());
            }
            if (target == Target.LIST && settings.maxRows() > 0) {
                // One more than the most, to tell a result that's too big.
                statement.setMaxRows(settings.maxRows() == Integer.MAX_VALUE ? 0 : settings.maxRows() + 1);
            }
            if (statement.execute()) {
                try (ResultSet rows = statement.getResultSet()) {
                    return read(rows, target, false);
                }
            }
            long affected = Math.max(statement.getUpdateCount(), 0);
            if (keys) {
                try (ResultSet generated = statement.getGeneratedKeys()) {
                    return read(generated, target, true);
                }
            }
            return switch (target) {
                case SINGLE -> affected;
                // A list for a change gets nothing: it's emptied, as skript-db does.
                case LIST -> new LinkedHashMap<String, Object>();
                case NONE -> null;
            };
        } finally {
            running.finished();
        }
    }

    /**
     * Runs a statement once for each row of parameters, in one round trip, in a transaction of its own unless
     * it's in one already, as skript-db's {@code with batch arguments} does.
     *
     * @return how many rows were affected in all
     */
    long executeBatch(Connection connection, String sql, List<List<Object>> rows, boolean ownTransaction,
            Running running) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }
        boolean own = ownTransaction && connection.getAutoCommit();
        if (own) {
            connection.setAutoCommit(false);
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            running.started(statement);
            if (settings.queryTimeoutSeconds() > 0) {
                statement.setQueryTimeout(settings.queryTimeoutSeconds());
            }
            for (List<Object> row : rows) {
                bind(statement, row);
                statement.addBatch();
            }
            long total = 0;
            for (int count : statement.executeBatch()) {
                if (count > 0) {
                    total += count;
                }
            }
            if (own) {
                connection.commit();
            }
            return total;
        } catch (SQLException | RuntimeException e) {
            if (own) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
            }
            throw e;
        } finally {
            running.finished();
            if (own) {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            statement.setObject(index++, parameter);
        }
    }

    private Object read(ResultSet rows, Target target, boolean keys) throws SQLException {
        switch (target) {
            case NONE -> {
                return null;
            }
            case SINGLE -> {
                if (keys) {
                    return rows.next() && rows.getMetaData().getColumnCount() > 0
                            ? JdbcValues.fromJdbc(rows.getObject(1)) : null;
                }
                long count = 0;
                while (rows.next()) {
                    count++;
                }
                return count;
            }
            default -> {
                return list(rows);
            }
        }
    }

    private Map<String, Object> list(ResultSet rows) throws SQLException {
        ResultSetMetaData meta = rows.getMetaData();
        int columns = meta.getColumnCount();
        List<String> labels = new ArrayList<>(columns);
        List<String> names = new ArrayList<>(columns);
        Map<String, Object> list = new LinkedHashMap<>();
        for (int column = 1; column <= columns; column++) {
            String label = meta.getColumnLabel(column);
            labels.add(label);
            names.add(label.toLowerCase(Locale.ENGLISH));
            list.put(names.getLast(), label);
        }
        int row = 0;
        int max = settings.maxRows();
        while (rows.next()) {
            row++;
            if (max > 0 && row > max) {
                throw new SQLException("The result has more than " + max + " rows, the most a list gets"
                        + " (max-rows in plugins/NeoSkriptDb/config.yml). Ask for fewer with LIMIT.");
            }
            for (int column = 1; column <= columns; column++) {
                put(list, names.get(column - 1) + "::" + row, JdbcValues.fromJdbc(rows.getObject(column)));
            }
        }
        return list;
    }

    private static void put(Map<String, Object> list, String index, Object value) {
        if (value instanceof List<?> elements) {
            int i = 1;
            for (Object element : elements) {
                put(list, index + "::" + i++, element);
            }
        } else if (value != null) {
            list.put(index, value);
        }
    }
}
