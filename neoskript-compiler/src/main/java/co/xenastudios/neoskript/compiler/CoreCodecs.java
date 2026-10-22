/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
