package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.variable.VariableCodec;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JDBC-backed variable store (SQLite by default; works with any JDBC URL, e.g. H2/MySQL). Values
 * are encoded via {@link VariableCodec} into a {@code (name, type, value)} table. Save replaces the
 * whole table inside a transaction so it stays portable across SQL dialects (no upsert needed).
 */
public final class SqlVariableStore {

    private static final String TABLE = "neoskript_variables";

    private final String jdbcUrl;

    public SqlVariableStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE
                    + " (name VARCHAR(255) PRIMARY KEY, type VARCHAR(64), val CLOB)");
        }
        return connection;
    }

    /**
     * Loads all variables.
     *
     * @return the stored variables
     * @throws SQLException on a database error
     */
    public Map<String, Object> load() throws SQLException {
        Map<String, Object> variables = new LinkedHashMap<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name, type, val FROM " + TABLE)) {
            while (rs.next()) {
                Object value = VariableCodec.decode(rs.getString("type"), rs.getString("val"));
                if (value != null) {
                    variables.put(rs.getString("name"), value);
                }
            }
        }
        return variables;
    }

    /**
     * Replaces all stored variables with the given map (within a transaction).
     *
     * @param variables the variables to persist
     * @throws SQLException on a database error
     */
    public void save(Map<String, Object> variables) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.execute("DELETE FROM " + TABLE);
            }
            try (PreparedStatement insert =
                         connection.prepareStatement("INSERT INTO " + TABLE + " (name, type, val) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    VariableCodec.Encoded encoded = VariableCodec.encode(entry.getValue());
                    if (encoded == null) {
                        continue;
                    }
                    insert.setString(1, entry.getKey());
                    insert.setString(2, encoded.type());
                    insert.setString(3, encoded.value());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        }
    }
}
