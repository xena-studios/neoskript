/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import java.util.List;

/**
 * A registered syntax element.
 *
 * @param returnType for expressions, the id of the type they return, or {@code object} if unknown
 * @param regexRoles how to check each {@code <regex>}'s text, by regex index. Missing entries use the
 *     last one, or {@link RegexRole#ANY} if there are none.
 */
public record SyntaxInfo(SyntaxKind kind, String id, List<CompiledPattern> patterns, String returnType,
        List<RegexRole> regexRoles) {

    public SyntaxInfo {
        patterns = List.copyOf(patterns);
        regexRoles = List.copyOf(regexRoles);
    }

    public RegexRole regexRole(int index) {
        if (regexRoles.isEmpty()) {
            return RegexRole.ANY;
        }
        return regexRoles.get(Math.min(index, regexRoles.size() - 1));
    }

    @Override
    public String toString() {
        return kind + " " + id;
    }
}
