/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.bukkitutil;

import ch.njol.skript.entity.EntityData;
import co.xenastudios.neoskript.paper.EntityKind;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/** Skript's entity helpers that addons use. */
public class EntityUtils {

    public EntityUtils() {}

    public static boolean isAgeable(Entity entity) {
        return entity instanceof Ageable;
    }

    public static int getAge(Entity entity) {
        return entity instanceof Ageable ageable ? ageable.getAge() : 0;
    }

    public static void setAge(Entity entity, int age) {
        if (entity instanceof Ageable ageable) {
            ageable.setAge(age);
        }
    }

    public static void setBaby(Entity entity) {
        if (entity instanceof Ageable ageable) {
            ageable.setBaby();
        }
    }

    public static void setAdult(Entity entity) {
        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
    }

    public static boolean isAdult(Entity entity) {
        return entity instanceof Ageable ageable && ageable.isAdult();
    }

    public static EntityType toBukkitEntityType(EntityData<?> data) {
        return data == null ? null : data.neoskript().type();
    }

    public static EntityType toBukkitEntityType(Class<?> entityClass) {
        for (EntityType type : EntityType.values()) {
            if (type.getEntityClass() == entityClass) {
                return type;
            }
        }
        return null;
    }

    public static EntityData<?> toSkriptEntityData(EntityType type) {
        return type == null || type.getEntityClass() == null ? null : EntityData.of(EntityKind.of(type));
    }
}
