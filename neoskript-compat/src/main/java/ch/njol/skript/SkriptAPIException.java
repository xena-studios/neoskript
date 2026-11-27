/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript;

/** An addon used Skript's API wrongly. */
public class SkriptAPIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SkriptAPIException(String message) {
        super(message);
    }

    public SkriptAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
