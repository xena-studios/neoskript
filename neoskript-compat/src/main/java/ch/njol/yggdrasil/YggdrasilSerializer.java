/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.yggdrasil;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;

/** The base of Skript's serializers. */
public abstract class YggdrasilSerializer<T> implements ClassResolver {

    public YggdrasilSerializer() {}

    @Override
    public abstract Class<? extends T> getClass(String id);

    public abstract Fields serialize(T o) throws NotSerializableException;

    public boolean canBeInstantiated(Class<? extends T> c) {
        return true;
    }

    public abstract <E extends T> E newInstance(Class<E> c);

    public abstract void deserialize(T o, Fields fields) throws StreamCorruptedException, NotSerializableException;

    public <E extends T> E deserialize(Class<E> c, Fields fields) throws StreamCorruptedException, NotSerializableException {
        E instance = newInstance(c);
        if (instance == null) {
            throw new StreamCorruptedException();
        }
        deserialize(instance, fields);
        return instance;
    }
}
