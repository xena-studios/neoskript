/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

/**
 * A built-in function's parameter.
 *
 * @param name the parameter's name
 * @param type the id of the parameter's type
 * @param plural whether it takes several values
 * @param optional whether it can be left out
 * @param defaultValue what it is when left out (not when given as nothing), or {@code null} for nothing
 */
public record Parameter(String name, String type, boolean plural, boolean optional,
        java.util.function.Supplier<?> defaultValue) {

    /**
     * A parameter with nothing as its default.
     *
     * @param name the parameter's name
     * @param type the id of the parameter's type
     * @param plural whether it takes several values
     * @param optional whether it can be left out
     */
    public Parameter(String name, String type, boolean plural, boolean optional) {
        this(name, type, plural, optional, null);
    }

    /**
     * A parameter that takes one value and can't be left out.
     *
     * @param name the parameter's name
     * @param type the id of the parameter's type
     * @return the parameter
     */
    public static Parameter of(String name, String type) {
        return new Parameter(name, type, false, false);
    }

    /**
     * An optional parameter that is {@code value} when left out: {@code location(1, 2, 3)} is in the main world.
     *
     * @param name the parameter's name
     * @param type the id of the parameter's type
     * @param value what it is when left out, asked each time
     * @return the parameter
     */
    public static Parameter defaulting(String name, String type, java.util.function.Supplier<?> value) {
        return new Parameter(name, type, false, true, value);
    }
}
