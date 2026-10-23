/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** More about entities (max health, AI, being leashed, charged, alive), dropping items, and a few values. */
final class EntitySyntax {

    /** The item dropped last on each thread, for {@code the last dropped item}. */
    private static final ThreadLocal<org.bukkit.entity.Item> LAST_DROPPED = new ThreadLocal<>();

    private EntitySyntax() {}

    static void register(Registrar registrar) {
        SettableProperty.register(registrar, "ExprMaxHealth", "number", "max[imum] health", "livingentities",
                LivingEntity.class, Number.class, living -> {
                    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                    return max == null ? null : max.getValue() / 2;
                }, (living, hearts) -> {
                    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        max.setBaseValue(Math.max(0.01, hearts.doubleValue() * 2));
                    }
                });
        SettableProperty.register(registrar, "ExprAI", "boolean", "(ai|artificial intelligence)", "livingentities",
                LivingEntity.class, Boolean.class, LivingEntity::hasAI, LivingEntity::setAI);

        condition(registrar, "CondLeashed", "entities", "leashed", Entity.class,
                entity -> entity instanceof LivingEntity living && living.isLeashed());
        condition(registrar, "CondIsCharged", "entities", "(charged|powered)", Entity.class,
                entity -> entity instanceof Creeper creeper ? creeper.isPowered()
                        : entity instanceof WitherSkull skull && skull.isCharged());
        condition(registrar, "CondIsAlive", "entities", "(alive|dead:dead)", Entity.class, entity -> !entity.isDead());
        condition(registrar, "CondIsInvulnerable", "entities", "(invulnerable|invincible)", Entity.class,
                Entity::isInvulnerable);

        condition(registrar, "CondPandaIsRolling", "livingentities", "rolling", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isRolling);
        condition(registrar, "CondPandaIsSneezing", "livingentities", "sneezing", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isSneezing);
        condition(registrar, "CondPandaIsOnBack", "livingentities", "on (its|their) back[s]", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isOnBack);
        condition(registrar, "CondPandaIsScared", "livingentities", "scared", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isScared);
        condition(registrar, "CondIsEating", "livingentities", "eating", LivingEntity.class,
                entity -> entity instanceof org.bukkit.entity.Panda panda ? panda.isEating()
                        : entity instanceof org.bukkit.entity.AbstractHorse horse && horse.isEating());
        condition(registrar, "CondIsValid", "entities", "valid", Entity.class, Entity::isValid);
        condition(registrar, "CondIsPersistent", "entities/blocks", "persistent", Object.class,
                value -> value instanceof Entity entity ? entity.isPersistent()
                        : value instanceof org.bukkit.block.Block block
                                && block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves && leaves.isPersistent());
        condition(registrar, "CondIsShivering", "livingentities", "shivering", org.bukkit.entity.Strider.class,
                org.bukkit.entity.Strider::isShivering);
        condition(registrar, "CondIsCommandBlockConditional", "blocks", "[un:un]conditional", org.bukkit.block.Block.class,
                block -> block.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock command
                        && command.isConditional());

        SettableProperty.register(registrar, "ExprSpawnerType", "entitytype", org.bukkit.block.Block.class, EntityKind.class,
                block -> block.getState() instanceof org.bukkit.block.CreatureSpawner spawner && spawner.getSpawnedType() != null
                        ? EntityKind.of(spawner.getSpawnedType()) : null,
                (block, kind) -> {
                    if (block.getState() instanceof org.bukkit.block.CreatureSpawner spawner && kind.type() != null) {
                        spawner.setSpawnedType(kind.type());
                        spawner.update();
                    }
                },
                "[the] (spawner|entity|creature) type[s] of %blocks%", "%blocks%'[s] (spawner|entity|creature) type[s]");

        registrar.expression("ExprLastDroppedItem", "entity", info -> (context, args) -> LAST_DROPPED.get(),
                "[the] [last[ly]] dropped item");

        registrar.condition("CondIsBetween", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                if (values.length == 0 || args[1] == null || args[2] == null) {
                    return false;
                }
                for (Object value : values) {
                    Integer first = co.xenastudios.neoskript.runtime.Comparison.order(value, args[1]);
                    Integer second = co.xenastudios.neoskript.runtime.Comparison.order(value, args[2]);
                    // Between, whichever bound is the lower one.
                    boolean between = first != null && second != null
                            && ((first >= 0 && second <= 0) || (first <= 0 && second >= 0));
                    if (between == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%objects% (is|are)[not:(n't| not)] between %object% and %object%");

        registrar.effect("EffDetonate", info -> (context, args) -> {
            for (Object entity : (Object[]) args[0]) {
                switch (entity) {
                    case Creeper creeper -> creeper.explode();
                    case org.bukkit.entity.TNTPrimed tnt -> tnt.setFuseTicks(0);
                    case org.bukkit.entity.Firework firework -> firework.detonate();
                    case org.bukkit.entity.minecart.ExplosiveMinecart cart -> cart.explode();
                    case org.bukkit.entity.WindCharge charge -> charge.explode();
                    default -> {}
                }
            }
        }, "detonate %entities%");
        registrar.effect("EffInvulnerability", info -> {
            boolean invulnerable = info.pattern() == 0;
            return (context, args) -> {
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof Entity e) {
                        e.setInvulnerable(invulnerable);
                    }
                }
            };
        }, "make %entities% (invulnerable|invincible)", "make %entities% (not (invulnerable|invincible)|vulnerable|vincible)");
        registrar.effect("EffMakeAdultOrBaby", info -> {
            boolean adult = info.hasTag("adult");
            return (context, args) -> {
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof org.bukkit.entity.Ageable ageable) {
                        if (adult) {
                            ageable.setAdult();
                        } else {
                            ageable.setBaby();
                        }
                    }
                }
            };
        }, "make %livingentities% [a[n]] (adult:adult|baby|child)", "force %livingentities% to be[come] a[n] (adult:adult|baby|child)");
        registrar.effect("EffScreaming", info -> {
            boolean scream = info.pattern() < 2;
            return (context, args) -> {
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof org.bukkit.entity.Goat goat) {
                        goat.setScreaming(scream);
                    } else if (entity instanceof org.bukkit.entity.Enderman enderman) {
                        enderman.setScreaming(scream);
                    }
                }
            };
        }, "make %livingentities% (start screaming|scream)", "force %livingentities% to (start screaming|scream)",
                "make %livingentities% stop screaming", "force %livingentities% to stop screaming");
        registrar.effect("EffEating", info -> {
            boolean start = info.hasTag("start");
            return (context, args) -> {
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof org.bukkit.entity.Panda panda) {
                        panda.setEating(start);
                    } else if (entity instanceof org.bukkit.entity.AbstractHorse horse) {
                        horse.setEating(start);
                    }
                }
            };
        }, "make %livingentities% (start:start|stop) eating", "force %livingentities% to (start:start|stop) eating");
        registrar.expression("ExprLeashHolder", "entity", info -> (context, args) -> {
            List<Object> holders = new ArrayList<>();
            for (Object entity : (Object[]) args[0]) {
                if (entity instanceof LivingEntity living && living.isLeashed()) {
                    holders.add(living.getLeashHolder());
                }
            }
            return holders.size() == 1 ? holders.getFirst() : holders.toArray();
        }, "[the] leash holder[s] of %entities%", "%entities%'[s] leash holder[s]");
        registrar.condition("CondIsCustomNameVisible", info -> {
            boolean negated = info.pattern() % 2 == 1;
            return (context, args) -> {
                Object[] entities = (Object[]) args[0];
                for (Object entity : entities) {
                    if (!(entity instanceof Entity e) || e.isCustomNameVisible() == negated) {
                        return false;
                    }
                }
                return entities.length > 0;
            };
        }, "%entities%'[s] custom name[s] (is|are) visible", "%entities%'[s] custom name[s] (isn't|is not|are not|aren't) visible",
                "custom name of %entities% (is|are) visible", "custom name of %entities% (isn't|is not|are not|aren't) visible");
        SettableProperty.register(registrar, "ExprExplosiveYield", "number", "explosive (yield|radius|size|power)", "entities",
                org.bukkit.entity.Explosive.class, Number.class, explosive -> (double) explosive.getYield(),
                (explosive, yield) -> explosive.setYield(yield.floatValue()));
        SettableProperty.register(registrar, "ExprLoveTime", "timespan", "love[d] time", "livingentities",
                org.bukkit.entity.Animals.class, co.xenastudios.neoskript.runtime.Timespan.class,
                animal -> new co.xenastudios.neoskript.runtime.Timespan(animal.getLoveModeTicks() * 50L),
                (animal, time) -> animal.setLoveModeTicks((int) time.ticks()));
        registrar.expression("ExprNearestEntity", "entity", info -> (context, args) -> {
            Location from = args[1] instanceof Location location ? location : args[1] instanceof Entity e ? e.getLocation() : null;
            if (from == null || from.getWorld() == null) {
                return null;
            }
            Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity entity : from.getWorld().getEntities()) {
                if (entity == args[1]) {
                    continue;
                }
                for (Object kind : (Object[]) args[0]) {
                    if (kind instanceof EntityKind k && k.matches(entity)) {
                        double distance = entity.getLocation().distanceSquared(from);
                        if (distance < best) {
                            best = distance;
                            nearest = entity;
                        }
                    }
                }
            }
            return nearest;
        }, "[the] (nearest|closest) %*entitytypes% [[relative] to %entity/location%]",
                "[the] %*entitytypes% (nearest|closest) [to %entity/location%]");

        registrar.effect("EffDrop", info -> (context, args) -> {
            Object[] places = args[1] == null ? new Object[0] : (Object[]) args[1];
            for (Object place : places) {
                if (!(place instanceof Location location) || location.getWorld() == null) {
                    continue;
                }
                for (ItemStack stack : Items.stacks((Object[]) args[0])) {
                    org.bukkit.entity.Item item = info.hasTag("still") ? location.getWorld().dropItem(location, stack)
                            : location.getWorld().dropItemNaturally(location, stack);
                    if (info.hasTag("still")) {
                        item.setVelocity(new Vector());
                    }
                    LAST_DROPPED.set(item);
                }
            }
        }, "drop %itemtypes% [(at|on) %locations%] [still:without velocity]");

        registrar.expression("ExprRandomUUID", "uuid", info -> (context, args) -> UUID.randomUUID(), "[a] random uuid");
        registrar.expression("ExprVectorRandom", "vector", info -> (context, args) -> Vector.getRandom()
                .subtract(new Vector(0.5, 0.5, 0.5)).normalize(), "[a] random vector");
        registrar.expression("ExprHash", "string", info -> {
            String algorithm = info.hasTag("md5") ? "MD5" : info.hasTag("384") ? "SHA-384"
                    : info.hasTag("512") ? "SHA-512" : "SHA-256";
            return (context, args) -> {
                List<Object> hashes = new ArrayList<>();
                for (Object text : (Object[]) args[0]) {
                    try {
                        MessageDigest digest = MessageDigest.getInstance(algorithm);
                        hashes.add(HexFormat.of().formatHex(digest.digest(Values.text(text).getBytes(StandardCharsets.UTF_8))));
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return hashes.size() == 1 ? hashes.getFirst() : hashes.toArray();
            };
        }, "%texts% hash[ed] with (md5:MD5|SHA-256|384:SHA-384|512:SHA-512)");
    }

    /**
     * A condition {@code %owners% (is|are) <property>} and its negation, true when {@code test} holds for
     * every owner. A {@code dead} or {@code un} tag flips it, for {@code is dead} and {@code is unconditional}.
     */
    private static <T> void condition(Registrar registrar, String id, String owners, String property, Class<T> type,
            Predicate<? super T> test) {
        registrar.condition(id, info -> {
            boolean negated = (info.pattern() == 1) != (info.hasTag("dead") || info.hasTag("un"));
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                for (Object value : values) {
                    if (!type.isInstance(value) || test.test(type.cast(value)) == negated) {
                        return false;
                    }
                }
                return values.length > 0;
            };
        }, "%" + owners + "% (is|are) " + property, "%" + owners + "% (isn't|is not|aren't|are not) " + property);
    }
}
