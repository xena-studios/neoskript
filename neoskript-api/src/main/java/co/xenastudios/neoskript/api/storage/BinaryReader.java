/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.storage;

/**
 * Reads a value written by a {@link BinaryWriter}. Fields are read in the order they were written, by the names
 * they were written with. Reading what isn't there throws {@link IllegalStateException}, and the saved bytes are
 * then kept as they are.
 *
 * <p>Codecs are given one; they don't implement it.
 */
public interface BinaryReader {

    /**
     * Reads the whole value, a {@code long}.
     *
     * @return the value
     */
    long rawLong();

    /**
     * Reads the whole value, a {@code double}.
     *
     * @return the value
     */
    double rawDouble();

    /**
     * Reads the whole value, a {@code boolean}.
     *
     * @return the value
     */
    boolean rawBoolean();

    /**
     * Reads the whole value, a string.
     *
     * @return the value
     */
    String rawString();

    /**
     * Reads the whole value, a short name.
     *
     * @return the name
     */
    String rawName();

    /**
     * The rest of the value's bytes, as they are: for a value matched whole against how Skript saves it.
     *
     * @return the bytes
     */
    byte[] remainingBytes();

    /**
     * Starts an object.
     *
     * @return its field count
     */
    int beginObject();

    /**
     * Reads a {@code byte} field.
     *
     * @param name the field's name
     * @return its value
     */
    byte byteField(String name);

    /**
     * Reads an {@code int} field.
     *
     * @param name the field's name
     * @return its value
     */
    int intField(String name);

    /**
     * Reads a {@code long} field.
     *
     * @param name the field's name
     * @return its value
     */
    long longField(String name);

    /**
     * Reads a {@code float} field.
     *
     * @param name the field's name
     * @return its value
     */
    float floatField(String name);

    /**
     * Reads a {@code double} field.
     *
     * @param name the field's name
     * @return its value
     */
    double doubleField(String name);

    /**
     * Reads a {@code boolean} field.
     *
     * @param name the field's name
     * @return its value
     */
    boolean booleanField(String name);

    /**
     * Reads a string field.
     *
     * @param name the field's name
     * @return its value
     */
    String stringField(String name);

    /**
     * Reads a field holding an object. Follow with {@link #beginObject}.
     *
     * @param name the field's name
     * @return the object's type
     */
    String objectField(String name);

    /**
     * Reads a field holding an object or nothing. Follow an object with {@link #beginObject}.
     *
     * @param name the field's name
     * @return the object's type, or {@code null} if the field holds nothing
     */
    String objectOrNullField(String name);

    /**
     * The type of an object a field holds, from {@link #anyField}: follow with {@link #beginObject}.
     *
     * @param type the object's type
     */
    record ObjectType(String type) {}

    /**
     * Reads a field holding anything but an array.
     *
     * @param name the field's name
     * @return a boxed primitive (as {@code Integer} or {@code Float}), a string, {@code null} if it holds nothing,
     *     or an {@link ObjectType} for an object
     */
    Object anyField(String name);

    /**
     * Reads a field holding an array of objects of {@code componentType}.
     *
     * @param name the field's name
     * @param componentType the elements' declared type
     * @return its length
     */
    int objectArrayField(String name, String componentType);

    /**
     * Reads an element of an object array. Follow with {@link #beginObject}.
     *
     * @return the element's type
     */
    String arrayElement();
}
