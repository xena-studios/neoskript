/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

/** The tags Skript's binary format puts before each field's value. */
final class Tags {

    static final int NULL = 0x00;
    static final int INT = 0x03;
    static final int LONG = 0x04;
    static final int FLOAT = 0x08;
    static final int DOUBLE = 0x09;
    static final int BOOLEAN = 0x0F;
    static final int STRING = 0x20;
    static final int ARRAY = 0x30;
    static final int OBJECT = 0x80;
    /** A short name that's a back-reference: followed by its index as an int. */
    static final int REFERENCE = 0xFF;

    private Tags() {}
}
