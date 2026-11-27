/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.entry;

import ch.njol.skript.config.Node;

/** An entry a section or structure may have: its key, default, and whether it's optional. */
public abstract class EntryData<T> {

    private final String key;
    private final T defaultValue;
    private final boolean optional;
    private final boolean multiple;

    public EntryData(String key, T defaultValue, boolean optional) {
        this(key, defaultValue, optional, false);
    }

    public EntryData(String key, T defaultValue, boolean optional, boolean multiple) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.optional = optional;
        this.multiple = multiple;
    }

    public String getKey() {
        return key;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean supportsMultiple() {
        return multiple;
    }

    public abstract T getValue(Node node);

    public abstract boolean canCreateWith(Node node);
}
