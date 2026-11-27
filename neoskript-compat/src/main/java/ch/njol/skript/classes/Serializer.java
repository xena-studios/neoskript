/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.classes;

import ch.njol.yggdrasil.Fields;
import ch.njol.yggdrasil.YggdrasilSerializer;
import java.io.NotSerializableException;
import java.io.StreamCorruptedException;

/** How a type's values are saved in variables. NeoSkriptCompat records it; saving addon types comes later. */
public abstract class Serializer<T> extends YggdrasilSerializer<T> {

    protected ClassInfo<? extends T> info = null;

    public Serializer() {}

    @Override
    public Class<? extends T> getClass(final String id) {
        ClassInfo<? extends T> info = this.info;
        return info != null && id.equals(info.getCodeName()) ? info.getC() : null;
    }

    @Override
    public String getID(final Class<?> c) {
        ClassInfo<? extends T> info = this.info;
        return info != null && info.getC().isAssignableFrom(c) ? info.getCodeName() : null;
    }

    @Override
    public <E extends T> E newInstance(final Class<E> c) {
        try {
            java.lang.reflect.Constructor<E> constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    public abstract Fields serialize(T o) throws NotSerializableException;

    @Override
    public void deserialize(T o, Fields f) throws StreamCorruptedException, NotSerializableException {
        throw new StreamCorruptedException();
    }

    public abstract boolean mustSyncDeserialization();

    @Override
    public boolean canBeInstantiated(final Class<? extends T> c) {
        return canBeInstantiated();
    }

    protected abstract boolean canBeInstantiated();

    @Override
    public <E extends T> E deserialize(final Class<E> c, final Fields fields)
            throws StreamCorruptedException, NotSerializableException {
        if (!canBeInstantiated()) {
            @SuppressWarnings("unchecked")
            E value = (E) deserialize(fields);
            return value;
        }
        return super.deserialize(c, fields);
    }

    protected T deserialize(final Fields fields) throws StreamCorruptedException, NotSerializableException {
        throw new StreamCorruptedException();
    }

    @Deprecated
    public T deserialize(final String s) {
        return null;
    }
}
