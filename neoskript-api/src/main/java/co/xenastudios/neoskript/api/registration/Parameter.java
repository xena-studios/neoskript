/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

/**
 * A built-in function's parameter.
 *
 * @param type the id of the parameter's type
 * @param plural whether it takes several values
 * @param optional whether it can be left out
 * @param defaultValue what it is when left out (not when given as nothing), or {@code null} for nothing
 */
public record Parameter(String name, String type, boolean plural, boolean optional,
        java.util.function.Supplier<?> defaultValue) {

    public Parameter(String name, String type, boolean plural, boolean optional) {
        this(name, type, plural, optional, null);
    }

    public static Parameter of(String name, String type) {
        return new Parameter(name, type, false, false);
    }

    /** An optional parameter that is {@code value} when left out: {@code location(1, 2, 3)} is in the main world. */
    public static Parameter defaulting(String name, String type, java.util.function.Supplier<?> value) {
        return new Parameter(name, type, false, true, value);
    }
}
