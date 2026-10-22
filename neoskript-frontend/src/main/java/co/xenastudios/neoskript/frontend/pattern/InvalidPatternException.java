/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

/** A syntax pattern that can't be compiled. This is a bug in whoever registered the pattern. */
public final class InvalidPatternException extends IllegalArgumentException {

    private final String pattern;
    private final int index;

    public InvalidPatternException(String pattern, int index, String message) {
        super(message + " at index " + index + " in pattern: " + pattern);
        this.pattern = pattern;
        this.index = index;
    }

    public String pattern() {
        return pattern;
    }

    public int index() {
        return index;
    }
}
