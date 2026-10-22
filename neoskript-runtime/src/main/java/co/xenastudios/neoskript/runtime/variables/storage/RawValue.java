/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * A saved value NeoSkript can't turn into a value yet (an item, before NeoSkript has items), kept exactly
 * as it was saved so it's written back unchanged.
 */
public record RawValue(String type, byte[] bytes) {

    @Override
    public boolean equals(Object other) {
        return other instanceof RawValue raw && raw.type.equals(type) && Arrays.equals(raw.bytes, bytes);
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "<" + type + ">";
    }

    String hex() {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
