/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

/** What {@code Skript.exception} returns, for an addon to throw: the error is logged already. */
public class EmptyStacktraceException extends RuntimeException {

    public EmptyStacktraceException() {
        super(null, null, false, false);
    }
}
