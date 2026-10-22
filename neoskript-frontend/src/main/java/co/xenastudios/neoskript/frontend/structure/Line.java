/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

/**
 * One line of code, with its comment and surrounding whitespace removed.
 *
 * @param number the 1-based line number in the file
 * @param indent the leading whitespace, exactly as written
 * @param text the code, never blank
 */
public record Line(int number, String indent, String text) {

    /** The 0-based column where {@link #text()} starts. */
    public int column() {
        return indent.length();
    }

    /** Whether the line opens a section, i.e. ends with {@code :}. */
    public boolean opensSection() {
        return text.endsWith(":");
    }

    /** The text without the trailing {@code :} of a section header. */
    public String key() {
        return opensSection() ? text.substring(0, text.length() - 1).stripTrailing() : text;
    }
}
