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

    int intField(String name);

    long longField(String name);

    float floatField(String name);

    double doubleField(String name);

    boolean booleanField(String name);

    String stringField(String name);

    /** Reads a field holding an object; returns its type. Follow with {@link #beginObject}. */
    String objectField(String name);
}
