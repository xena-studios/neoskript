/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Skript's binary variable format (see {@link SkriptBinaryWriter}). Anything unexpected throws
 * {@link IllegalStateException}, and the caller keeps the value's bytes as they are.
 */
public final class SkriptBinaryReader implements BinaryReader {

    private final ByteBuffer in;
    private final List<String> names = new ArrayList<>();

    public SkriptBinaryReader(String type, byte[] bytes) {
        this.in = ByteBuffer.wrap(bytes);
        remember(type);
    }

    /** Whether every byte was read: a codec that reads less didn't understand the value. */
    public boolean atEnd() {
        return !in.hasRemaining();
    }

    @Override
    public long rawLong() {
        return in.getLong();
    }

    @Override
    public double rawDouble() {
        return in.getDouble();
    }

    @Override
    public boolean rawBoolean() {
        return in.get() != 0;
    }

    @Override
    public String rawString() {
        byte[] utf8 = new byte[length()];
        in.get(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }

    @Override
    public String rawName() {
        int length = in.get() & 0xFF;
        if (length == Tags.REFERENCE) {
            int index = in.getInt();
            if (index < 0 || index >= names.size()) {
                throw new IllegalStateException("Bad name reference " + index);
            }
            return names.get(index);
        }
        byte[] utf8 = new byte[length];
        in.get(utf8);
        String name = new String(utf8, StandardCharsets.UTF_8);
        remember(name);
        return name;
    }

    /** Skript only numbers names longer than 4 bytes, for back-references: shorter ones are always written out. */
    private void remember(String name) {
        if (name.getBytes(StandardCharsets.UTF_8).length > 4) {
            names.add(name);
        }
    }

    @Override
    public int beginObject() {
        int header = in.get() & 0xFF;
        if ((header & 0x80) == 0) {
            throw new IllegalStateException("Expected an object");
        }
        return header & 0x7F;
    }

    @Override
    public byte byteField(String name) {
        field(name, Tags.BYTE);
        return in.get();
    }

    @Override
    public int intField(String name) {
        field(name, Tags.INT);
        return in.getInt();
    }

    @Override
    public long longField(String name) {
        field(name, Tags.LONG);
        return in.getLong();
    }

    @Override
    public float floatField(String name) {
        field(name, Tags.FLOAT);
        return in.getFloat();
    }

    @Override
    public double doubleField(String name) {
        field(name, Tags.DOUBLE);
        return in.getDouble();
    }

    @Override
    public boolean booleanField(String name) {
        field(name, Tags.BOOLEAN);
        return rawBoolean();
    }

    @Override
    public String stringField(String name) {
        field(name, Tags.STRING);
        return rawString();
    }

    @Override
    public String objectField(String name) {
        field(name, Tags.OBJECT);
        return rawName();
    }

    @Override
    public String objectOrNullField(String name) {
        String actual = rawName();
        int tag = in.get() & 0xFF;
        if (!actual.equals(name) || (tag != Tags.OBJECT && tag != Tags.NULL)) {
            throw new IllegalStateException("Expected field " + name + ", found " + actual + " (" + tag + ")");
        }
        return tag == Tags.NULL ? null : rawName();
    }

    @Override
    public Object anyField(String name) {
        String actual = rawName();
        int tag = in.get() & 0xFF;
        if (!actual.equals(name)) {
            throw new IllegalStateException("Expected field " + name + ", found " + actual + " (" + tag + ")");
        }
        return switch (tag) {
            case Tags.NULL -> null;
            case Tags.STRING -> rawString();
            case Tags.OBJECT -> new ObjectType(rawName());
            case Tags.BOXED + Tags.BYTE -> in.get();
            case Tags.BOXED + Tags.SHORT -> in.getShort();
            case Tags.BOXED + Tags.INT -> in.getInt();
            case Tags.BOXED + Tags.LONG -> in.getLong();
            case Tags.BOXED + Tags.FLOAT -> in.getFloat();
            case Tags.BOXED + Tags.DOUBLE -> in.getDouble();
            case Tags.BOXED + Tags.CHAR -> in.getChar();
            case Tags.BOXED + Tags.BOOLEAN -> in.get() != 0;
            default -> throw new IllegalStateException("Unexpected tag " + tag + " in field " + name);
        };
    }

    @Override
    public int objectArrayField(String name, String componentType) {
        field(name, Tags.ARRAY);
        if ((in.get() & 0xFF) != Tags.OBJECT || !rawName().equals(componentType)) {
            throw new IllegalStateException("Expected an array of " + componentType);
        }
        return length();
    }

    @Override
    public String arrayElement() {
        if ((in.get() & 0xFF) != Tags.OBJECT) {
            throw new IllegalStateException("Expected an object in the array");
        }
        return rawName();
    }

    private int length() {
        int first = in.get(in.position()) & 0xFF;
        return (first & 0x80) != 0 ? in.getShort() & 0x7FFF : in.getInt();
    }

    private void field(String name, int tag) {
        String actual = rawName();
        int actualTag = in.get() & 0xFF;
        if (!actual.equals(name) || actualTag != tag) {
            throw new IllegalStateException("Expected field " + name + " (" + tag + "), found " + actual + " (" + actualTag + ")");
        }
    }
}
