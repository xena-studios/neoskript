/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

/** Code the compiler can't compile, reported as an error at a script line. */
final class CompileException extends Exception {

    private final int line;

    /** Whether it's about words nothing understands, whose error depends on where they are. */
    private final boolean unknownWords;

    CompileException(int line, String message) {
        this(line, message, false);
    }

    CompileException(int line, String message, boolean unknownWords) {
        super(message);
        this.line = line;
        this.unknownWords = unknownWords;
    }

    boolean unknownWords() {
        return unknownWords;
    }

    int line() {
        return line;
    }

    /** Whether the problem was already reported by the parser, so it shouldn't be reported again. */
    boolean alreadyReported() {
        return getMessage() == null;
    }
}
