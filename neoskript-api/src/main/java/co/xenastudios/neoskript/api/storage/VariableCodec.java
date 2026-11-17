/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.storage;

/**
 * How values of a type are saved in variables files, in the binary format Skript uses, so Skript and
 * NeoSkript can read each other's files. Register one with
 * {@link co.xenastudios.neoskript.api.registration.Registrar#storage}. Saving runs off the server's threads, so
 * a codec mustn't touch the world.
 *
 * @param <T> the values' class
 */
public interface VariableCodec<T> {

    /**
     * Writes a value. Throws {@link UnsupportedOperationException} for a value that can't be saved, as Skript
     * can't save it either: it's then left out of the file.
     *
     * @param value the value
     * @param out where it's written
     */
    void encode(T value, BinaryWriter out);

    /**
     * Reads a value back. Returns {@code null} if it can't be turned into a value right now (a world that
     * isn't loaded, say); the saved bytes are then kept as they are, so nothing is lost.
     *
     * @param in what was written
     * @return the value, or {@code null}
     */
    T decode(BinaryReader in);
}
