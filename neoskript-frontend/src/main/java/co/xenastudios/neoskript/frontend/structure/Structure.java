/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import java.util.Locale;

/**
 * A top-level part of a script: usually a section, or a single line such as {@code using <feature>}. The
 * kind only says which parser handles it next. Events are every section that isn't one of the fixed
 * structures, and the event registry decides whether they exist.
 */
public record Structure(Kind kind, Node node) {

    public enum Kind {
        OPTIONS,
        VARIABLES,
        ALIASES,
        FUNCTION,
        COMMAND,
        EVENT,
        /** {@code using <experimental feature>}, a single line. */
        USING
    }

    /** The structure's section. Every kind except {@link Kind#USING} is a section. */
    public Node.Section section() {
        if (node instanceof Node.Section section) {
            return section;
        }
        throw new IllegalStateException(kind + " structure on line " + node.line().number() + " has no section");
    }

    /** The kind of a single-line structure, or {@code null} if the line isn't one. */
    public static Kind simpleKindOf(Line line) {
        return line.text().toLowerCase(Locale.ROOT).startsWith("using ") ? Kind.USING : null;
    }

    public static Kind kindOf(Line header) {
        String key = header.key().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "options" -> Kind.OPTIONS;
            case "variables" -> Kind.VARIABLES;
            case "aliases" -> Kind.ALIASES;
            default -> {
                if (key.startsWith("function ") || key.startsWith("local function ")) {
                    yield Kind.FUNCTION;
                }
                yield key.startsWith("command ") ? Kind.COMMAND : Kind.EVENT;
            }
        };
    }
}
