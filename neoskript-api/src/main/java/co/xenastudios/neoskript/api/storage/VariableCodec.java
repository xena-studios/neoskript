/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.storage;

/**
 * How values of a type are saved in variables files, in the binary format Skript uses, so Skript and
 * NeoSkript can read each other's files.
 */
public interface VariableCodec<T> {

    /**
     * Writes a value. Throws {@link UnsupportedOperationException} for a value that can't be saved, as Skript
     * can't save it either: it's then left out of the file.
     */
    void encode(T value, BinaryWriter out);

    /**
     * Reads a value back. Returns {@code null} if it can't be turned into a value right now (a world that
     * isn't loaded, say); the saved bytes are then kept as they are, so nothing is lost.
     */
    T decode(BinaryReader in);
}
