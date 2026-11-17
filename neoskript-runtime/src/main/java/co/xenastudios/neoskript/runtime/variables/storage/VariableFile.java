/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skript's {@code variables.csv}: a comment header, then one {@code name, type, hex} line per variable,
 * then an empty line. Names with a comma, quote or whitespace are quoted CSV-style. When a name appears twice the last line wins, and a
 * line without a type deletes the variable.
 */
public final class VariableFile {

    /** One saved variable. */
    public record Entry(String name, String type, byte[] bytes) {}

    /** The header Skript writes. NeoSkript writes the version of Skript whose format it matches. */
    static final String HEADER = """
            # === Skript's variable storage ===
            # Please do not modify this file manually!
            #
            # version: 2.17.0

            """;

    private VariableFile() {}

    /** A line that couldn't be read and was skipped, with its number, its text and why. */
    public record Problem(int line, String text, String name, String message) {}

    /** What a file holds: the variables it could read, and the lines it skipped. */
    public record Contents(Map<String, Entry> entries, List<Problem> problems) {}

    /** The variables in the file. Lines that can't be read are skipped; {@link #load} says which. */
    public static Map<String, Entry> read(Path file) throws IOException {
        return load(file).entries();
    }

    /**
     * Reads the file as Skript does: a line that can't be read is skipped and the rest are kept. A skipped line
     * changes nothing, so an earlier line for the same variable still counts. The file is read a line at a time,
     * never held in memory whole, so a file of hundreds of MB needs no more heap than its variables do.
     */
    public static Contents load(Path file) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        HexFormat hex = HexFormat.of();
        int number = 0;
        // Bytes that aren't UTF-8 (a line cut off mid-character) become U+FFFD instead of failing the read,
        // as with Skript's reader. Lines end at \n, \r or \r\n, as String.lines() ends them.
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = in.readLine()) != null) {
                number++;
                read(line, number, hex, entries, problems);
            }
        }
        return new Contents(entries, problems);
    }

    /**
     * The names of the variables the file holds, as {@link #load} would read them, without their values: read a line
     * at a time, so only the names are held.
     */
    public static java.util.Set<String> names(Path file) throws IOException {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> fields = split(line);
                if (fields.size() != 3) {
                    continue;
                }
                if (fields.get(1).isEmpty()) {
                    names.remove(fields.getFirst());
                } else if (isHex(fields.get(2))) {
                    names.add(fields.getFirst());
                }
            }
        }
        return names;
    }

    private static boolean isHex(String text) {
        if (text.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** One line of the file, line {@code number}: into {@code entries}, or {@code problems} if it can't be read. */
    private static void read(String line, int number, HexFormat hex, Map<String, Entry> entries,
            List<Problem> problems) {
        if (line.isBlank() || line.startsWith("#")) {
            return;
        }
        List<String> fields = split(line);
        String name = fields.getFirst();
        String shown = name.isEmpty() ? "<unknown>" : name;
        if (fields.size() != 3) {
            // Skript's message.
            problems.add(new Problem(number, line, shown,
                    "invalid amount of commas in line " + number + " ('" + line + "')"));
            return;
        }
        if (fields.get(1).isEmpty()) {
            entries.remove(name);
            return;
        }
        byte[] bytes;
        try {
            bytes = hex.parseHex(fields.get(2));
        } catch (IllegalArgumentException e) {
            problems.add(new Problem(number, line, shown,
                    "invalid value in line " + number + " ('" + line + "'): " + e.getMessage()));
            return;
        }
        entries.remove(name);
        entries.put(name, new Entry(name, fields.get(1), bytes));
    }

    /**
     * Writes the entries, in the order given, to a temporary file that's synced to disk and then replaces
     * {@code file}, so a crash or power cut leaves either the old file or the new one. The file is streamed, never
     * held in memory whole.
     */
    public static void write(Path file, Iterable<Entry> entries) throws IOException {
        write(file, entries, List.of());
    }

    /**
     * As {@link #write(Path, Iterable)}, with {@code comments} as {@code #} lines after Skript's header, for files of
     * NeoSkript's own in this format. An entry with an empty type is written as a deletion.
     */
    static void write(Path file, Iterable<Entry> entries, List<String> comments) throws IOException {
        Durable.replace(file, stream -> {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), 1 << 16);
            if (comments.isEmpty()) {
                out.write(HEADER);
            } else {
                out.write(HEADER.stripTrailing());
                out.write('\n');
                for (String comment : comments) {
                    out.write("# ");
                    out.write(comment.replace('\n', ' '));
                    out.write('\n');
                }
                out.write('\n');
            }
            HexFormat hex = HexFormat.of().withUpperCase();
            for (Entry entry : entries) {
                out.write(quote(entry.name()));
                out.write(", ");
                out.write(entry.type());
                out.write(", ");
                out.write(hex.formatHex(entry.bytes()));
                out.write('\n');
            }
            // Skript ends the file with an empty line.
            out.write('\n');
            // Flushed, not closed: the file is synced before it's closed.
            out.flush();
        });
    }

    static String quote(String field) {
        if (!field.startsWith("#") && field.chars().noneMatch(c -> c == ',' || c == '"' || Character.isWhitespace(c))) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }

    /** Splits a CSV line on ", ", with quoted fields. */
    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().strip());
        return fields;
    }
}
