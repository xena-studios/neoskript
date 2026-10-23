/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * Thrown by an {@link ElementFactory} when its syntax matched but can't be used as written, like the midpoint
 * between a location and a vector. The message is the error the script's author sees, as Skript words it.
 */
public final class SyntaxError extends RuntimeException {

    public SyntaxError(String message) {
        super(message, null, false, false);
    }
}
