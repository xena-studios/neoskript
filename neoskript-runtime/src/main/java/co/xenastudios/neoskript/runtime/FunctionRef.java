/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Arrays;
import java.util.List;

/**
 * A function as a value, Skript's {@code function}: {@code function "heal" from {_script}}, which can be run.
 *
 * @param name the function's name
 * @param localTo the script it's local to, or {@code null} for a global or built-in function
 * @param plural for each parameter, whether it takes several values
 * @param body calls the function with a value (or values) for each parameter
 */
public record FunctionRef(String name, String localTo, List<Boolean> plural,
        java.util.function.Function<Object[], Object> body) {

    public FunctionRef {
        plural = List.copyOf(plural);
    }

    /**
     * Calls it with {@code values}, one to each parameter in order; a last parameter taking several values
     * takes the rest.
     */
    public Object call(Object[] values) {
        Object[] arguments = new Object[plural.size()];
        for (int i = 0; i < arguments.length && i < values.length; i++) {
            if (plural.get(i)) {
                arguments[i] = i == arguments.length - 1 ? Arrays.copyOfRange(values, i, values.length)
                        : new Object[] {values[i]};
            } else {
                arguments[i] = values[i];
            }
        }
        return KeyedValues.plain(body.apply(arguments));
    }

    /** Equal if it's the same function: the same name, local to the same script. */
    @Override
    public boolean equals(Object other) {
        return other instanceof FunctionRef ref && ref.name.equals(name) && java.util.Objects.equals(ref.localTo, localTo);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + java.util.Objects.hashCode(localTo);
    }

    /** {@code name}, or {@code name from script} for a local function, which parses back to it. */
    @Override
    public String toString() {
        return localTo == null ? name : name + " from " + localTo;
    }
}
