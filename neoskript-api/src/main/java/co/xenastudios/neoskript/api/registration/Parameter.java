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
 */
public record Parameter(String name, String type, boolean plural, boolean optional) {

    public static Parameter of(String name, String type) {
        return new Parameter(name, type, false, false);
    }
}
