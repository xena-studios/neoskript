/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.Set;

/**
 * A type that values and expressions can have.
 *
 * @param id the code name, e.g. {@code itemtype}
 * @param name the display name, e.g. {@code item type}. Patterns may use either, singular or plural.
 * @param literals lower-case literal values when the type is a closed set, e.g. game modes. Empty otherwise.
 * @param openLiterals whether any word-like text might be a literal of this type, e.g. item names, which
 *     depend on aliases the frontend doesn't have
 */
public record TypeInfo(String id, String name, Set<String> literals, boolean openLiterals) {

    public TypeInfo {
        literals = Set.copyOf(literals);
    }

    /** Types are identified by id; comparing literal sets would make hashing slow. */
    @Override
    public boolean equals(Object other) {
        return other instanceof TypeInfo type && type.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
