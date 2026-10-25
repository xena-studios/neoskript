/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.storage;

/** Reads a value written by a {@link BinaryWriter}. Fields are read in the order they were written. */
public interface BinaryReader {

    long rawLong();

    double rawDouble();

    boolean rawBoolean();

    String rawString();

    String rawName();

    /** Starts an object; returns its field count. */
    int beginObject();

    byte byteField(String name);

    int intField(String name);

    long longField(String name);

    float floatField(String name);

    double doubleField(String name);

    boolean booleanField(String name);

    String stringField(String name);

    /** Reads a field holding an object; returns its type. Follow with {@link #beginObject}. */
    String objectField(String name);

    /**
     * Reads a field holding an object or nothing: returns the object's type (follow with
     * {@link #beginObject}), or {@code null} if the field holds nothing.
     */
    String objectOrNullField(String name);

    /** The type of an object a field holds, from {@link #anyField}: follow with {@link #beginObject}. */
    record ObjectType(String type) {}

    /**
     * Reads a field holding anything but an array: a boxed primitive (as {@code Integer} or {@code Float}), a
     * string, {@code null} if it holds nothing, or an {@link ObjectType} for an object.
     */
    Object anyField(String name);

    /** Reads a field holding an array of objects of {@code componentType}; returns its length. */
    int objectArrayField(String name, String componentType);

    /** Reads an element of an object array; returns its type. Follow with {@link #beginObject}. */
    String arrayElement();
}
