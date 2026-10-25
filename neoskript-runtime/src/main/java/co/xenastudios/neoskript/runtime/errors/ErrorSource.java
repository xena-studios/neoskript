/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

/**
 * Where a runtime error came from, as Skript's {@code ErrorSource}: the kind of syntax ({@code effect},
 * {@code expression}...), its name (Skript's documentation name), the line and its code, and the script.
 */
public record ErrorSource(String syntaxType, String syntaxName, int lineNumber, String lineText, String script) {

    /** Skript's source for an element it can't place: no line, no script. */
    public static ErrorSource unknown(String syntaxType, String syntaxName) {
        return new ErrorSource(syntaxType, syntaxName, 0, "-unknown-", "-unknown-");
    }

    /** The script and line, which errors are counted by. */
    public Location location() {
        return new Location(script, lineNumber);
    }

    /** A line of a script. */
    public record Location(String script, int lineNumber) {}
}
