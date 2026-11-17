/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.storage;

/**
 * Writes a value in Skript's variable format. A value is either one raw primitive ({@link #rawLong} and
 * the like) or an object: {@link #beginObject} with its field count, then each field's name followed by
 * one tagged value ({@link #longField} and the like, or {@link #objectField} and a nested object).
 *
 * <p>Codecs are given one; they don't implement it.
 */
public interface BinaryWriter {

    /**
     * The whole value, a {@code long}.
     *
     * @param value the value
     */
    void rawLong(long value);

    /**
     * The whole value, a {@code double}.
     *
     * @param value the value
     */
    void rawDouble(double value);

    /**
     * The whole value, a {@code boolean}.
     *
     * @param value the value
     */
    void rawBoolean(boolean value);

    /**
     * The whole value, a string.
     *
     * @param value the value
     */
    void rawString(String value);

    /**
     * The whole value, a short name, such as an enum constant's.
     *
     * @param value the name
     */
    void rawName(String value);

    /**
     * Bytes written as they are: a whole value, already in Skript's format, as Skript saves it.
     *
     * @param bytes the bytes
     */
    void rawBytes(byte[] bytes);

    /**
     * Starts an object. Follow with exactly {@code fields} fields.
     *
     * @param fields how many fields it has
     */
    void beginObject(int fields);

    /**
     * A {@code byte} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void byteField(String name, byte value);

    /**
     * An {@code int} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void intField(String name, int value);

    /**
     * A {@code long} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void longField(String name, long value);

    /**
     * A {@code float} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void floatField(String name, float value);

    /**
     * A {@code double} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void doubleField(String name, double value);

    /**
     * A {@code boolean} field.
     *
     * @param name the field's name
     * @param value its value
     */
    void booleanField(String name, boolean value);

    /**
     * A string field.
     *
     * @param name the field's name
     * @param value its value
     */
    void stringField(String name, String value);

    /**
     * A field holding an object of {@code type}: follow with {@link #beginObject} and its fields.
     *
     * @param name the field's name
     * @param type the object's type, as Skript names it
     */
    void objectField(String name, String type);

    /**
     * A field holding nothing ({@code null}).
     *
     * @param name the field's name
     */
    void nullField(String name);

    /**
     * A field holding a boxed primitive, as Skript writes an {@code Integer} or {@code Float} held as an object
     * rather than a primitive.
     *
     * @param name the field's name
     * @param value an {@code Integer}, {@code Float} and the like
     */
    void boxedField(String name, Object value);

    /**
     * A field holding an array of {@code length} objects whose declared element type is
     * {@code componentType}: follow with {@link #arrayElement} and an object for each element.
     *
     * @param name the field's name
     * @param componentType the elements' declared type
     * @param length how many elements it has
     */
    void objectArrayField(String name, String componentType, int length);

    /**
     * An element of an object array, an object of {@code type}: follow with {@link #beginObject}.
     *
     * @param type the element's type
     */
    void arrayElement(String type);
}
