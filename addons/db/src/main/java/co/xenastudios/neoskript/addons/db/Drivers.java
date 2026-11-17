/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.util.Locale;

/**
 * Which JDBC driver a database's URL uses, as skript-db picks it: MariaDB and PostgreSQL from NeoSkriptDb's own,
 * MySQL and SQLite from the server's (Paper ships both). A {@code mysql:} URL uses MariaDB's driver if the server
 * has no MySQL driver.
 */
final class Drivers {

    static final String MARIADB = org.mariadb.jdbc.Driver.class.getName();
    static final String POSTGRESQL = org.postgresql.Driver.class.getName();
    // Written in parts, so the build's relocation leaves the server's drivers' names as they are.
    static final String MYSQL = String.join(".", "com", "mysql", "cj", "jdbc", "Driver");
    static final String SQLITE = String.join(".", "org", "sqlite", "JDBC");
    private static final String MARIADB_PACKAGE = String.join(".", "org", "mariadb", "jdbc");
    private static final String POSTGRESQL_PACKAGE = String.join(".", "org", "postgresql");

    private final ClassLoader loader;

    Drivers(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * A URL and the driver for it.
     *
     * @param url the URL to connect to
     * @param driver the driver's class name, or {@code null} to let JDBC find one
     */
    record Resolution(String url, String driver) {}

    /**
     * The driver for {@code url}.
     *
     * @param url a JDBC URL
     * @param explicit a driver a script or config.yml named, or {@code null}
     * @return the URL and driver to use
     */
    Resolution resolve(String url, String explicit) {
        if (explicit != null) {
            return new Resolution(url, bundled(explicit));
        }
        return switch (scheme(url)) {
            case "mariadb" -> new Resolution(url, MARIADB);
            case "postgresql" -> new Resolution(url, POSTGRESQL);
            case "mysql" -> loadable(MYSQL) ? new Resolution(url, MYSQL)
                    : new Resolution(withParameter(url, "permitMysqlScheme", "true"), MARIADB);
            case "sqlite" -> new Resolution(url, loadable(SQLITE) ? SQLITE : null);
            default -> new Resolution(url, null);
        };
    }

    /** A driver named by its usual class name, which may be NeoSkriptDb's own copy under another name. */
    String bundled(String driver) {
        if (loadable(driver)) {
            return driver;
        }
        String mapped = null;
        if (driver.startsWith(MARIADB_PACKAGE + ".")) {
            mapped = packageOf(MARIADB) + driver.substring(MARIADB_PACKAGE.length());
        } else if (driver.startsWith(POSTGRESQL_PACKAGE + ".")) {
            mapped = packageOf(POSTGRESQL) + driver.substring(POSTGRESQL_PACKAGE.length());
        }
        return mapped != null && loadable(mapped) ? mapped : driver;
    }

    private static String packageOf(String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }

    boolean loadable(String className) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * The database kind a JDBC URL names: {@code mysql} in {@code jdbc:mysql://...}.
     *
     * @param url a JDBC URL
     * @return the kind, lower case, or empty
     */
    static String scheme(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:")) {
            return "";
        }
        int end = lower.indexOf(':', 5);
        return end < 0 ? lower.substring(5) : lower.substring(5, end);
    }

    static String withParameter(String url, String name, String value) {
        int query = url.indexOf('?');
        if (query >= 0) {
            for (String pair : url.substring(query + 1).split("&")) {
                String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
                if (key.equalsIgnoreCase(name)) {
                    return url;
                }
            }
        }
        return url + (query < 0 ? "?" : "&") + name + "=" + value;
    }

    /**
     * A URL as scripts write it, as JDBC wants it: {@code mysql://...} as {@code jdbc:mysql://...}.
     *
     * @param url the URL
     * @return it, starting with {@code jdbc:}
     */
    static String normalise(String url) {
        String trimmed = url.strip();
        return trimmed.regionMatches(true, 0, "jdbc:", 0, 5) ? "jdbc:" + trimmed.substring(5) : "jdbc:" + trimmed;
    }
}
