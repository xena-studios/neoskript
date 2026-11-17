/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

/**
 * Reads and writes a setting type the config doesn't know natively.
 *
 * @param <T> the setting's type
 */
public interface Codec<T> {

    /**
     * Turns a raw YAML value (a String, Number or Boolean) into a setting.
     *
     * @throws IllegalArgumentException with a reason a server owner can act on, such as
     *     {@code "isn't a duration, such as 5 minutes"}, if the value can't be used
     */
    T decode(Object raw);

    /** Turns a setting back into a value YAML can hold. */
    Object encode(T value);
}
