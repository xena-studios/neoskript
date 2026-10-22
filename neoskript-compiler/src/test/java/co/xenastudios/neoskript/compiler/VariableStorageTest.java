/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import co.xenastudios.neoskript.runtime.variables.storage.RawValue;
import co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs;
import co.xenastudios.neoskript.runtime.variables.storage.VariableFile;
import co.xenastudios.neoskript.runtime.variables.storage.VariableStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Skript's {@code variables.csv}, as written by Skript 2.17.0, round-trips byte for byte. */
class VariableStorageTest {

    @TempDir
    Path dir;

    private Path skriptFile() throws Exception {
        Path file = dir.resolve("variables.csv");
        try (var in = VariableStorageTest.class.getResourceAsStream("/skript-variables.csv")) {
            Files.copy(in, file);
        }
        return file;
    }

    @Test
    void coreTypesDecodeAndReencodeToTheSameBytes() throws Exception {
        VariableCodecs codecs = new Library().codecs();
        Map<String, VariableFile.Entry> entries = VariableFile.read(skriptFile());
        int decoded = 0;
        for (VariableFile.Entry entry : entries.values()) {
            Object value = codecs.decode(entry.type(), entry.bytes());
            if (!(value instanceof RawValue)) {
                decoded++;
            }
            VariableCodecs.Encoded encoded = codecs.encode(value);
            assertEquals(entry.type(), encoded.type(), entry.name());
            assertArrayEquals(entry.bytes(), encoded.bytes(), entry.name());
        }
        assertEquals(35, decoded, "string, long, double, boolean and timespan values");
    }

    @Test
    void decodedValues() throws Exception {
        VariableStore store = new VariableStore();
        new VariableStorage(store, new Library().codecs(), skriptFile()).load();
        assertEquals("héllo ✓ 日本", store.get("t::unicode"));
        assertEquals("say \"hi\"", store.get("t::quote"));
        assertEquals(1234567890123L, store.get("t::big"));
        assertEquals(-0.5, store.get("t::dneg"));
        assertEquals(true, store.get("t::bool"));
        assertEquals(new Timespan(300_000), store.get("t::timespan"));
        assertEquals(new Timespan(150), store.get("t::ticks"));
        assertEquals(2L, store.get("name \"quoted\""));
        assertInstanceOf(RawValue.class, store.get("t::itemtype"));
        assertEquals("<location>", String.valueOf(store.get("t::loc")), "no world codec without a server");
    }

    @Test
    void theWholeFileRoundTrips() throws Exception {
        Path original = skriptFile();
        VariableStore store = new VariableStore();
        VariableStorage storage = new VariableStorage(store, new Library().codecs(), original);
        storage.load();
        Path copy = dir.resolve("copy.csv");
        new VariableStorage(store, new Library().codecs(), copy).save();
        assertEquals(Files.readString(original), Files.readString(copy));
    }

    @Test
    void savesInSkriptsOrderWithoutMemoryVariables() throws Exception {
        VariableStore store = new VariableStore();
        store.set("o::10", "ten");
        store.set("o::B", "upper b");
        store.set("o::2", "two");
        store.set("o::a", "lower a");
        store.set("-memory", 1L);
        store.set("-memory::list", 1L);
        Path file = dir.resolve("out.csv");
        VariableStorage storage = new VariableStorage(store, new Library().codecs(), file);
        assertTrue(storage.changed());
        storage.save();
        assertFalse(storage.changed());
        assertEquals("""
                # === Skript's variable storage ===
                # Please do not modify this file manually!
                #
                # version: 2.17.0

                o::2, string, 800374776F
                o::10, string, 800374656E
                o::a, string, 80076C6F7765722061
                o::b, string, 800775707065722062

                """, Files.readString(file));
    }

    @Test
    void laterLinesWinAndEmptyTypesDelete() throws Exception {
        Path file = dir.resolve("edited.csv");
        Files.writeString(file, "a, long, 0000000000000001\nb, long, 0000000000000002\na, long, 0000000000000003\nb, , \n");
        Map<String, VariableFile.Entry> entries = VariableFile.read(file);
        assertEquals(1, entries.size());
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 3}, entries.get("a").bytes());
    }

    @Test
    void longStringsUseAFourByteLength() {
        VariableCodecs codecs = new Library().codecs();
        String text = "a".repeat(40_000);
        byte[] bytes = codecs.encode(text).bytes();
        assertArrayEquals(new byte[] {0, 0, (byte) 0x9C, 0x40}, java.util.Arrays.copyOf(bytes, 4));
        assertEquals(text, codecs.decode("string", bytes));
        byte[] short255 = codecs.encode("a".repeat(256)).bytes();
        assertArrayEquals(new byte[] {(byte) 0x81, 0x00}, java.util.Arrays.copyOf(short255, 2), "Skript's 256-byte string");
    }
}
