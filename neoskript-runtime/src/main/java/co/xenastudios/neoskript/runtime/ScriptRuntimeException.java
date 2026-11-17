/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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

    /**
     * What compiled code throws for {@code thrown}, caught at {@code line} of {@code script}: an error that already
     * says where it happened, from a function called here, passes through as it is. One without a line yet (the stack
     * ran out as a function started, before its first line) is placed here instead.
     */
    public static ScriptRuntimeException at(Throwable thrown, String script, int line) {
        if (thrown instanceof ScriptRuntimeException e) {
            return e.line != 0 || line == 0 ? e : new ScriptRuntimeException(script, line, e.getCause());
        }
        return new ScriptRuntimeException(script, line, thrown);
    }

    public String script() {
        return script;
    }

    public int line() {
        return line;
    }
}
