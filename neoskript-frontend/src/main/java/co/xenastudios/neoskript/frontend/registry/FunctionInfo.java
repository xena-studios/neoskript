/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.List;

/**
 * A function's signature: a built-in one, or one a script defines.
 *
 * @param returnType the return type's id, or {@code null} if it returns nothing
 * @param localTo for a {@code local function}, the script it belongs to; {@code null} for a global one
 */
public record FunctionInfo(String name, List<Parameter> parameters, String returnType, boolean returnsPlural,
        String localTo) {

    public FunctionInfo {
        parameters = List.copyOf(parameters);
    }

    /** A global function. */
    public FunctionInfo(String name, List<Parameter> parameters, String returnType, boolean returnsPlural) {
        this(name, parameters, returnType, returnsPlural, null);
    }

    /**
     * @param type the type's id
     * @param optional whether the parameter has a default value
     */
    public record Parameter(String name, String type, boolean plural, boolean optional) {}
}
