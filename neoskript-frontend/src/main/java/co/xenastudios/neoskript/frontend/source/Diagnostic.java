/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.source;

/**
 * An error or warning at a line of a script. {@code line} is 1-based, {@code column} is 0-based and
 * {@code length} may be 0 when the problem covers the whole line.
 */
public record Diagnostic(Severity severity, String file, int line, int column, int length, String message) {

    @Override
    public String toString() {
        return severity.name().toLowerCase(java.util.Locale.ROOT) + " " + file + ":" + line + ":" + (column + 1)
                + ": " + message;
    }
}
