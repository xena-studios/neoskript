/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;

/**
 * Skript's {@code entity type}: a kind of entity as scripts name it. Most are one Minecraft entity type
 * ({@code pig}), which can be spawned; some are categories that match several ({@code projectile},
 * {@code monster}), which can't.
 *
 * @param type the one entity type it is, or {@code null} for a category
 * @param entityClass the class entities of the kind have
 */
public record EntityKind(String name, EntityType type, Class<? extends Entity> entityClass) {

    private static volatile Map<String, EntityKind> names;

    /** Whether {@code entity} is of this kind. */
    public boolean matches(Entity entity) {
        return type != null ? entity.getType() == type : entityClass.isInstance(entity);
    }

    /** The kind {@code text} names, with or without an article, singular or plural, or {@code null}. */
    public static EntityKind parse(String text) {
        String words = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceFirst("^(?:an?|the) ", "");
        return names().get(words);
    }

    /** The kind of {@code type}. */
    public static EntityKind of(EntityType type) {
        return new EntityKind(name(type), type, type.getEntityClass() == null ? Entity.class : type.getEntityClass());
    }

    private static Map<String, EntityKind> names() {
        Map<String, EntityKind> known = names;
        if (known == null) {
            known = new HashMap<>();
            for (EntityType type : EntityType.values()) {
                if (type != EntityType.UNKNOWN) {
                    add(known, of(type), name(type));
                }
            }
            // Skript's names for some types, and the categories it knows.
            add(known, of(EntityType.FIREWORK_ROCKET), "firework");
            add(known, of(EntityType.LIGHTNING_BOLT), "lightning");
            add(known, of(EntityType.ITEM), "dropped item");
            add(known, of(EntityType.EXPERIENCE_ORB), "xp orb", "experience orb");
            add(known, new EntityKind("entity", null, Entity.class), "entity");
            add(known, new EntityKind("living entity", null, LivingEntity.class), "living entity");
            add(known, new EntityKind("mob", null, Mob.class), "mob");
            add(known, new EntityKind("monster", null, Enemy.class), "monster");
            add(known, new EntityKind("creature", null, Creature.class), "creature");
            add(known, new EntityKind("animal", null, Animals.class), "animal");
            add(known, new EntityKind("projectile", null, Projectile.class), "projectile");
            names = known;
        }
        return known;
    }

    private static void add(Map<String, EntityKind> known, EntityKind kind, String... aliases) {
        for (String alias : aliases) {
            known.putIfAbsent(alias, kind);
            known.putIfAbsent(alias.endsWith("s") ? alias : alias + "s", kind);
        }
    }

    static String name(EntityType type) {
        return type.getKey().getKey().replace('_', ' ');
    }

    @Override
    public String toString() {
        return name;
    }
}
