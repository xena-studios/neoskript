/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code variables.csv} of more than 300 MB loads with a heap of 512 MB ({@code largeFileTest}): read whole, the
 * file alone took twice that, as bytes and then as text.
 */
@Tag("large-file")
class VariableFileLargeTest {

    private static final int VARIABLES = 32_000;
    private static final int VALUE_BYTES = 5_000;

    @TempDir
    Path dir;

    @Test
    void aFileOfMoreThan300MbLoadsWithA512MbHeap() throws IOException {
        long heap = Runtime.getRuntime().maxMemory();
        assertTrue(heap <= 600L << 20, "run with -Xmx512m (largeFileTest), not " + (heap >> 20) + " MB");
        Path file = dir.resolve("variables.csv");
        byte[] value = new byte[VALUE_BYTES];
        Arrays.fill(value, (byte) 'x');
        java.util.HexFormat hex = java.util.HexFormat.of().withUpperCase();
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(VariableFile.HEADER);
            String hexValue = hex.formatHex(value);
            for (int i = 0; i < VARIABLES; i++) {
                // Each saved twice, as a file Skript appended to may have them: the last one counts.
                out.write("big::" + i + ", string, " + hexValue + "\n");
                out.write("big::" + i + ", string, " + hexValue.substring(0, hexValue.length() - 2) + "79\n");
            }
            out.write("broken line\n\n");
        }
        long size = Files.size(file);
        assertTrue(size > 300L << 20, size + " bytes");
        VariableStore store = new VariableStore();
        long start = System.nanoTime();
        VariableStorage.Result result = new VariableStorage(store, new VariableCodecs(), file).load();
        long millis = (System.nanoTime() - start) / 1_000_000;
        Runtime runtime = Runtime.getRuntime();
        System.out.printf("%,d MB variables.csv: loaded in %,d ms with a %,d MB heap, %,d MB in use after%n",
                size >> 20, millis, heap >> 20, (runtime.totalMemory() - runtime.freeMemory()) >> 20);
        assertEquals(VARIABLES, result.variables());
        assertEquals(1, result.skipped().size());
        byte[] last = value.clone();
        last[last.length - 1] = 'y';
        assertArrayEquals(last, ((RawValue) store.get("big::31999")).bytes());
    }
}
