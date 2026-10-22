/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.api.storage.BinaryWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes Skript's binary variable format.
 *
 * <ul>
 *   <li>Short names (field names, types, enum constants) are a length byte and UTF-8, or {@code FF} and an
 *       int index if the same name was written before; the value's type counts as the first name.
 *   <li>Strings are a compact length (two bytes with the top bit set below 32768, else four) and UTF-8.
 *   <li>An object is {@code 0x80 | fieldCount}, then each field's name, a tag and the value.
 * </ul>
 */
public final class SkriptBinaryWriter implements BinaryWriter {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);
    private final List<String> names = new ArrayList<>();

    /** @param type the value's type, which counts as the first name written */
    public SkriptBinaryWriter(String type) {
        names.add(type);
    }

    public byte[] toByteArray() {
        return bytes.toByteArray();
    }

    @Override
    public void rawLong(long value) {
        io(() -> out.writeLong(value));
    }

    @Override
    public void rawDouble(double value) {
        io(() -> out.writeDouble(value));
    }

    @Override
    public void rawBoolean(boolean value) {
        io(() -> out.writeByte(value ? 1 : 0));
    }

    @Override
    public void rawString(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        io(() -> {
            if (utf8.length < 0x8000) {
                out.writeShort(0x8000 | utf8.length);
            } else {
                out.writeInt(utf8.length);
            }
            out.write(utf8);
        });
    }

    @Override
    public void rawName(String value) {
        int index = names.indexOf(value);
        io(() -> {
            if (index >= 0) {
                out.writeByte(Tags.REFERENCE);
                out.writeInt(index);
            } else {
                byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
                if (utf8.length >= Tags.REFERENCE) {
                    throw new IllegalArgumentException("Name too long: " + value);
                }
                out.writeByte(utf8.length);
                out.write(utf8);
                names.add(value);
            }
        });
    }

    @Override
    public void beginObject(int fields) {
        if (fields >= 0x80) {
            throw new IllegalArgumentException("Too many fields: " + fields);
        }
        io(() -> out.writeByte(0x80 | fields));
    }

    @Override
    public void intField(String name, int value) {
        field(name, Tags.INT);
        io(() -> out.writeInt(value));
    }

    @Override
    public void longField(String name, long value) {
        field(name, Tags.LONG);
        rawLong(value);
    }

    @Override
    public void floatField(String name, float value) {
        field(name, Tags.FLOAT);
        io(() -> out.writeFloat(value));
    }

    @Override
    public void doubleField(String name, double value) {
        field(name, Tags.DOUBLE);
        rawDouble(value);
    }

    @Override
    public void booleanField(String name, boolean value) {
        field(name, Tags.BOOLEAN);
        rawBoolean(value);
    }

    @Override
    public void stringField(String name, String value) {
        field(name, Tags.STRING);
        rawString(value);
    }

    @Override
    public void objectField(String name, String type) {
        field(name, Tags.OBJECT);
        rawName(type);
    }

    private void field(String name, int tag) {
        rawName(name);
        io(() -> out.writeByte(tag));
    }

    private interface Write {
        void run() throws IOException;
    }

    private static void io(Write write) {
        try {
            write.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
