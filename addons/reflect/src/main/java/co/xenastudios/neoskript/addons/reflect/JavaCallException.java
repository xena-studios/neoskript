/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

/** A Java call that couldn't be made, as skript-reflect's: the error {@code the last java error} gives for it. */
public final class JavaCallException extends RuntimeException {

    JavaCallException(String message) {
        super(message);
    }
}
