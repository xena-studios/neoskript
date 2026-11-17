/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.source;

/**
 * An error or warning at a line of a script. {@code line} is 1-based, {@code column} is 0-based and
 * {@code length} may be 0 when the problem covers the whole line.
 */
public record Diagnostic(Severity severity, String file, int line, int column, int length, String message) {

    /**
     * Whether an error is of a lesser quality in Skript's parse log ({@code ErrorQuality.NOT_AN_EXPRESSION}): words
     * nothing understands, as Skript's change effect says it. Where only one error is kept, a later one of the usual
     * quality wins over it.
     */
    public static boolean lesserQuality(String message) {
        return message != null && message.startsWith("Can't understand this expression: '");
    }

    @Override
    public String toString() {
        return severity.name().toLowerCase(java.util.Locale.ROOT) + " " + file + ":" + line + ":" + (column + 1)
                + ": " + message;
    }
}
