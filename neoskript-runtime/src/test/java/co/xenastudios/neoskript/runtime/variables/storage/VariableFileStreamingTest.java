/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading {@code variables.csv} a line at a time gives exactly what reading it whole gave. */
class VariableFileStreamingTest {

    @TempDir
    Path dir;

    /** How the file was read before: all of it into one string, then split with String.lines(). */
    private static VariableFile.Contents wholeFile(Path file) throws IOException {
        Map<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        List<VariableFile.Problem> problems = new ArrayList<>();
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        HexFormat hex = HexFormat.of();
        int number = 0;
        for (String line : (Iterable<String>) text.lines()::iterator) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> fields = VariableFile.split(line);
            String name = fields.getFirst();
            String shown = name.isEmpty() ? "<unknown>" : name;
            if (fields.size() != 3) {
                problems.add(new VariableFile.Problem(number, line, shown,
                        "invalid amount of commas in line " + number + " ('" + line + "')"));
                continue;
            }
            if (fields.get(1).isEmpty()) {
                entries.remove(name);
                continue;
            }
            byte[] bytes;
            try {
                bytes = hex.parseHex(fields.get(2));
            } catch (IllegalArgumentException e) {
                problems.add(new VariableFile.Problem(number, line, shown,
                        "invalid value in line " + number + " ('" + line + "'): " + e.getMessage()));
                continue;
            }
            entries.remove(name);
            entries.put(name, new VariableFile.Entry(name, fields.get(1), bytes));
        }
        return new VariableFile.Contents(entries, problems);
    }

    private static void assertSame(VariableFile.Contents expected, VariableFile.Contents actual, String what) {
        assertEquals(expected.problems(), actual.problems(), what);
        assertEquals(List.copyOf(expected.entries().keySet()), List.copyOf(actual.entries().keySet()), what);
        for (VariableFile.Entry entry : expected.entries().values()) {
            VariableFile.Entry other = actual.entries().get(entry.name());
            assertEquals(entry.type(), other.type(), what);
            assertArrayEquals(entry.bytes(), other.bytes(), what);
        }
    }

    @Test
    void randomFilesReadTheSameAsWhole() throws IOException {
        Random random = new Random(7);
        String[] pieces = {"a", "b::1", "B::1", "\"q, \"\"x\"\"\"", ", ", ",", "string", "long", "0161", "zz", "",
                " ", "#", "\n", "\r", "\r\n", "\n\n", "é", "😀", "\"", "::"};
        for (int round = 0; round < 3000; round++) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int parts = random.nextInt(60);
            for (int i = 0; i < parts; i++) {
                if (random.nextInt(10) == 0) {
                    // Bytes that aren't UTF-8: cut-off characters, stray continuations.
                    byte[] junk = new byte[1 + random.nextInt(4)];
                    for (int j = 0; j < junk.length; j++) {
                        junk[j] = (byte) (0x80 | random.nextInt(0x80));
                    }
                    out.writeBytes(junk);
                } else if (random.nextInt(4) == 0) {
                    out.writeBytes((pieces[random.nextInt(2)] + ", string, " + "0" + random.nextInt(10) + "62\n")
                            .getBytes(StandardCharsets.UTF_8));
                } else {
                    out.writeBytes(pieces[random.nextInt(pieces.length)].getBytes(StandardCharsets.UTF_8));
                }
            }
            Path file = dir.resolve("variables.csv");
            Files.write(file, out.toByteArray());
            assertSame(wholeFile(file), VariableFile.load(file), "round " + round);
        }
    }

    @Test
    void longLinesAndCharactersAcrossTheReadersBufferReadTheSame() throws IOException {
        StringBuilder text = new StringBuilder(VariableFile.HEADER);
        HexFormat hex = HexFormat.of().withUpperCase();
        for (int i = 0; i < 300; i++) {
            String name = "v::" + i + "é😀".repeat(i % 7);
            byte[] value = ("x".repeat(i * 977 % 90_000)).getBytes(StandardCharsets.UTF_8);
            text.append(VariableFile.quote(name)).append(", string, ").append(hex.formatHex(value))
                    .append(i % 3 == 0 ? "\r\n" : "\n");
        }
        Path file = dir.resolve("variables.csv");
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        // Cut off mid-character at the end, as a crash leaves it.
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 2));
        assertSame(wholeFile(file), VariableFile.load(file), "long lines");
    }
}
