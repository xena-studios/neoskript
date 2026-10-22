/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import java.util.List;

/**
 * What a {@code %...%} placeholder accepts, e.g. {@code %-*number/timespan@-1%}.
 *
 * @param types the accepted type names as written, e.g. {@code number} or {@code players}. The type
 *     registry resolves the name and whether it's plural.
 * @param nullable {@code -}: the placeholder may be left out, with no default value filled in
 * @param literalOnly {@code *}: only a literal is accepted, not a variable or other expression
 * @param changeableOnly {@code ~}: only something that can be changed (set, added to) is accepted
 * @param time {@code @n}: the time state the expression is evaluated in. -1 is past, 1 is future, 0 is
 *     the default.
 */
public record PlaceholderSpec(List<String> types, boolean nullable, boolean literalOnly, boolean changeableOnly,
        int time) {

    public PlaceholderSpec {
        types = List.copyOf(types);
    }
}
