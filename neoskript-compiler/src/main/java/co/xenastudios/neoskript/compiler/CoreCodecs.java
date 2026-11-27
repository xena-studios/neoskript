/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Timespan;

/** How the core types are saved, matching Skript's {@code variables.csv} byte for byte. */
final class CoreCodecs {

    private CoreCodecs() {}

    static void register(Library library) {
        library.storage("string", String.class, codec(BinaryWriter::rawString, BinaryReader::rawString));
        library.storage("long", Long.class, codec(BinaryWriter::rawLong, BinaryReader::rawLong));
        library.storage("double", Double.class, codec(BinaryWriter::rawDouble, BinaryReader::rawDouble));
        library.storage("boolean", Boolean.class, codec(BinaryWriter::rawBoolean, BinaryReader::rawBoolean));
        // Java's other numbers, as Skript saves them (its integer, short, byte and float types, written as the
        // primitive): what a Java call through the reflect addon gives keeps its type, as in skript-reflect.
        library.storage("integer", Integer.class, codec((out, value) -> out.rawBytes(
                java.nio.ByteBuffer.allocate(4).putInt(value).array()), in -> fixed(in, 4).getInt()));
        library.storage("short", Short.class, codec((out, value) -> out.rawBytes(
                java.nio.ByteBuffer.allocate(2).putShort(value).array()), in -> fixed(in, 2).getShort()));
        library.storage("byte", Byte.class, codec((out, value) -> out.rawBytes(new byte[] {value}),
                in -> fixed(in, 1).get()));
        library.storage("float", Float.class, codec((out, value) -> out.rawBytes(
                java.nio.ByteBuffer.allocate(4).putFloat(value).array()), in -> fixed(in, 4).getFloat()));
        library.storage("timespan", Timespan.class, new VariableCodec<>() {
            @Override
            public void encode(Timespan value, BinaryWriter out) {
                out.beginObject(1);
                out.longField("millis", value.millis());
            }

            @Override
            public Timespan decode(BinaryReader in) {
                in.beginObject();
                return new Timespan(in.longField("millis"));
            }
        });
        library.storage("date", co.xenastudios.neoskript.runtime.Date.class, new VariableCodec<>() {
            @Override
            public void encode(co.xenastudios.neoskript.runtime.Date value, BinaryWriter out) {
                out.beginObject(1);
                out.longField("timestamp", value.millis());
            }

            @Override
            public co.xenastudios.neoskript.runtime.Date decode(BinaryReader in) {
                in.beginObject();
                return new co.xenastudios.neoskript.runtime.Date(in.longField("timestamp"));
            }
        });
    }

    /** The value's bytes, which must be exactly {@code length}: anything else isn't one. */
    private static java.nio.ByteBuffer fixed(BinaryReader in, int length) {
        byte[] bytes = in.remainingBytes();
        if (bytes.length != length) {
            throw new IllegalArgumentException("expected " + length + " bytes, got " + bytes.length);
        }
        return java.nio.ByteBuffer.wrap(bytes);
    }

    private interface Write<T> {
        void write(BinaryWriter out, T value);
    }

    private interface Read<T> {
        T read(BinaryReader in);
    }

    private static <T> VariableCodec<T> codec(Write<T> write, Read<T> read) {
        return new VariableCodec<>() {
            @Override
            public void encode(T value, BinaryWriter out) {
                write.write(out, value);
            }

            @Override
            public T decode(BinaryReader in) {
                return read.read(in);
            }
        };
    }
}
