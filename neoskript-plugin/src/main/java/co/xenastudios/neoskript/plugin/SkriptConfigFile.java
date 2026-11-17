/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.frontend.structure.Comments;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A file in Skript's config format, as Skript reads config.sk and its language files: the entries, by their path
 * ({@code disable hooks.regions.worldguard}) in lower case, with their values.
 *
 * <p>As Skript's {@code SectionNode}: a line whose only {@code :} ends it opens a section, which holds the lines
 * indented more than it; any other line is an entry, its key before the first {@code :} and its value after,
 * trimmed. Comments are taken off as Skript takes them off ({@link Comments}), and {@code ###} lines open and close
 * block comments. Keys ignore case, and of two entries with the same key the last counts.
 */
final class SkriptConfigFile {

    private SkriptConfigFile() {}

    private record Section(int indent, String path) {}

    static Map<String, String> read(String text) {
        Map<String, String> entries = new LinkedHashMap<>();
        Deque<Section> sections = new ArrayDeque<>();
        boolean inBlockComment = false;
        for (String line : text.split("\r\n|\r|\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.equals(Comments.BLOCK_DELIMITER)) {
                inBlockComment = !inBlockComment;
                continue;
            }
            if (inBlockComment || trimmed.startsWith("#")) {
                continue;
            }
            String code = Comments.strip(line).strip();
            if (code.isEmpty()) {
                continue;
            }
            int indent = indent(line);
            while (!sections.isEmpty() && sections.peek().indent() >= indent) {
                sections.pop();
            }
            String parent = sections.isEmpty() ? "" : sections.peek().path() + ".";
            int colon = code.indexOf(':');
            if (colon == code.length() - 1) {
                String key = code.substring(0, colon).strip().toLowerCase(Locale.ENGLISH);
                sections.push(new Section(indent, parent + key));
                continue;
            }
            if (colon < 0) {
                // Not an entry: Skript reports it and goes on.
                continue;
            }
            String key = code.substring(0, colon).strip().toLowerCase(Locale.ENGLISH);
            String path = parent + key;
            // The last of two entries with the same key counts, and keeps its place.
            entries.remove(path);
            entries.put(path, code.substring(colon + 1).strip());
        }
        return entries;
    }

    /** The width of a line's indentation, a tab counting as one, as Skript counts it. */
    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    /** Skript's yes/no values: {@code true}, {@code yes} or {@code on}, and {@code false}, {@code no} or {@code off}. */
    static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ENGLISH)) {
            case "true", "yes", "on" -> Boolean.TRUE;
            case "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }
}
