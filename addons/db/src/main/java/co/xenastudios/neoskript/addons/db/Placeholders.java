/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

/**
 * Counts the {@code ?} parameters in SQL, as skript-db does: not in quotes, back-quotes or comments, and not
 * {@code ??} or {@code \?}.
 */
final class Placeholders {

    private Placeholders() {}

    static int count(String sql) {
        int count = 0;
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
            } else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? length : end + 1;
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else if (c == '\\' && i + 1 < length && sql.charAt(i + 1) == '?') {
                i += 2;
            } else if (c == '?') {
                if (i + 1 < length && sql.charAt(i + 1) == '?') {
                    i += 2;
                } else {
                    count++;
                    i++;
                }
            } else {
                i++;
            }
        }
        return count;
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return i;
    }
}
