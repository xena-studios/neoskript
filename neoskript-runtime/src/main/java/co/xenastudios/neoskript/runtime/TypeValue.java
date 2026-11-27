/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

/**
 * A type written as a value, as in {@code black (color) is a color}: a value is it if it's of that type.
 *
 * @param name the type's name, as scripts write it
 * @param type the class values of the type are
 */
public record TypeValue(String name, Class<?> type) {

    static {
        // As Skript's comparator for a type: a value of it is it, and so is the type or one under it ('integer is
        // number', not 'number is integer').
        Comparison.registerEquality(TypeValue.class, TypeValue.class, (value, type) -> type.type().isAssignableFrom(
                value.type()));
        Comparison.registerEquality(TypeValue.class, Object.class, (type, value) -> type.type().isInstance(value));
    }

    @Override
    public String toString() {
        return name;
    }
}
