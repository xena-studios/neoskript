/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.storage;

/**
 * Writes a value in Skript's variable format. A value is either one raw primitive ({@link #rawLong} and
 * the like) or an object: {@link #beginObject} with its field count, then each field's name followed by
 * one tagged value ({@link #longField} and the like, or {@link #objectField} and a nested object).
 */
public interface BinaryWriter {

    void rawLong(long value);

    void rawDouble(double value);

    void rawBoolean(boolean value);

    void rawString(String value);

    /** A short name, such as an enum constant's. */
    void rawName(String value);

    void beginObject(int fields);

    void intField(String name, int value);

    void longField(String name, long value);

    void floatField(String name, float value);

    void doubleField(String name, double value);

    void booleanField(String name, boolean value);

    void stringField(String name, String value);

    /** A field holding an object of {@code type}: follow with {@link #beginObject} and its fields. */
    void objectField(String name, String type);

    /** A field holding nothing ({@code null}). */
    void nullField(String name);

    /**
     * A field holding an array of {@code length} objects whose declared element type is
     * {@code componentType}: follow with {@link #arrayElement} and an object for each element.
     */
    void objectArrayField(String name, String componentType, int length);

    /** An element of an object array, an object of {@code type}: follow with {@link #beginObject}. */
    void arrayElement(String type);
}
