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
                condition(registrar, "CondIsAlive", "entities", "(alive|dead:dead)", Entity.class, entity -> !entity.isDead());

                                condition(registrar, "CondPandaIsScared", "livingentities", "scared", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isScared);
                condition(registrar, "CondIsValid", "entities/scripts", "valid", Object.class, value -> value instanceof Entity entity
                ? entity.isValid() : value instanceof co.xenastudios.neoskript.runtime.ScriptRef script
                        && co.xenastudios.neoskript.runtime.ScriptCatalog.get().find(script.name()) != null);

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

        registerSettableConditions(registrar);
        registerProperties(registrar);

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

    private static void registerProperties(Registrar registrar) {
        registrar.effect("EffVehicle", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (!(value instanceof Entity entity)) {
                        continue;
                    }
                    switch (pattern) {
                        case 0 -> {
                            if (args[1] instanceof Entity vehicle) {
                                vehicle.addPassenger(entity);
                            }
                        }
                        case 1 -> entity.leaveVehicle();
                        default -> entity.eject();
                    }
                }
            };
        }, "(make|let|force) %entities% [to] (ride|mount) [(in|on)] %entity%",
                "(make|let|force) %entities% [to] (dismount|(dismount|leave) [(from|of)] [(any|the[ir]|his|her)] vehicle[s])",
                "(eject|dismount) [(any|the)] passenger[s] (of|from) %entities%");
        registrar.expression("ExprVehicle", "entity", info -> (context, args) -> {
            List<Object> vehicles = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Entity entity && entity.getVehicle() != null) {
                    vehicles.add(entity.getVehicle());
                }
            }
            return vehicles.size() == 1 ? vehicles.getFirst() : vehicles.toArray();
        }, "[the] vehicle[s] of %entities%", "%entities%'[s] vehicle[s]");

        registrar.effect("EffPoison", info -> {
            boolean cure = info.pattern() == 1;
            return (context, args) -> {
                int ticks = !cure && args[1] instanceof co.xenastudios.neoskript.runtime.Timespan time ? (int) time.ticks() : 15 * 20;
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof LivingEntity living) {
                        if (cure) {
                            living.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
                        } else {
                            living.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, ticks, 0));
                        }
                    }
                }
            };
        }, "poison %livingentities% [for %timespan%]", "(cure|unpoison) %livingentities% [(from|of) poison]");
        condition(registrar, "CondIsPoisoned", "livingentities", "poisoned", LivingEntity.class,
                living -> living.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON));
        registrar.effect("EffHandedness", info -> {
            boolean left = info.hasTag("left");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof org.bukkit.entity.Mob mob) {
                        mob.setLeftHanded(left);
                    }
                }
            };
        }, "make %livingentities% (left:left|right)( |-)handed");
        registrar.effect("EffInvisible", info -> {
            boolean invisible = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof LivingEntity living) {
                        living.setInvisible(invisible);
                    }
                }
            };
        }, "make %livingentities% (invisible|not visible)", "make %livingentities% (visible|not invisible)");
        registrar.effect("EffToggleCanPickUpItems", info -> {
            boolean allow = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof LivingEntity living) {
                        living.setCanPickupItems(allow);
                    }
                }
            };
        }, "allow %livingentities% to pick([ ]up items| items up)",
                "(forbid|disallow) %livingentities% (from|to) pick([ing | ]up items|[ing] items up)");
        condition(registrar, "CondIsTameable", "livingentities", "tameable", LivingEntity.class,
                living -> living instanceof org.bukkit.entity.Tameable);
        condition(registrar, "CondIsLootable", "blocks/entities", "lootable", Object.class,
                value -> value instanceof org.bukkit.loot.Lootable || value instanceof org.bukkit.block.Block block
                        && block.getState() instanceof org.bukkit.loot.Lootable);

        SettableProperty.register(registrar, "ExprFireTicks", "timespan", "[max[imum]] (burn[ing]|fire) (time|duration)",
                "entities", Entity.class, co.xenastudios.neoskript.runtime.Timespan.class,
                entity -> new co.xenastudios.neoskript.runtime.Timespan(Math.max(0, entity.getFireTicks()) * 50L),
                (entity, time) -> entity.setFireTicks((int) time.ticks()));
        SettableProperty.register(registrar, "ExprFreezeTicks", "timespan", "freeze time", "entities", Entity.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                entity -> new co.xenastudios.neoskript.runtime.Timespan(entity.getFreezeTicks() * 50L),
                (entity, time) -> entity.setFreezeTicks((int) Math.min(entity.getMaxFreezeTicks(), time.ticks())));
        SettableProperty.register(registrar, "ExprPortalCooldown", "timespan", "portal cooldown", "entities", Entity.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                entity -> new co.xenastudios.neoskript.runtime.Timespan(entity.getPortalCooldown() * 50L),
                (entity, time) -> entity.setPortalCooldown((int) time.ticks()));
        SettableProperty.register(registrar, "ExprNoDamageTicks", "integer", LivingEntity.class, Number.class,
                living -> (long) living.getNoDamageTicks(), (living, ticks) -> living.setNoDamageTicks(ticks.intValue()),
                "[the] (invulnerability|invincibility|no damage) tick[s] [of %livingentities%]",
                "%livingentities%'[s] (invulnerability|invincibility|no damage) tick[s]");
        SettableProperty.register(registrar, "ExprNoDamageTimespan", "timespan", LivingEntity.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                living -> new co.xenastudios.neoskript.runtime.Timespan(living.getNoDamageTicks() * 50L),
                (living, time) -> living.setNoDamageTicks((int) time.ticks()),
                "[the] (invulnerability|invincibility|no damage) (time[span]|duration) [of %livingentities%]",
                "%livingentities%'[s] (invulnerability|invincibility|no damage) (time[span]|duration)");
        SettableProperty.register(registrar, "ExprArrowsStuck", "integer", LivingEntity.class, Number.class,
                living -> (long) living.getArrowsInBody(), (living, count) -> living.setArrowsInBody(Math.max(0, count.intValue())),
                "[number of] arrow[s] stuck in %livingentities%");
        registrar.expression("ExprVillagerLevel", "integer", info -> new SettableVillager(info.hasTag("experience")),
                "[the] villager (level|experience:experience) of %livingentities%",
                "%livingentities%'[s] villager (level|experience:experience)");
        registrar.expression("ExprInteractionDimensions", "number", info -> new SettableInteraction(info.hasTag("height")),
                "[the] interaction (height:height|width)[s] of %entities%", "%entities%'[s] interaction (height:height|width)[s]");

        registrar.type("attributetype", "attribute type", org.bukkit.attribute.Attribute.class);
        registrar.literalParser("attributetype", EntitySyntax::attribute);
        registrar.parser("attributetype", EntitySyntax::attribute);
        registrar.text(org.bukkit.attribute.Attribute.class, attribute -> attribute.getKey().getKey().replace('_', ' '));
        registrar.expression("ExprEntityAttribute", "number", info -> new AttributeValue(info.hasTag("total")),
                "[the] %attributetype% [total:(total|final|modified)] attribute [value] of %entities%",
                "%entities%'[s] %attributetype% [total:(total|final|modified)] attribute [value]");
    }

    /** The attribute {@code text} names ({@code movement speed}, {@code generic armour}), looked up when needed. */
    static org.bukkit.attribute.Attribute attribute(String text) {
        String name = text.strip().toLowerCase(java.util.Locale.ROOT).replaceFirst("^(generic|player) ", "")
                .replace("armour", "armor").replace(' ', '_');
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(name);
        return key == null ? null : org.bukkit.Registry.ATTRIBUTE.get(key);
    }

    /** {@code the villager level of %livingentities%} and its experience, which can be set. */
    private record SettableVillager(boolean experience) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Villager villager) {
                    values.add((long) (experience ? villager.getVillagerExperience() : villager.getVillagerLevel()));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            int by = delta.length > 0 && delta[0] instanceof Number n ? n.intValue() : 0;
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Villager villager) {
                    int current = experience ? villager.getVillagerExperience() : villager.getVillagerLevel();
                    int value = mode == Mode.ADD ? current + by : mode == Mode.REMOVE ? current - by : by;
                    if (experience) {
                        villager.setVillagerExperience(Math.max(0, value));
                    } else {
                        villager.setVillagerLevel(Math.max(1, Math.min(5, value)));
                    }
                }
            }
        }
    }

    /** {@code the interaction width of %entities%} and height, which can be set. */
    private record SettableInteraction(boolean height) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Interaction interaction) {
                    values.add((double) (height ? interaction.getInteractionHeight() : interaction.getInteractionWidth()));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            float value = delta.length > 0 && delta[0] instanceof Number n ? n.floatValue() : 0;
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Interaction interaction) {
                    if (height) {
                        interaction.setInteractionHeight(value);
                    } else {
                        interaction.setInteractionWidth(value);
                    }
                }
            }
        }
    }

    /** {@code the movement speed attribute of %entities%}: the base value, or with {@code total} the final one. */
    private record AttributeValue(boolean total) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[1])) {
                if (owner instanceof org.bukkit.attribute.Attributable attributable
                        && arguments[0] instanceof org.bukkit.attribute.Attribute attribute) {
                    org.bukkit.attribute.AttributeInstance instance = attributable.getAttribute(attribute);
                    if (instance != null) {
                        values.add(total ? instance.getValue() : instance.getBaseValue());
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return !total && (mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE) ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            double by = delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
            for (Object owner : owners(arguments[1])) {
                if (owner instanceof org.bukkit.attribute.Attributable attributable
                        && arguments[0] instanceof org.bukkit.attribute.Attribute attribute) {
                    org.bukkit.attribute.AttributeInstance instance = attributable.getAttribute(attribute);
                    if (instance != null) {
                        double current = instance.getBaseValue();
                        instance.setBaseValue(mode == Mode.ADD ? current + by : mode == Mode.REMOVE ? current - by : by);
                    }
                }
            }
        }
    }

    private static void registerSettableConditions(Registrar registrar) {
        condition(registrar, "CondIsCharged", "entities", "(charged|powered)", Entity.class,
                entity -> entity instanceof Creeper creeper ? creeper.isPowered()
                        : entity instanceof WitherSkull skull && skull.isCharged(), (entity, value) -> {
                    if (entity instanceof Creeper creeper) {
                        creeper.setPowered(value);
                    } else if (entity instanceof WitherSkull skull) {
                        skull.setCharged(value);
                    }
                });
        condition(registrar, "CondIsInvulnerable", "entities", "(invulnerable|invincible)", Entity.class,
                Entity::isInvulnerable, Entity::setInvulnerable);
        condition(registrar, "CondPandaIsRolling", "livingentities", "rolling", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isRolling, org.bukkit.entity.Panda::setRolling);
        condition(registrar, "CondPandaIsSneezing", "livingentities", "sneezing", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isSneezing, org.bukkit.entity.Panda::setSneezing);
        condition(registrar, "CondPandaIsOnBack", "livingentities", "on (its|their) back[s]", org.bukkit.entity.Panda.class,
                org.bukkit.entity.Panda::isOnBack, org.bukkit.entity.Panda::setOnBack);
        condition(registrar, "CondIsEating", "livingentities", "eating", LivingEntity.class,
                entity -> entity instanceof org.bukkit.entity.Panda panda ? panda.isEating()
                        : entity instanceof org.bukkit.entity.AbstractHorse horse && horse.isEating(), (entity, value) -> {
                    if (entity instanceof org.bukkit.entity.Panda panda) {
                        panda.setEating(value);
                    } else if (entity instanceof org.bukkit.entity.AbstractHorse horse) {
                        horse.setEating(value);
                    }
                });
        condition(registrar, "CondIsPersistent", "entities/blocks", "persistent", Object.class,
                value -> value instanceof Entity entity ? entity.isPersistent()
                        : value instanceof org.bukkit.block.Block block
                                && block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves && leaves.isPersistent(),
                (value, persistent) -> {
                    if (value instanceof Entity entity) {
                        entity.setPersistent(persistent);
                    } else if (value instanceof org.bukkit.block.Block block
                            && block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                        leaves.setPersistent(persistent);
                        block.setBlockData(leaves);
                    }
                });
        condition(registrar, "CondIsShivering", "livingentities", "shivering", org.bukkit.entity.Strider.class,
                org.bukkit.entity.Strider::isShivering, org.bukkit.entity.Strider::setShivering);
        condition(registrar, "CondIsCommandBlockConditional", "blocks", "[un:un]conditional", org.bukkit.block.Block.class,
                block -> block.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock command && command.isConditional(),
                (block, value) -> {
                    if (block.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock command) {
                        command.setConditional(value);
                        block.setBlockData(command);
                    }
                });
        conditionPatterns(registrar, "CondIsCustomNameVisible", Entity.class, Entity::isCustomNameVisible,
                Entity::setCustomNameVisible, "%entities%'[s] custom name[s] (is|are) visible",
                "custom name of %entities% (is|are) visible", "%entities%'[s] custom name[s] (isn't|is not|are not|aren't) visible",
                "custom name of %entities% (isn't|is not|are not|aren't) visible");
        conditionPatterns(registrar, "CondCanAge", org.bukkit.entity.Breedable.class, breedable -> !breedable.getAgeLock(),
                (breedable, value) -> breedable.setAgeLock(!value), "%livingentities% can (age|grow (up|old[er]))",
                "%livingentities% (can't|cannot|can not) (age|grow (up|old[er]))");
        conditionPatterns(registrar, "CondCanBreed", org.bukkit.entity.Breedable.class, org.bukkit.entity.Breedable::canBreed,
                org.bukkit.entity.Breedable::setBreed, "%livingentities% can (breed|be bred)",
                "%livingentities% (can't|cannot|can not) (breed|be bred)");
        condition(registrar, "CondIsAdult", "livingentities", "[an] adult", org.bukkit.entity.Ageable.class,
                org.bukkit.entity.Ageable::isAdult, (ageable, adult) -> {
                    if (adult) {
                        ageable.setAdult();
                    } else {
                        ageable.setBaby();
                    }
                });
        condition(registrar, "CondIsBaby", "livingentities", "a (child|baby)", org.bukkit.entity.Ageable.class,
                ageable -> !ageable.isAdult(), (ageable, baby) -> {
                    if (baby) {
                        ageable.setBaby();
                    } else {
                        ageable.setAdult();
                    }
                });
        condition(registrar, "CondIsScreaming", "livingentities", "screaming", LivingEntity.class,
                entity -> entity instanceof org.bukkit.entity.Goat goat ? goat.isScreaming()
                        : entity instanceof org.bukkit.entity.Enderman enderman && enderman.isScreaming(), (entity, value) -> {
                    if (entity instanceof org.bukkit.entity.Goat goat) {
                        goat.setScreaming(value);
                    } else if (entity instanceof org.bukkit.entity.Enderman enderman) {
                        enderman.setScreaming(value);
                    }
                });
        conditionPatterns(registrar, "CondIncendiary", org.bukkit.entity.Explosive.class,
                org.bukkit.entity.Explosive::isIncendiary, org.bukkit.entity.Explosive::setIsIncendiary,
                "%entities% ((is|are) incendiary|cause[s] a[n] (incendiary|fiery) explosion)",
                "%entities% ((is not|are not|isn't|aren't) incendiary|(does not|do not|doesn't|don't) cause[s] a[n] (incendiary|fiery) explosion)");
        conditionPatterns(registrar, "CondTextDisplayHasDropShadow", org.bukkit.entity.TextDisplay.class,
                org.bukkit.entity.TextDisplay::isShadowed, org.bukkit.entity.TextDisplay::setShadowed,
                "[[the] text of] %entities% (has|have) [a] (drop|text) shadow", "%entities%'[s] text (has|have) [a] (drop|text) shadow",
                "[[the] text of] %entities% (doesn't|does not|do not|don't) have [a] (drop|text) shadow",
                "%entities%'[s] text (doesn't|does not|do not|don't) have [a] (drop|text) shadow");
        condition(registrar, "CondTextDisplaySeeThroughBlocks", "entities", "visible through (blocks|walls)",
                org.bukkit.entity.TextDisplay.class, org.bukkit.entity.TextDisplay::isSeeThrough,
                org.bukkit.entity.TextDisplay::setSeeThrough);
        conditionPatterns(registrar, "CondAllayCanDuplicate", org.bukkit.entity.Allay.class,
                org.bukkit.entity.Allay::canDuplicate, org.bukkit.entity.Allay::setCanDuplicate,
                "%livingentities% can (duplicate|clone)", "%livingentities% (can't|cannot|can not) (duplicate|clone)");
        conditionPatterns(registrar, "CondCanPickUpItems", LivingEntity.class, LivingEntity::getCanPickupItems,
                LivingEntity::setCanPickupItems, "%livingentities% can pick([ ]up items| items up)",
                "%livingentities% (can't|cannot|can not) pick([ ]up items| items up)");
        conditionPatterns(registrar, "CondEndermanStaredAt", org.bukkit.entity.Enderman.class,
                org.bukkit.entity.Enderman::hasBeenStaredAt, org.bukkit.entity.Enderman::setHasBeenStaredAt,
                "%livingentities% (has|have) been stared at", "%livingentities% (doesn't|does not|do not|don't) have been stared at");
        condition(registrar, "CondIsSilent", "entities", "silent", Entity.class, Entity::isSilent, Entity::setSilent);
        condition(registrar, "CondIsSheared", "livingentities", "(sheared|shorn)", org.bukkit.entity.Sheep.class,
                org.bukkit.entity.Sheep::isSheared, org.bukkit.entity.Sheep::setSheared);
        condition(registrar, "CondIsSaddled", "livingentities", "[properly] saddled", org.bukkit.entity.Steerable.class,
                org.bukkit.entity.Steerable::hasSaddle, org.bukkit.entity.Steerable::setSaddle);
        condition(registrar, "CondIsInvisible", "livingentities", "(invisible|visible:flip)", LivingEntity.class,
                LivingEntity::isInvisible, LivingEntity::setInvisible);
        condition(registrar, "CondIsLeftHanded", "livingentities", "(left|right:flip)( |-)handed", org.bukkit.entity.Mob.class,
                org.bukkit.entity.Mob::isLeftHanded, org.bukkit.entity.Mob::setLeftHanded);
        condition(registrar, "CondIsTamed", "entities", "(tamed|domesticated)", org.bukkit.entity.Tameable.class,
                org.bukkit.entity.Tameable::isTamed, org.bukkit.entity.Tameable::setTamed);
        condition(registrar, "CondIsGliding", "livingentities", "gliding", LivingEntity.class, LivingEntity::isGliding,
                LivingEntity::setGliding);
        condition(registrar, "CondIsSwimming", "livingentities", "swimming", LivingEntity.class, LivingEntity::isSwimming,
                LivingEntity::setSwimming);
        condition(registrar, "CondIsRiptiding", "livingentities", "riptiding", LivingEntity.class, LivingEntity::isRiptiding,
                LivingEntity::setRiptiding);
        condition(registrar, "CondIsSleeping", "livingentities", "sleeping", LivingEntity.class, LivingEntity::isSleeping);
        condition(registrar, "CondIsInLove", "livingentities", "in lov(e|ing) [state|mode]", org.bukkit.entity.Animals.class,
                org.bukkit.entity.Animals::isLoveMode, (animal, love) -> animal.setLoveModeTicks(love ? 600 : 0));
        condition(registrar, "CondIsPlayingDead", "livingentities", "playing dead", org.bukkit.entity.Axolotl.class,
                org.bukkit.entity.Axolotl::isPlayingDead, org.bukkit.entity.Axolotl::setPlayingDead);
        condition(registrar, "CondIsResponsive", "entities", "responsive", org.bukkit.entity.Interaction.class,
                org.bukkit.entity.Interaction::isResponsive, org.bukkit.entity.Interaction::setResponsive);
        condition(registrar, "CondIsFrozen", "entities", "frozen", Entity.class, Entity::isFrozen,
                (entity, frozen) -> entity.setFreezeTicks(frozen ? entity.getMaxFreezeTicks() : 0));
        condition(registrar, "CondIsWhitelisted", "offlineplayers", "whitelisted", org.bukkit.OfflinePlayer.class,
                org.bukkit.OfflinePlayer::isWhitelisted, org.bukkit.OfflinePlayer::setWhitelisted);
        condition(registrar, "CondIsUnbreakable", "itemtypes", "[un:un]breakable", ItemType.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().isUnbreakable(),
                (item, value) -> item.editMeta(meta -> meta.setUnbreakable(value)));
        condition(registrar, "CondIsFireResistant", "itemtypes", "(fire resistant|resistant to fire)", ItemType.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().isFireResistant(),
                (item, value) -> item.editMeta(meta -> meta.setFireResistant(value)));
        conditionPatterns(registrar, "CondItemEnchantmentGlint", ItemType.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().hasEnchantmentGlintOverride(),
                (item, value) -> item.editMeta(meta -> meta.setEnchantmentGlintOverride(value ? Boolean.TRUE : null)),
                "%itemtypes% (has|have) enchantment glint overrid(den|e)",
                "%itemtypes% (doesn't|does not|do not|don't) have enchantment glint overrid(den|e)");
    }

    /**
     * A condition {@code %owners% (is|are) <property>} and its negation, true when {@code test} holds for
     * every owner. A {@code dead} or {@code un} tag flips it, for {@code is dead} and {@code is unconditional}.
     */
    private static <T> void condition(Registrar registrar, String id, String owners, String property, Class<T> type,
            Predicate<? super T> test) {
        condition(registrar, id, owners, property, type, test, null);
    }

    /** The same, settable with {@code setter}: {@code set whether {_pig} is saddled to true}. */
    private static <T> void condition(Registrar registrar, String id, String owners, String property, Class<T> type,
            Predicate<? super T> test, java.util.function.BiConsumer<? super T, Boolean> setter) {
        conditionPatterns(registrar, id, type, test, setter, "%" + owners + "% (is|are) " + property,
                "%" + owners + "% (isn't|is not|aren't|are not) " + property);
    }

    /**
     * A condition with its own positive and negative patterns (any number of each, positive first), true
     * when {@code test} holds for every owner, and settable if {@code setter} isn't {@code null}.
     */
    static <T> void conditionPatterns(Registrar registrar, String id, Class<T> type, Predicate<? super T> test,
            java.util.function.BiConsumer<? super T, Boolean> setter, String... patterns) {
        int half = patterns.length / 2;
        registrar.condition(id, info -> {
            boolean negated = (info.pattern() >= half) != (info.hasTag("dead") || info.hasTag("un") || info.hasTag("flip"));
            Predicate<Object[]> holds = values -> {
                for (Object value : values) {
                    if (!type.isInstance(value) || test.test(type.cast(value)) == negated) {
                        return false;
                    }
                }
                return values.length > 0;
            };
            if (setter == null) {
                return (context, args) -> holds.test(owners(args[0]));
            }
            return new co.xenastudios.neoskript.api.element.SettableCondition() {
                @Override
                public boolean check(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
                    return holds.test(owners(args[0]));
                }

                @Override
                public void set(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, boolean value) {
                    for (Object owner : owners(args[0])) {
                        if (type.isInstance(owner)) {
                            setter.accept(type.cast(owner), value != negated);
                        }
                    }
                }
            };
        }, patterns);
    }

    private static Object[] owners(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }
}
