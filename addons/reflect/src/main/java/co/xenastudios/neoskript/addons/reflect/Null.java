/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

/**
 * Java's {@code null}, as skript-reflect's {@code null}: a value scripts can pass to a method, unlike Skript's
 * {@code <none>}, which is no value at all.
 */
public final class Null {

    /** The only one. */
    public static final Null NULL = new Null();

    private Null() {}

    @Override
    public String toString() {
        return "null";
    }
}
