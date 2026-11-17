/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.entry;

import ch.njol.skript.config.Node;

/** An entry written {@code key: value}. NeoSkriptCompat doesn't read sections' entries yet. */
public abstract class KeyValueEntryData<T> extends EntryData<T> {

    public KeyValueEntryData(String key, T defaultValue, boolean optional) {
        super(key, defaultValue, optional);
    }

    public KeyValueEntryData(String key, T defaultValue, boolean optional, boolean multiple) {
        super(key, defaultValue, optional, multiple);
    }

    @Override
    public T getValue(Node node) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't read sections' entries yet");
    }

    protected abstract T getValue(String value);

    public String getSeparator() {
        return ": ";
    }

    @Override
    public boolean canCreateWith(Node node) {
        return false;
    }
}
