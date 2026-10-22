/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import java.util.List;

/** A line of a script, or a section header with the lines indented under it. */
public sealed interface Node {

    Line line();

    /** A line that doesn't open a section, e.g. {@code send "hi" to player} or {@code permission: x.y}. */
    record Simple(Line line) implements Node {}

    /** A line ending in {@code :} and the lines indented under it. {@code children} can be empty. */
    record Section(Line line, List<Node> children) implements Node {

        public Section {
            children = List.copyOf(children);
        }
    }
}
