/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Damaged values in {@code variables.csv} are refused, so they're kept as saved, not a crash. */
class SkriptBinaryReaderTest {

    private static SkriptBinaryReader reader(String hex) {
        return new SkriptBinaryReader("string", HexFormat.of().parseHex(hex));
    }

    @Test
    void readsATextWithItsLength() {
        assertEquals("AB", reader("80024142").rawString());
        assertEquals("AB", reader("000000024142").rawString());
    }

    @Test
    void aLengthPastTheEndIsRefused() {
        // 0x41414141 bytes: making that array ran the server out of memory, which the load didn't catch, so
        // NeoSkript failed to start.
        assertThrows(IllegalStateException.class, () -> reader("41414141" + "41".repeat(40)).rawString());
        assertThrows(IllegalStateException.class, () -> reader("80ff4142").rawString());
        assertThrows(IllegalStateException.class, () -> reader("ffffffff").rawString());
    }
}
