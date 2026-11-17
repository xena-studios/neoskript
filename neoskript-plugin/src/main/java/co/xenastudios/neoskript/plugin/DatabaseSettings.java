/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.plugin.config.SectionCodec;
import co.xenastudios.neoskript.runtime.variables.storage.SqlVariableStorage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The {@code databases:} section of config.yml, as Skript's in config.sk: where variables are saved, by name, each
 * with its type and options, checked from the top. A database that can't be used as written is kept, with why, so it
 * still owns its pattern and its variables aren't saved anywhere else by mistake.
 *
 * <p>Keys are config.yml's kebab case ({@code monitor-changes}); Skript's own ({@code monitor changes}) work too, so
 * the section can be copied from config.sk as it is.
 */
public final class DatabaseSettings {

    /** What a database is. */
    public enum Type {
        CSV, NATIVE, SQLITE, MYSQL, DISABLED;

        /** Skript's names for them, and NeoSkript's {@code native}; {@code null} if it's none. */
        static Type parse(String text) {
            return switch (text.strip().toLowerCase(Locale.ROOT)) {
                case "csv", "file", "flatfile" -> CSV;
                case "native" -> NATIVE;
                case "sqlite" -> SQLITE;
                case "mysql" -> MYSQL;
                case "disabled", "none" -> DISABLED;
                default -> null;
            };
        }

        boolean file() {
            return this == CSV || this == NATIVE || this == SQLITE;
        }

        boolean sql() {
            return this == SQLITE || this == MYSQL;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One database.
     *
     * @param typeText the type as written
     * @param file the file, relative to plugins/NeoSkript, for CSV, native and SQLite
     * @param backupInterval how often the file is backed up, or {@code null} for never
     * @param backupsToKeep how many of its backups to keep, or -1 for all
     * @param problem why it can't be used, or {@code null}
     */
    public record Database(String name, Type type, String typeText, String pattern, String file,
            boolean monitorChanges, NeoSkriptConfig.Interval monitorInterval, NeoSkriptConfig.Interval backupInterval,
            int backupsToKeep, String host, int port, String user, String password, String database, String table,
            String problem) {

        @Override
        public String toString() {
            // Never the password.
            return name + " (" + typeText + ")";
        }

        /** The same settings, ignoring the password's text but not whether it changed. */
        boolean sameAs(Database other) {
            return equals(other);
        }
    }

    /** Every key a database may have, in config.yml's case. */
    static final Set<String> KEYS = Set.of("type", "pattern", "monitor-changes", "monitor-interval", "file",
            "backup-interval", "backups-to-keep", "host", "port", "user", "password", "database", "table");

    private final List<Database> databases;

    DatabaseSettings(List<Database> databases) {
        this.databases = List.copyOf(databases);
    }

    /** The databases, from the top, without the disabled ones. */
    public List<Database> enabled() {
        return databases.stream().filter(d -> d.type() != Type.DISABLED).toList();
    }

    public List<Database> all() {
        return databases;
    }

    /** The default: everything in variables.csv, as Skript's default config. */
    static final DatabaseSettings DEFAULTS = new DatabaseSettings(List.of(new Database("default", Type.CSV, "csv", ".*",
            "variables.csv", false, NeoSkriptConfig.Interval.parse("20 seconds"),
            NeoSkriptConfig.Interval.parse("2 hours"), 12, null, 3306, null, null, null,
            SqlVariableStorage.DEFAULT_TABLE, null)));

    @Override
    public boolean equals(Object other) {
        return other instanceof DatabaseSettings that && databases.equals(that.databases);
    }

    @Override
    public int hashCode() {
        return databases.hashCode();
    }

    @Override
    public String toString() {
        return databases.toString();
    }

    /** Reads and writes the section. */
    static final SectionCodec<DatabaseSettings> CODEC = new SectionCodec<>() {
        @Override
        public DatabaseSettings decode(Map<String, Object> section, String path, List<String> warnings) {
            List<Database> databases = new ArrayList<>();
            Set<String> files = new java.util.HashSet<>();
            for (Map.Entry<String, Object> entry : section.entrySet()) {
                String at = path + "." + entry.getKey();
                if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                    // Skript's message.
                    warnings.add(at + ": Invalid line in databases: databases must be defined as sections; it's "
                            + "ignored.");
                    continue;
                }
                Database database = database(entry.getKey(), raw, at, warnings);
                if (database.problem() == null && database.type().file() && !files.add(normalise(database.file()))) {
                    database = withProblem(database, "the file " + database.file() + " is already used by another "
                            + "database");
                }
                if (database.problem() == null && database.type() == Type.MYSQL && !files.add("mysql|"
                        + database.host().toLowerCase(Locale.ROOT) + "|" + database.port() + "|" + database.database()
                        + "|" + database.table().toLowerCase(Locale.ROOT))) {
                    database = withProblem(database, "the table " + database.table() + " in " + database.host() + ":"
                            + database.port() + "/" + database.database() + " is already used by another database");
                }
                if (database.problem() != null) {
                    warnings.add(at + ": " + database.problem() + ". Its variables aren't saved until it's fixed.");
                }
                databases.add(database);
            }
            if (databases.stream().noneMatch(d -> d.type() != Type.DISABLED)) {
                // Skript's message.
                throw new IllegalArgumentException("has no databases to store variables. Please enable at least the "
                        + "default database, even if you don't use variables at all");
            }
            return new DatabaseSettings(databases);
        }

        @Override
        public List<String> render(DatabaseSettings value) {
            List<String> lines = new ArrayList<>();
            for (Database database : value.databases) {
                lines.add(key(database.name()) + ":");
                for (Map.Entry<String, Object> setting : settings(database).entrySet()) {
                    Object v = setting.getValue();
                    String text = v instanceof String s ? quote(s) : String.valueOf(v);
                    lines.add("  " + setting.getKey() + ": " + text);
                }
            }
            return lines;
        }

        @Override
        public Map<String, Object> plain(DatabaseSettings value) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Database database : value.databases) {
                map.put(database.name(), settings(database));
            }
            return map;
        }
    };

    /** A database's settings as config.yml writes them: only those its type uses. */
    static Map<String, Object> settings(Database database) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", database.typeText());
        map.put("pattern", database.pattern());
        if (database.type().sql()) {
            map.put("monitor-changes", database.monitorChanges());
            map.put("monitor-interval", database.monitorInterval().toString());
        }
        if (database.type() == Type.MYSQL) {
            map.put("host", database.host());
            map.put("port", database.port());
            map.put("user", database.user());
            map.put("password", database.password());
            map.put("database", database.database());
            map.put("table", database.table());
        } else {
            if (database.file() != null) {
                map.put("file", database.file());
            }
            map.put("backup-interval", database.backupInterval() == null ? "0" : database.backupInterval().toString());
            map.put("backups-to-keep", database.backupsToKeep());
            if (database.type() == Type.SQLITE) {
                map.put("table", database.table());
            }
        }
        return map;
    }

    /** Reads one database, keeping why it can't be used rather than dropping it. */
    static Database database(String name, Map<?, ?> raw, String at, List<String> warnings) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey()).strip().toLowerCase(Locale.ROOT).replace(' ', '-');
            if (!KEYS.contains(key)) {
                warnings.add(at + "." + entry.getKey() + " isn't a database setting; it's ignored.");
                continue;
            }
            values.put(key, entry.getValue());
        }
        List<String> problems = new ArrayList<>();
        String typeText = text(values.get("type"));
        Type type;
        if (typeText == null) {
            // Skript's message.
            problems.add("Missing entry 'type' in database definition");
            type = Type.CSV;
            typeText = "?";
        } else {
            type = Type.parse(typeText);
            if (type == null) {
                // Kept, owning its pattern, with only this to say: its other settings depend on the type.
                String pattern = text(values.get("pattern"));
                return new Database(name, Type.CSV, typeText, pattern == null || !compiles(pattern) ? "(?!)" : pattern,
                        null, false, NeoSkriptConfig.Interval.parse("20 seconds"), null, -1, null, 3306, null, null,
                        null, SqlVariableStorage.DEFAULT_TABLE, "Invalid database type '" + typeText + "'");
            }
        }
        if (type == Type.DISABLED) {
            return new Database(name, type, typeText, Objects.requireNonNullElse(text(values.get("pattern")), ".*"),
                    null, false, NeoSkriptConfig.Interval.parse("20 seconds"), null, -1, null, 3306, null, null, null,
                    SqlVariableStorage.DEFAULT_TABLE, null);
        }
        String pattern = text(values.get("pattern"));
        if (pattern == null) {
            problems.add("The config is missing the entry for 'pattern' in the database '" + name + "'");
            pattern = "(?!)";
        } else {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                problems.add("Invalid pattern '" + pattern + "': " + e.getDescription());
            }
        }
        boolean monitor = false;
        Object monitorRaw = values.get("monitor-changes");
        if (monitorRaw != null) {
            String m = String.valueOf(monitorRaw).strip().toLowerCase(Locale.ROOT);
            switch (m) {
                case "true", "yes", "on" -> monitor = true;
                case "false", "no", "off" -> monitor = false;
                default -> warnings.add(at + ".monitor-changes (\"" + monitorRaw + "\") should be true or false; using "
                        + "false.");
            }
        }
        NeoSkriptConfig.Interval monitorInterval = interval(values.get("monitor-interval"), "20 seconds",
                at + ".monitor-interval", warnings, false);
        if (monitorInterval.millis() < 1_000) {
            warnings.add(at + ".monitor-interval is less than a second; using 1 second.");
            monitorInterval = NeoSkriptConfig.Interval.parse("1 second");
        }
        String file = null;
        NeoSkriptConfig.Interval backupInterval = null;
        int backupsToKeep = -1;
        String host = null;
        int port = 3306;
        String user = null;
        String password = null;
        String databaseName = null;
        String table = Objects.requireNonNullElse(text(values.get("table")), SqlVariableStorage.DEFAULT_TABLE);
        if (type.sql() && !SqlVariableStorage.TABLE_NAME.matcher(table).matches()) {
            problems.add("'" + table + "' isn't a table name: use letters, digits and _");
        }
        if (type.file()) {
            file = text(values.get("file"));
            if (file == null) {
                problems.add("The config is missing the entry for 'file' in the database '" + name + "'");
            } else if (type == Type.SQLITE && !file.toLowerCase(Locale.ROOT).endsWith(".db")) {
                // As Skript: an SQLite file always ends in .db.
                file = file + ".db";
            }
            backupInterval = interval(values.get("backup-interval"), "0", at + ".backup-interval", warnings, true);
            Object keep = values.get("backups-to-keep");
            if (keep != null) {
                try {
                    backupsToKeep = Integer.parseInt(String.valueOf(keep).strip());
                    if (backupsToKeep < -1) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    warnings.add(at + ".backups-to-keep (\"" + keep + "\") should be a whole number, -1 for all; "
                            + "using -1.");
                    backupsToKeep = -1;
                }
            }
        }
        if (type == Type.MYSQL) {
            host = required(values, "host", name, problems);
            user = required(values, "user", name, problems);
            password = values.get("password") == null ? null : String.valueOf(values.get("password"));
            if (password == null) {
                problems.add("The config is missing the entry for 'password' in the database '" + name + "'");
            }
            databaseName = required(values, "database", name, problems);
            Object portRaw = values.get("port");
            if (portRaw == null) {
                problems.add("The config is missing the entry for 'port' in the database '" + name + "'");
            } else {
                try {
                    port = Integer.parseInt(String.valueOf(portRaw).strip());
                    if (port < 1 || port > 65535) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    problems.add("The entry for 'port' in the database '" + name + "' must be a port, from 1 to 65535");
                }
            }
        }
        return new Database(name, type, typeText, pattern, file, monitor, monitorInterval, backupInterval,
                backupsToKeep, host, port, user, password, databaseName, table,
                problems.isEmpty() ? null : String.join("; ", problems));
    }

    private static boolean compiles(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static Database withProblem(Database d, String problem) {
        return new Database(d.name(), d.type(), d.typeText(), d.pattern(), d.file(), d.monitorChanges(),
                d.monitorInterval(), d.backupInterval(), d.backupsToKeep(), d.host(), d.port(), d.user(), d.password(),
                d.database(), d.table(), problem);
    }

    private static String required(Map<String, Object> values, String key, String name, List<String> problems) {
        String value = text(values.get(key));
        if (value == null) {
            problems.add("The config is missing the entry for '" + key + "' in the database '" + name + "'");
        }
        return value;
    }

    /** A length of time; with {@code zeroIsOff}, {@code 0} (or any zero length) is {@code null}, for never. */
    private static NeoSkriptConfig.Interval interval(Object raw, String fallback, String at, List<String> warnings,
            boolean zeroIsOff) {
        String text = raw == null ? fallback : String.valueOf(raw).strip();
        // Only the text 0 is off, as Skript checks before anything else (VariablesStorage): "0 seconds" is an
        // interval, which with backups-to-keep 0 still removes the backups, as in Skript.
        if (zeroIsOff && (text.equals("0") || text.isEmpty())) {
            return null;
        }
        try {
            return NeoSkriptConfig.Interval.parse(text);
        } catch (IllegalArgumentException e) {
            warnings.add(at + " (\"" + raw + "\") " + e.getMessage() + "; using " + fallback + ".");
            return zeroIsOff && fallback.equals("0") ? null : NeoSkriptConfig.Interval.parse(fallback);
        }
    }

    private static String text(Object raw) {
        if (raw == null || raw instanceof Map<?, ?> || raw instanceof List<?>) {
            return null;
        }
        String text = String.valueOf(raw).strip();
        return text.isEmpty() ? null : text;
    }

    private static String normalise(String file) {
        return java.nio.file.Path.of(file).normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private static String key(String key) {
        return PLAIN_KEY.matcher(key).matches() ? key : quote(key);
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
