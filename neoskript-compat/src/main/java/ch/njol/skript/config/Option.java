/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.config;

/** One of Skript's config options: its key and value. */
public class Option<T> {

    public final String key;
    private final T defaultValue;
    private volatile T value;
    private boolean optional;

    public Option(String key, T defaultValue) {
        this.key = key.toLowerCase(java.util.Locale.ENGLISH);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final Option<T> optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    public final T value() {
        return value;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final boolean isOptional() {
        return optional;
    }

    protected void onValueChange() {}
}
