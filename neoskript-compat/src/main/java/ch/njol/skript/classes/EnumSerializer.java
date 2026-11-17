/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.classes;

import ch.njol.yggdrasil.Fields;

/** Saves an enum's values by name. */
public class EnumSerializer<T extends Enum<T>> extends Serializer<T> {

    private final Class<T> c;

    public EnumSerializer(Class<T> c) {
        this.c = c;
    }

    @Override
    @Deprecated
    public T deserialize(String s) {
        try {
            return Enum.valueOf(c, s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean mustSyncDeserialization() {
        return false;
    }

    @Override
    public boolean canBeInstantiated() {
        return false;
    }

    @Override
    public Fields serialize(T e) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't save Skript addons' types yet");
    }

    @Override
    public T deserialize(Fields fields) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't load Skript addons' types yet");
    }

    @Override
    public void deserialize(T o, Fields f) {
        throw new UnsupportedOperationException();
    }
}
