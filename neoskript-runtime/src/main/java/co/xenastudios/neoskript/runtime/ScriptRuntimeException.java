/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/** Something went wrong while a script ran, at a line of that script. */
public final class ScriptRuntimeException extends RuntimeException {

    private final String script;
    private final int line;

    public ScriptRuntimeException(String script, int line, Throwable cause) {
        super(script + ", line " + line + ": " + cause, cause);
        this.script = script;
        this.line = line;
    }

    public String script() {
        return script;
    }

    public int line() {
        return line;
    }
}
