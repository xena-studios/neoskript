/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public static Map<String, Entry> read(Path file) throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        int number = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> fields = split(line);
            if (fields.size() != 3) {
                throw new IOException(file + ", line " + number + ": expected 'name, type, value'");
            }
            String name = fields.get(0);
            if (fields.get(1).isEmpty()) {
                entries.remove(name);
            } else {
                entries.remove(name);
                entries.put(name, new Entry(name, fields.get(1), HexFormat.of().parseHex(fields.get(2))));
            }
        }
        return entries;
    }

    /** Writes the entries, in the order given, to a temporary file that then replaces {@code file}. */
    public static void write(Path file, Iterable<Entry> entries) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try (BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            out.write(HEADER);
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
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
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
