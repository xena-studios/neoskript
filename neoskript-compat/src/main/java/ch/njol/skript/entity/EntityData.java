/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.entity;

import co.xenastudios.neoskript.paper.EntityKind;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Skript's entity type, for addons: it holds NeoSkript's, which is what scripts get. Addons can't make their own
 * kinds of entity data on NeoSkript yet.
 */
public abstract class EntityData<E extends Entity> {

    protected EntityData() {}

    /** NeoSkript's entity type as Skript's: not one of Skript's methods. */
    public static EntityData<?> of(EntityKind kind) {
        return kind == null ? null : new Kind<>(kind);
    }

    /** The NeoSkript entity type this holds: not one of Skript's methods. */
    public abstract EntityKind neoskript();

    public static EntityData<?> fromEntity(Entity entity) {
        return entity == null ? null : of(EntityKind.of(entity.getType()));
    }

    @SuppressWarnings("unchecked")
    public static <E extends Entity> EntityData<? super E> fromClass(Class<E> entityClass) {
        for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
            if (type.getEntityClass() == entityClass) {
                return (EntityData<? super E>) of(EntityKind.of(type));
            }
        }
        return null;
    }

    public static String toString(Entity entity) {
        EntityData<?> data = fromEntity(entity);
        return data == null ? String.valueOf(entity) : data.toString();
    }

    public static String toString(Entity entity, int flags) {
        return toString(entity);
    }

    public final boolean isInstance(Entity entity) {
        return entity != null && neoskript().matches(entity);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends E> getType() {
        return (Class<? extends E>) neoskript().entityClass();
    }

    public boolean isSupertypeOf(EntityData<?> other) {
        return other != null && neoskript().includes(other.neoskript());
    }

    @SuppressWarnings("unchecked")
    public E spawn(Location location) {
        return (E) neoskript().spawn(location);
    }

    public E spawn(Location location, Consumer<E> consumer) {
        E entity = spawn(location);
        if (entity != null && consumer != null) {
            consumer.accept(entity);
        }
        return entity;
    }

    @Override
    public String toString() {
        return neoskript().toString();
    }

    public String toString(int flags) {
        return toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityData<?> data && neoskript().equals(data.neoskript());
    }

    @Override
    public int hashCode() {
        return neoskript().hashCode();
    }

    /** One of NeoSkript's entity types. */
    private static final class Kind<E extends Entity> extends EntityData<E> {

        private final EntityKind kind;

        Kind(EntityKind kind) {
            this.kind = kind;
        }

        @Override
        public EntityKind neoskript() {
            return kind;
        }
    }
}
