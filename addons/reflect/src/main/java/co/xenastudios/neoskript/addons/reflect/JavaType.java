/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

/**
 * A Java class as scripts have it, skript-reflect's {@code javatype}: what an import gives ({@code System}), and
 * what {@code the class "..."} and {@code player's class} give. A call on one is a static call.
 *
 * @param javaClass the class
 */
public record JavaType(Class<?> javaClass) {

    @Override
    public String toString() {
        return javaClass.getName();
    }
}
