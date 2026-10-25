/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

/**
 * One runtime error or warning, as Skript's {@code RuntimeError}.
 *
 * @param toHighlight a regular expression for the part of the line to highlight (its first match), or null
 */
public record RuntimeError(Level level, ErrorSource source, String error, String toHighlight) {

    /** How bad it is: Skript's {@code SEVERE} or {@code WARNING}. */
    public enum Level {
        ERROR,
        WARNING
    }
}
