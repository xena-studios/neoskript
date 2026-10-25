/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Entity properties and actions: targets, ages, fall distance, gravity and glowing, knockback, shearing,
 * pathfinding, entity sounds and snapshots, minecarts, arrows, pandas, goats, allays, endermen and wardens.
 */
final class EntityProperties {

    /** How far {@code the target of a player} looks, as Skript's default {@code maximum target block distance}. */
    private static final int TARGET_DISTANCE = 100;

    private EntityProperties() {}

    static void register(Registrar registrar) {
        registerConditions(registrar);
        registerEffects(registrar);
        registerExpressions(registrar);
        registerSounds(registrar);
        registerSnapshots(registrar);
    }

    /** {@code %owners% (is|are) <property>} and its negation. */
    private static String[] be(String owners, String property) {
        return Patterns.isProperty(property, owners);
    }

    private static void registerConditions(Registrar registrar) {
        registerResponsive(registrar);
        EntitySyntax.conditionPatterns(registrar, "CondAI", LivingEntity.class, LivingEntity::hasAI, null,
                Patterns.hasProperty("(ai|artificial intelligence)", "livingentities"));
        // Which liquid, by the pattern's mark: water (1), lava (2), a bubble column (3) or rain (4).
        registrar.condition("CondEntityIsInLiquid", info -> {
            int mark = info.mark();
            Predicate<Entity> test = switch (mark) {
                case 2 -> Entity::isInLava;
                case 3 -> Entity::isInBubbleColumn;
                case 4 -> Entity::isInRain;
                default -> Entity::isInWater;
            };
            return propertyCheck(info.pattern() == 1, info.and(0), Entity.class, test);
        }, be("entities", "in (1¦water|2¦lava|3¦[a] bubble[ ]column|4¦rain)"));
        EntitySyntax.conditionPatterns(registrar, "CondEntityIsWet", Entity.class, Entity::isInWaterOrRainOrBubbleColumn,
                null, be("entities", "wet"));
        EntitySyntax.conditionPatterns(registrar, "CondIsInOpenWater", Entity.class,
                entity -> entity instanceof org.bukkit.entity.FishHook hook && hook.isInOpenWater(), null,
                be("entities", "in open water[s]"));
        EntitySyntax.conditionPatterns(registrar, "CondFromMobSpawner", Entity.class, Entity::fromMobSpawner, null,
                "%entities% (is|are) from a [mob] spawner", "%entities% (was|were) spawned (from|by) a [mob] spawner",
                "%entities% (isn't|aren't|is not|are not) from a [mob] spawner",
                "%entities% (wasn't|weren't|was not|were not) spawned (from|by) a [mob] spawner");
        registrar.condition("CondIsPathfinding", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            boolean towards = info.hasTag("towards");
            return (context, args) -> {
                Object[] entities = Property.of(args[0]);
                Object target = args.length > 1 ? args[1] : null;
                if (entities.length == 0) {
                    return negated;
                }
                return Condition.check(entities, and, value -> pathfinding(value, towards, target) != negated);
            };
        }, be("livingentities", "pathfinding [towards:to[wards] %-livingentity/location%]"));
        registrar.condition("CondWithinRadius", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            boolean centresAnd = info.and(2);
            return (context, args) -> {
                double radius = args[1] instanceof Number number ? number.doubleValue() : 0;
                // Skript's epsilon, so points right on the edge count.
                double squared = radius * radius * 1.00001;
                Object[] locations = Property.of(args[0]);
                Object[] centres = Property.of(args[2]);
                if (locations.length == 0) {
                    return negated;
                }
                return Condition.check(locations, and, value -> Condition.check(centres, centresAnd, centre ->
                        value instanceof Location location && centre instanceof Location point
                                && location.getWorld() != null && location.getWorld().equals(point.getWorld())
                                && location.distanceSquared(point) <= squared) != negated);
            };
        }, be("locations", "within %number% (block|metre|meter)[s] (around|of) %locations%"));
        EntitySyntax.conditionPatterns(registrar, "CondEntityStorageIsFull", org.bukkit.block.Block.class,
                block -> block.getState() instanceof org.bukkit.block.EntityBlockStorage<?> storage && storage.isFull(), null,
                "[the] entity storage of %blocks% (is|are) full", "%blocks%'[s] entity storage (is|are) full",
                "[the] entity storage of %blocks% (isn't|is not|aren't|are not) full",
                "%blocks%'[s] entity storage (isn't|is not|aren't|are not) full");
    }

    /**
     * Whether interactions are responsive, or with {@code unresponsive} aren't; anything else is neither.
     * Settable.
     */
    private static void registerResponsive(Registrar registrar) {
        registrar.condition("CondIsResponsive", info -> {
            boolean negated = info.pattern() == 1;
            boolean responsive = !info.hasTag("unresponsive");
            boolean and = info.and(0);
            return new co.xenastudios.neoskript.api.element.SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] args) {
                    Object[] values = Property.of(args[0]);
                    if (values.length == 0) {
                        return negated;
                    }
                    return Condition.check(values, and, value -> (value instanceof org.bukkit.entity.Interaction interaction
                            && interaction.isResponsive() == responsive) != negated);
                }

                @Override
                public void set(TriggerContext context, Object[] args, boolean value) {
                    for (Object owner : Property.of(args[0])) {
                        if (owner instanceof org.bukkit.entity.Interaction interaction) {
                            interaction.setResponsive((value != negated) == responsive);
                        }
                    }
                }
            };
        }, be("entities", "(responsive|:unresponsive)"));
    }

    /** A property condition's check: {@code test} for each value, which must be a {@code type}. */
    private static <T> Condition propertyCheck(boolean negated, boolean and, Class<T> type, Predicate<? super T> test) {
        return (context, args) -> {
            Object[] values = Property.of(args[0]);
            if (values.length == 0) {
                return negated;
            }
            return Condition.check(values, and, value -> (type.isInstance(value) && test.test(type.cast(value))) != negated);
        };
    }

    /** Whether {@code value} is a mob finding a path, to {@code target} if {@code towards}. */
    private static boolean pathfinding(Object value, boolean towards, Object target) {
        if (!(value instanceof Mob mob)) {
            return false;
        }
        com.destroystokyo.paper.entity.Pathfinder pathfinder = mob.getPathfinder();
        if (!towards) {
            return pathfinder.hasPath();
        }
        com.destroystokyo.paper.entity.Pathfinder.PathResult current = pathfinder.getCurrentPath();
        if (target == null || current == null || current.getFinalPoint() == null) {
            return false;
        }
        Location end = current.getFinalPoint();
        if (target instanceof Location location) {
            return end.equals(location);
        }
        return target instanceof LivingEntity entity && end.getWorld() == entity.getWorld()
                && end.distance(entity.getLocation()) < 1;
    }

    private static void registerEffects(Registrar registrar) {
        registrar.effect("EffKnockback", info -> (context, args) -> {
            // No direction is no direction at all, as Skript's default for one.
            Direction direction = args[1] instanceof Direction given ? given : null;
            double strength = args[2] instanceof Number number ? number.doubleValue() : 1;
            for (Object value : Property.of(args[0])) {
                if (!(value instanceof LivingEntity living)) {
                    continue;
                }
                Vector push = direction == null ? new Vector() : direction.getDirection(living);
                // Knockback takes the direction it comes from, not the one it goes in.
                push.multiply(-1);
                living.knockback(strength, push.getX(), push.getZ());
                // So the client is told of the new velocity.
                living.setVelocity(living.getVelocity());
            }
        }, "(apply knockback to|knock[back]) %livingentities% [%direction%] [with (strength|force) %-number%]");
        registrar.effect("EffShear", info -> {
            boolean force = info.hasTag("force");
            boolean shear = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (shear) {
                        if (value instanceof io.papermc.paper.entity.Shearable shearable
                                && (force || shearable.readyToBeSheared())) {
                            shearable.shear();
                        }
                    } else if (value instanceof org.bukkit.entity.Sheep sheep) {
                        sheep.setSheared(false);
                    } else if (value instanceof org.bukkit.entity.Snowman snowman) {
                        snowman.setDerp(false);
                    }
                }
            };
        }, "[:force] shear %livingentities%", "un[-]shear %livingentities%");
        registrar.effect("EffFeed", info -> {
            boolean by = info.hasTag("by");
            return (context, args) -> {
                int level = 20;
                if (by) {
                    if (!(args[1] instanceof Number number)) {
                        return;
                    }
                    level = number.intValue();
                }
                for (Object value : Property.of(args[0])) {
                    if (value instanceof Player player) {
                        player.setFoodLevel(player.getFoodLevel() + level);
                    }
                }
            };
        }, "feed [the] %players% [by:by %-number% [beef[s]]]");
        registrar.effect("EffPathfind", info -> {
            boolean stop = info.pattern() == 1;
            return (context, args) -> {
                Object target = stop ? null : args[1];
                double speed = !stop && args[2] instanceof Number number ? number.doubleValue() : 1;
                for (Object value : Property.of(args[0])) {
                    if (!(value instanceof Mob mob)) {
                        continue;
                    }
                    if (stop) {
                        mob.getPathfinder().stopPathfinding();
                    } else if (target instanceof LivingEntity entity) {
                        mob.getPathfinder().moveTo(entity, speed);
                    } else if (target instanceof Location location) {
                        mob.getPathfinder().moveTo(location, speed);
                    }
                }
            };
        }, "make %livingentities% (pathfind|move) to[wards] %livingentity/location% [at speed %-number%]",
                "make %livingentities% stop (pathfinding|moving)");
        registrar.effect("EffEntityUnload", info -> {
            boolean despawn = info.pattern() != 2;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (value instanceof LivingEntity living) {
                        living.setRemoveWhenFarAway(despawn);
                    }
                }
            };
        }, "make %livingentities% despawn[able] (on chunk unload|when far away)",
                "force %livingentities% to despawn (on chunk unload|when far away)",
                "prevent %livingentities% from despawning [on chunk unload|when far away]");
        registrar.effect("EffItemDespawn", info -> {
            boolean prevent = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (value instanceof Item item) {
                        item.setUnlimitedLifetime(prevent);
                    }
                }
            };
        }, "(prevent|disallow) %entities% from (naturally despawning|despawning naturally)",
                "allow natural despawning of %entities%", "allow %entities% to (naturally despawn|despawn naturally)");
        registrar.effect("EffMakeResponsive", info -> {
            boolean responsive = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (value instanceof org.bukkit.entity.Interaction interaction) {
                        interaction.setResponsive(responsive);
                    }
                }
            };
        }, "make %entities% responsive", "make %entities% (not |un)responsive");
        registrar.effect("EffDancing", info -> {
            boolean start = info.pattern() == 0;
            return (context, args) -> {
                Location location = null;
                if (start && args[1] instanceof Direction direction && args[2] instanceof Location at) {
                    location = direction.getRelative(at);
                }
                long ticks = start && args[3] instanceof Timespan time ? time.ticks() : 0;
                for (Object value : Property.of(args[0])) {
                    if (value instanceof org.bukkit.entity.Allay allay) {
                        if (!start) {
                            allay.stopDancing();
                        } else if (location != null) {
                            allay.startDancing(location);
                        } else {
                            allay.startDancing();
                        }
                    } else if (value instanceof org.bukkit.entity.Piglin piglin) {
                        if (!start) {
                            piglin.setDancing(false);
                        } else if (ticks > 0) {
                            piglin.setDancing(ticks);
                        } else {
                            piglin.setDancing(true);
                        }
                    }
                }
            };
        }, "make %livingentities% (start dancing|dance) [%-direction% %-location%] [timespan:for %-timespan%]",
                "make %livingentities% (stop dancing|not dance)");
        registrar.effect("EffAllayCanDuplicate", info -> {
            boolean allow = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (value instanceof org.bukkit.entity.Allay allay) {
                        allay.setCanDuplicate(allow);
                    }
                }
            };
        }, "allow %livingentities% to (duplicate|clone)", "prevent %livingentities% from (duplicating|cloning)");
        registrar.effect("EffPandaOnBack", info -> {
            boolean on = info.hasTag("on");
            return pandas(panda -> panda.setOnBack(on));
        }, "make %livingentities% get (:on|off) (its|their) back[s]",
                "force %livingentities% to get (:on|off) (its|their) back[s]");
        registrar.effect("EffPandaRolling", info -> {
            boolean start = info.hasTag("start");
            return pandas(panda -> panda.setRolling(start));
        }, "make %livingentities% (start:(start rolling|roll)|stop rolling)", "force %livingentities% to (:start|stop) rolling");
        registrar.effect("EffPandaSneezing", info -> {
            boolean start = info.hasTag("start");
            return pandas(panda -> panda.setSneezing(start));
        }, "make %livingentities% (start:(start sneezing|sneeze)|stop sneezing)",
                "force %livingentities% to (:start|stop) sneezing");
        registrar.effect("EffGoatHorns", info -> {
            int horn = info.hasTag("right") ? 1 : info.hasTag("both") ? 2 : 0;
            // As in Skript, removing a horn sets it to be there, and regrowing it takes it away.
            boolean remove = info.pattern() <= 1;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    if (value instanceof org.bukkit.entity.Goat goat) {
                        if (horn != 1) {
                            goat.setLeftHorn(remove);
                        }
                        if (horn != 0) {
                            goat.setRightHorn(remove);
                        }
                    }
                }
            };
        }, "remove [the] (left horn[s]|right:right horn[s]|both:both horns) of %livingentities%",
                "remove %livingentities%'[s] (left horn[s]|right:right horn[s]|both:horns)",
                "(regrow|replace) [the] (left horn[s]|right:right horn[s]|both:both horns) of %livingentities%",
                "(regrow|replace) %livingentities%'[s] (left horn[s]|right:right horn[s]|both:horns)");
        registrar.effect("EffGoatRam", info -> (context, args) -> {
            if (!(args[1] instanceof LivingEntity target)) {
                return;
            }
            for (Object value : Property.of(args[0])) {
                if (value instanceof org.bukkit.entity.Goat goat) {
                    goat.ram(target);
                }
            }
        }, "make %livingentities% ram %livingentity%", "force %livingentities% to ram %livingentity%");
        registrar.effect("EffEndermanTeleport", info -> {
            boolean towards = info.pattern() >= 2;
            return (context, args) -> {
                Entity target = towards && args[1] instanceof Entity entity ? entity : null;
                for (Object value : Property.of(args[0])) {
                    if (value instanceof org.bukkit.entity.Enderman enderman) {
                        if (target != null) {
                            enderman.teleportTowards(target);
                        } else {
                            enderman.teleport();
                        }
                    }
                }
            };
        }, "make %livingentities% (randomly teleport|teleport randomly)",
                "force %livingentities% to (randomly teleport|teleport randomly)",
                "make %livingentities% teleport [randomly] towards %entity%",
                "force %livingentities% to teleport [randomly] towards %entity%");
        registrar.effect("EffWardenDisturbance", info -> (context, args) -> {
            if (!(args[1] instanceof Direction direction) || !(args[2] instanceof Location at)) {
                return;
            }
            Location location = direction.getRelative(at);
            for (Object value : Property.of(args[0])) {
                if (value instanceof org.bukkit.entity.Warden warden) {
                    warden.setDisturbanceLocation(location);
                }
            }
        }, "make %livingentities% sense [a] disturbance %direction% %location%");
    }

    /** An effect doing {@code action} to each panda. */
    private static co.xenastudios.neoskript.api.element.Effect pandas(
            java.util.function.Consumer<org.bukkit.entity.Panda> action) {
        return (context, args) -> {
            for (Object value : Property.of(args[0])) {
                if (value instanceof org.bukkit.entity.Panda panda) {
                    action.accept(panda);
                }
            }
        };
    }

    private static void registerExpressions(Registrar registrar) {
        registerTarget(registrar);
        // The age of a crop or sapling (its block's age), or an animal's (negative while a baby).
        registrar.expression("ExprAge", "integer", info -> {
            boolean max = info.hasTag("max");
            String type = info.argumentType(0);
            if (max && type != null && !type.equals("block") && !type.equals("object")) {
                throw new SyntaxError("Cannot use 'max age' expression with entities, use just the 'age' expression instead");
            }
            return new Property<>(Object.class, owner -> age(owner, max), Number.class,
                    max ? Property.modes() : Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.RESET),
                    (owner, delta, mode) -> {
                        int value = mode == Mode.RESET ? 0 : Property.intOf(delta[0]);
                        Long old = age(owner, false);
                        if (old == null && mode != Mode.RESET) {
                            return;
                        }
                        switch (mode) {
                            case ADD -> setAge(owner, old.intValue() + value);
                            case REMOVE -> setAge(owner, old.intValue() - value);
                            // A baby animal takes 20 minutes to grow up.
                            case RESET -> setAge(owner, owner instanceof org.bukkit.entity.Ageable ? -24000 : 0);
                            default -> setAge(owner, value);
                        }
                    });
        }, Patterns.property("[:max[imum]] age", "blocks/entities"));
        registrar.expression("ExprFallDistance", "number", info -> new Property<>(Entity.class,
                entity -> (double) entity.getFallDistance(), Number.class, Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE),
                (entity, delta, mode) -> {
                    float by = ((Number) delta[0]).floatValue();
                    entity.setFallDistance(switch (mode) {
                        case ADD -> entity.getFallDistance() + by;
                        case REMOVE -> entity.getFallDistance() - by;
                        default -> by;
                    });
                }), Patterns.property("fall[en] (distance|height)", "entities"));
        registrar.expression("ExprGlowing", "boolean", info -> new Property<>(Entity.class, Entity::isGlowing, Boolean.class,
                Property.modes(Mode.SET, Mode.RESET), (entity, delta, mode) -> entity.setGlowing(delta != null && (Boolean) delta[0])),
                Patterns.property("glowing", "entities"));
        registrar.expression("ExprGravity", "boolean", info -> new Property<>(Entity.class, Entity::hasGravity, Boolean.class,
                Property.modes(Mode.SET, Mode.RESET), (entity, delta, mode) -> entity.setGravity(delta == null || (Boolean) delta[0])),
                Patterns.property("gravity", "entities"));
        registrar.expression("ExprGlidingState", "boolean", info -> new Property<>(LivingEntity.class, LivingEntity::isGliding,
                Boolean.class, Property.modes(Mode.SET, Mode.RESET),
                (entity, delta, mode) -> entity.setGliding(delta != null && (Boolean) delta[0])),
                Patterns.property("(gliding|glider) [state]", "livingentities"));
        registrar.expression("ExprTimeLived", "timespan", info -> new Property<>(Entity.class,
                entity -> Property.ticks(entity.getTicksLived()), Timespan.class,
                Property.modes(Mode.ADD, Mode.REMOVE, Mode.SET, Mode.RESET), (entity, delta, mode) -> {
                    long ticks = delta != null && delta[0] instanceof Timespan time ? (int) time.ticks() : 1;
                    long current = entity.getTicksLived();
                    long value = switch (mode) {
                        case ADD -> current + ticks;
                        case REMOVE -> current - ticks;
                        default -> ticks;
                    };
                    entity.setTicksLived((int) Property.fit(1, value, Integer.MAX_VALUE));
                }), Patterns.property("time (alive|lived)", "entities"));
        registrar.expression("ExprPickupDelay", "timespan", info -> new Property<>(Entity.class,
                entity -> entity instanceof Item item ? Property.ticks(item.getPickupDelay()) : null, Timespan.class,
                Property.modes(Mode.SET, Mode.ADD, Mode.RESET, Mode.DELETE, Mode.REMOVE), (entity, delta, mode) -> {
                    if (!(entity instanceof Item item)) {
                        return;
                    }
                    int change = delta == null ? 0 : (int) ((Timespan) delta[0]).ticks();
                    switch (mode) {
                        case ADD -> item.setPickupDelay(item.getPickupDelay() + change);
                        case REMOVE -> item.setPickupDelay(item.getPickupDelay() - change);
                        default -> item.setPickupDelay(change);
                    }
                }), Patterns.property("pick[ ]up delay", "entities"));
        registrar.expression("ExprNoDamageTime", "timespan", info -> new Property<>(LivingEntity.class,
                living -> Property.ticks(living.getNoDamageTicks()), Timespan.class,
                Property.modes(Mode.SET, Mode.DELETE, Mode.RESET, Mode.ADD, Mode.REMOVE), (living, delta, mode) -> {
                    int ticks = delta != null && delta[0] instanceof Timespan time ? (int) time.ticks() : 0;
                    switch (mode) {
                        case ADD -> living.setNoDamageTicks((int) Property.fit(0, (long) living.getNoDamageTicks() + ticks,
                                Integer.MAX_VALUE));
                        case REMOVE -> living.setNoDamageTicks((int) Property.fit(0, (long) living.getNoDamageTicks() - ticks,
                                Integer.MAX_VALUE));
                        default -> living.setNoDamageTicks(ticks);
                    }
                }).defaulting(), Property.defaultPatterns("(invulnerability|invincibility|no damage) time[[ ]span]",
                "livingentities"));
        registrar.expression("ExprMaxFreezeTicks", "timespan", info -> new Property<>(Entity.class,
                entity -> Property.ticks(entity.getMaxFreezeTicks())), Patterns.property("max[imum] freeze time", "entities"));
        registrar.expression("ExprLastAttacker", "entity", info -> new Property<>(Entity.class,
                entity -> attacker(entity.getLastDamageCause())), Patterns.property("last attacker", "entity"));
        // No damage yet counts as 'custom', which is what deleting or resetting it gives too.
        registrar.expression("ExprLastDamageCause", "damagecause", info -> new Property<>(LivingEntity.class,
                living -> living.getLastDamageCause() == null ? DamageCause.CUSTOM : living.getLastDamageCause().getCause(),
                DamageCause.class, Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.DELETE, Mode.RESET),
                (living, delta, mode) -> {
                    if (mode == Mode.SET) {
                        setDamageCause(living, (DamageCause) delta[0]);
                    } else if (mode == Mode.DELETE || mode == Mode.RESET) {
                        setDamageCause(living, DamageCause.CUSTOM);
                    }
                }), Patterns.property("last damage (cause|reason|type)", "livingentities"));
        registrar.expression("ExprAttackCooldown", "number", info -> new Property<>(org.bukkit.entity.HumanEntity.class,
                human -> (double) human.getAttackCooldown()), Patterns.property("attack cooldown", "players"));
        registrar.expression("ExprActiveItem", "itemstack", info -> new Property<>(LivingEntity.class, living -> {
            ItemStack item = living.getActiveItem();
            return item.getType() == Material.AIR ? null : item;
        }), Patterns.property("(raised|active) (tool|item|weapon)", "livingentities"));
        registrar.expression("ExprEntityItemUseTime", "timespan", info -> {
            boolean remaining = info.hasTag("remaining");
            return new Property<>(LivingEntity.class, living -> Property.ticks(remaining ? living.getItemUseRemainingTime()
                    : living.getHandRaisedTime()));
        }, Patterns.property("[elapsed|:remaining] (item|tool) us[ag]e time", "livingentities"));
        registrar.expression("ExprMaxItemUseTime", "timespan", info -> new Property<>(ItemStack.class,
                stack -> Property.ticks(maxUseDuration(stack))),
                Patterns.property("max[imum] [item] us(e|age) (time|duration)", "itemstacks"));
        registrar.expression("ExprAllayJukebox", "location", info -> new Property<>(LivingEntity.class,
                living -> living instanceof org.bukkit.entity.Allay allay ? allay.getJukebox() : null).defaulting(),
                Property.defaultPatterns("target jukebox", "livingentities"));
        // Non-creepers' fuses are 0 ticks long; resetting makes a creeper's 30 again.
        registrar.expression("ExprCreeperMaxFuseTicks", "integer", info -> new Property<>(LivingEntity.class,
                living -> living instanceof org.bukkit.entity.Creeper creeper ? (long) creeper.getMaxFuseTicks() : 0L,
                Number.class, Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.DELETE, Mode.RESET), (living, delta, mode) -> {
                    if (!(living instanceof org.bukkit.entity.Creeper creeper)) {
                        return;
                    }
                    int by = delta == null ? 0 : Property.intOf(delta[0]);
                    creeper.setMaxFuseTicks(Math.max(0, switch (mode) {
                        case ADD -> creeper.getMaxFuseTicks() + by;
                        case REMOVE -> creeper.getMaxFuseTicks() - by;
                        case DELETE -> 0;
                        case RESET -> 30;
                        default -> by;
                    }));
                }), Patterns.property("[creeper] max[imum] fuse tick[s]", "livingentities"));
        registrar.expression("ExprMaxMinecartSpeed", "number", info -> new Property<>(Entity.class,
                entity -> entity instanceof Minecart minecart ? minecart.getMaxSpeed() : null, Number.class,
                Property.modes(Mode.ADD, Mode.REMOVE, Mode.RESET, Mode.SET), (entity, delta, mode) -> {
                    if (!(entity instanceof Minecart minecart)) {
                        return;
                    }
                    switch (mode) {
                        case RESET -> minecart.setMaxSpeed(0.4);
                        case ADD -> minecart.setMaxSpeed(minecart.getMaxSpeed() + ((Number) delta[0]).doubleValue());
                        case REMOVE -> minecart.setMaxSpeed(minecart.getMaxSpeed() - ((Number) delta[0]).doubleValue());
                        default -> minecart.setMaxSpeed(((Number) delta[0]).doubleValue());
                    }
                }), Patterns.property("max[imum] minecart (speed|velocity)", "entities"));
        registrar.expression("ExprMinecartDerailedFlyingVelocity", "vector", info -> {
            boolean flying = info.mark() == 2;
            return new Property<>(Entity.class, entity -> entity instanceof Minecart minecart
                    ? (flying ? minecart.getFlyingVelocityMod() : minecart.getDerailedVelocityMod()) : null, Vector.class,
                    Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE), (entity, delta, mode) -> {
                        if (!(entity instanceof Minecart minecart)) {
                            return;
                        }
                        Vector given = ((Vector) delta[0]).clone();
                        Vector current = flying ? minecart.getFlyingVelocityMod() : minecart.getDerailedVelocityMod();
                        // As in Skript, removing takes the current velocity from the one given.
                        Vector value = switch (mode) {
                            case ADD -> given.add(current);
                            case REMOVE -> given.subtract(current);
                            default -> given;
                        };
                        if (flying) {
                            minecart.setFlyingVelocityMod(value);
                        } else {
                            minecart.setDerailedVelocityMod(value);
                        }
                    });
        }, Patterns.property("[minecart] (1¦derailed|2¦flying) velocity", "entities"));
        registerArrows(registrar);
        registerItemEntities(registrar);
        registerInteractions(registrar);
        registrar.expression("ExprBed", "location", info -> {
            boolean safe = info.hasTag("safe");
            return new Property<>(OfflinePlayer.class, OfflinePlayer::getBedSpawnLocation, Location.class,
                    Property.modes(Mode.SET, Mode.DELETE), (player, delta, mode) -> {
                        Player online = player.getPlayer();
                        if (online != null) {
                            online.setBedSpawnLocation(delta == null ? null : (Location) delta[0], !safe);
                        }
                    });
        }, Patterns.property("[(safe:(safe|valid)|(unsafe|invalid))] bed[s] [location[s]]", "offlineplayers"));
    }

    @SuppressWarnings("removal")
    private static int maxUseDuration(ItemStack stack) {
        return stack.getMaxItemUseDuration();
    }

    private static Long age(Object owner, boolean max) {
        if (owner instanceof org.bukkit.block.Block block) {
            return block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                    ? (long) (max ? ageable.getMaximumAge() : ageable.getAge()) : null;
        }
        return !max && owner instanceof org.bukkit.entity.Ageable ageable ? (long) ageable.getAge() : null;
    }

    private static void setAge(Object owner, int value) {
        if (owner instanceof org.bukkit.block.Block block) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                ageable.setAge(Math.max(Math.min(value, ageable.getMaximumAge()), 0));
                block.setBlockData(ageable);
            }
        } else if (owner instanceof org.bukkit.entity.Ageable ageable) {
            ageable.setAge(value);
        }
    }

    /** Who did the damage: the damager, or a projectile's shooter, as Skript's {@code attacker}. */
    static Entity attacker(EntityDamageEvent event) {
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent damage) {
            Entity damager = damage.getDamager();
            if (damager instanceof org.bukkit.entity.Projectile projectile) {
                return projectile.getShooter() instanceof Entity shooter ? shooter : null;
            }
            return damager;
        }
        return null;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void setDamageCause(LivingEntity living, DamageCause cause) {
        living.setLastDamageCause(new EntityDamageEvent(living, cause,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 0));
    }

    private static void registerArrows(Registrar registrar) {
        registrar.expression("ExprArrowKnockbackStrength", "integer", info -> new Property<>(Entity.class,
                entity -> entity instanceof AbstractArrow arrow ? (long) knockback(arrow) : null, Number.class,
                Property.modes(Mode.SET, Mode.ADD, Mode.RESET, Mode.REMOVE), (entity, delta, mode) -> {
                    if (!(entity instanceof AbstractArrow arrow)) {
                        return;
                    }
                    int strength = delta == null ? 0 : Property.intOf(delta[0]);
                    switch (mode) {
                        case REMOVE -> setKnockback(arrow, Math.max(0, knockback(arrow) - strength));
                        case ADD -> {
                            if (knockback(arrow) + strength >= 0) {
                                setKnockback(arrow, knockback(arrow) + strength);
                            }
                        }
                        default -> setKnockback(arrow, strength);
                    }
                }), Patterns.property("arrow knockback strength", "entities"));
        // Arrows pierce up to 127 entities.
        registrar.expression("ExprArrowPierceLevel", "integer", info -> new Property<>(Entity.class,
                entity -> entity instanceof AbstractArrow arrow ? (long) arrow.getPierceLevel() : null, Number.class,
                Property.modes(Mode.SET, Mode.RESET, Mode.REMOVE, Mode.ADD), (entity, delta, mode) -> {
                    if (!(entity instanceof AbstractArrow arrow)) {
                        return;
                    }
                    int level = delta == null ? 0 : Math.max(Property.intOf(delta[0]), 0);
                    int value = switch (mode) {
                        case ADD -> arrow.getPierceLevel() + level;
                        case REMOVE -> arrow.getPierceLevel() - level;
                        default -> level;
                    };
                    arrow.setPierceLevel(Math.max(0, Math.min(127, value)));
                }), Patterns.property("arrow pierce level", "entities"));
        registrar.expression("ExprProjectileCriticalState", "boolean", info -> new Property<>(Entity.class,
                entity -> entity instanceof AbstractArrow arrow ? arrow.isCritical() : null, Boolean.class,
                Property.modes(Mode.SET), (entity, delta, mode) -> {
                    if (entity instanceof AbstractArrow arrow) {
                        arrow.setCritical((Boolean) delta[0]);
                    }
                }), Patterns.property("(projectile|arrow) critical (state|ability|mode)", "entities"));
        registrar.expression("ExprAttachedBlock", "block", info -> {
            boolean multiple = info.hasTag("multiple");
            if (!multiple) {
                String projectiles = co.xenastudios.neoskript.api.element.Warnings.argument(0);
                co.xenastudios.neoskript.api.element.Warnings.warn("It is recommended to use the plural version of this"
                        + " expression instead: 'attached blocks of " + (projectiles == null ? "event-projectile" : projectiles)
                        + "'");
            }
            return (context, args) -> {
                Set<Object> blocks = new LinkedHashSet<>();
                for (Object value : Property.of(args[0])) {
                    if (value instanceof AbstractArrow arrow) {
                        if (multiple) {
                            blocks.addAll(arrow.getAttachedBlocks());
                        } else if (arrow.getAttachedBlock() != null) {
                            blocks.add(arrow.getAttachedBlock());
                        }
                    }
                }
                return Property.single(new ArrayList<>(blocks));
            };
        }, Patterns.property("(attached|hit) block[multiple:s]", "entities"));
    }

    @SuppressWarnings("removal")
    private static int knockback(AbstractArrow arrow) {
        return arrow.getKnockbackStrength();
    }

    @SuppressWarnings("removal")
    private static void setKnockback(AbstractArrow arrow, int strength) {
        arrow.setKnockbackStrength(strength);
    }

    /** Dropped items: who threw them and who may pick them up, by UUID. */
    private static void registerItemEntities(Registrar registrar) {
        for (boolean thrower : new boolean[] {false, true}) {
            String who = thrower ? "thrower" : "owner";
            registrar.expression(thrower ? "ExprItemThrower" : "ExprItemOwner", "uuid", info -> new Property<>(Item.class,
                    item -> thrower ? item.getThrower() : item.getOwner(), Object.class, Property.modes(Mode.SET, Mode.DELETE),
                    (item, delta, mode) -> {
                        UUID uuid = delta == null ? null : uuid(delta[0]);
                        if (thrower) {
                            item.setThrower(uuid);
                        } else {
                            item.setOwner(uuid);
                        }
                    }).defaulting(),
                    "[the] uuid of [the] [dropped] item " + who + " [of %entities%]",
                    "[the] [dropped] item " + who + "'s uuid [of %entities%]");
        }
    }

    /** The UUID of an entity or player, or the UUID given. */
    private static UUID uuid(Object value) {
        return switch (value) {
            case UUID uuid -> uuid;
            case Entity entity -> entity.getUniqueId();
            case OfflinePlayer player -> player.getUniqueId();
            case null, default -> null;
        };
    }

    /** Interaction entities: the last players to click them, and when. */
    private static void registerInteractions(Registrar registrar) {
        registrar.expression("ExprLastInteractionDate", "date", info -> {
            int type = info.mark();
            return new Property<>(Entity.class, entity -> {
                org.bukkit.entity.Interaction.PreviousInteraction last = lastInteraction(entity, type);
                return last == null ? null : new co.xenastudios.neoskript.runtime.Date(last.getTimestamp());
            });
        }, "[the] last (date|time)[s] [that|when] %entities% (were|was) (attacked|1¦interacted with|2¦clicked [on])");
        registrar.expression("ExprLastInteractionPlayer", "offlineplayer", info -> {
            int type = info.mark();
            return new Property<>(Entity.class, entity -> {
                org.bukkit.entity.Interaction.PreviousInteraction last = lastInteraction(entity, type);
                return last == null ? null : last.getPlayer();
            });
        }, "[the] last player[s] to (attack|1¦interact with|2¦click [on]) %entities%",
                "[the] last player[s] (who|that) (attacked|1¦interacted with|2¦clicked [on]) %entities%");
    }

    /** An interaction's last attack (0), its last interaction (1), or whichever was later (2). */
    private static org.bukkit.entity.Interaction.PreviousInteraction lastInteraction(Entity entity, int type) {
        if (!(entity instanceof org.bukkit.entity.Interaction interaction)) {
            return null;
        }
        org.bukkit.entity.Interaction.PreviousInteraction attack = interaction.getLastAttack();
        org.bukkit.entity.Interaction.PreviousInteraction interact = interaction.getLastInteraction();
        return switch (type) {
            case 0 -> attack;
            case 1 -> interact;
            default -> attack == null ? interact : interact == null ? attack
                    : attack.getTimestamp() >= interact.getTimestamp() ? attack : interact;
        };
    }

    /**
     * {@code the target of %livingentities%}: a mob's target, or what a player looks at. In a target event,
     * the target the event is about; setting it there changes the event's target.
     */
    private static void registerTarget(Registrar registrar) {
        registrar.expression("ExprTarget", "entity", info -> {
            int pattern = info.pattern();
            boolean ignoreBlocks = info.hasTag("blocks");
            return new Target(pattern, ignoreBlocks);
        }, "[the] target[[ed] %-*entitytype%] [of %livingentities%] [blocks:ignoring blocks] [[with|at] [a] ray[ ]size [of] %-number%]",
                "%livingentities%'[s] target[[ed] %-*entitytype%] [blocks:ignoring blocks] [[with|at] [a] ray[ ]size [of] %-number%]");
        // 'future target' in a target event, of the event's entity, is the target it's getting; there's no past one.
        registrar.timeStates("ExprTarget", info -> info.time() > 0 && info.isDefault(1 - info.pattern())
                && info.isEvent(org.bukkit.event.entity.EntityTargetEvent.class));
    }

    /** {@code the target of %livingentities%}, settable. */
    private record Target(int pattern, boolean ignoreBlocks) implements Expression, Changeable {

        private EntityKind type(Object[] args) {
            return args[pattern] instanceof EntityKind kind ? kind : null;
        }

        private Object[] entities(TriggerContext context, Object[] args) {
            Object entities = args[1 - pattern];
            if (entities == null) {
                Object value = Property.eventValue(context);
                return value instanceof LivingEntity ? new Object[] {value} : new Object[0];
            }
            return Property.of(entities);
        }

        private double raySize(Object[] args) {
            return args[2] instanceof Number number ? number.doubleValue() : 0;
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            EntityKind type = type(args);
            double raySize = raySize(args);
            List<Object> targets = new ArrayList<>();
            for (Object value : entities(context, args)) {
                if (!(value instanceof LivingEntity entity)) {
                    continue;
                }
                Entity target;
                if (context.event() instanceof org.bukkit.event.entity.EntityTargetEvent event && entity.equals(event.getEntity())) {
                    target = event.getTarget();
                    if (target != null && type != null && !type.matches(target)) {
                        target = null;
                    }
                } else {
                    target = target(entity, type, raySize, ignoreBlocks);
                }
                if (target != null) {
                    targets.add(target);
                }
            }
            return Property.single(targets);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {LivingEntity.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            // No target makes mobs lose theirs; deleting a player's removes what they look at.
            LivingEntity target = delta == null || delta.length == 0 ? null : (LivingEntity) delta[0];
            if (context.event() instanceof org.bukkit.event.entity.EntityTargetEvent event) {
                for (Object value : entities(context, args)) {
                    if (value.equals(event.getEntity())) {
                        event.setTarget(target);
                    }
                }
                return;
            }
            for (Object value : entities(context, args)) {
                if (value instanceof Mob mob) {
                    mob.setTarget(target);
                } else if (value instanceof Player player && mode == Mode.DELETE) {
                    Entity looked = target(player, type(args), raySize(args), ignoreBlocks);
                    if (looked != null && !(looked instanceof OfflinePlayer)) {
                        looked.remove();
                    }
                }
            }
        }
    }

    /** A mob's target, or the entity {@code origin} looks at, of {@code type} if one's given. */
    static Entity target(LivingEntity origin, EntityKind type, double raySize, boolean ignoreBlocks) {
        if (origin instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target == null || type != null && !type.matches(target) ? null : target;
        }
        Predicate<Entity> wanted = entity -> !entity.equals(origin) && (type == null || type.matches(entity))
                && !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR);
        Location eyes = origin.getEyeLocation();
        Vector direction = origin.getLocation().getDirection();
        double distance = TARGET_DISTANCE;
        if (!ignoreBlocks) {
            RayTraceResult block = origin.getWorld().rayTraceBlocks(eyes, direction, TARGET_DISTANCE);
            if (block != null) {
                distance = eyes.toVector().distance(block.getHitPosition());
            }
        }
        RayTraceResult result = origin.getWorld().rayTraceEntities(eyes, direction, distance, raySize, wanted);
        return result == null ? null : result.getHitEntity();
    }

    /** The sounds entities make: {@code the death sound of %livingentities%}. */
    private static void registerSounds(Registrar registrar) {
        registrar.expression("ExprEntitySound", "string", info -> {
            int kind = info.pattern() / 2;
            boolean possessive = info.pattern() % 2 == 1;
            boolean big = info.hasTag("high") || info.hasTag("fast");
            return (context, args) -> {
                // The fall sound's height comes before the entities when they're written after it.
                int entitiesAt = kind == 2 && !possessive ? 1 : 0;
                Object heightArg = kind == 2 ? args[possessive ? 1 : 0] : null;
                int height = heightArg instanceof Number number ? number.intValue() : -1;
                ItemStack item = new ItemStack(kind == 5 ? Material.COOKED_BEEF : Material.POTION);
                if ((kind == 5 || kind == 6) && !possessive && args.length > 1 && args[1] instanceof ItemType given) {
                    item = given.stack().clone();
                }
                Set<String> sounds = new LinkedHashSet<>();
                for (Object value : Property.of(args[entitiesAt])) {
                    if (!(value instanceof LivingEntity entity)) {
                        continue;
                    }
                    Sound sound = switch (kind) {
                        case 0 -> entity.getHurtSound();
                        case 1 -> entity.getDeathSound();
                        case 2 -> height != -1 ? entity.getFallDamageSound(height)
                                : big ? entity.getFallDamageSoundBig() : entity.getFallDamageSoundSmall();
                        case 3 -> entity.getSwimSound();
                        case 4 -> big ? entity.getSwimHighSpeedSplashSound() : entity.getSwimSplashSound();
                        case 5 -> entity.getEatingSound(item);
                        case 6 -> entity.getDrinkingSound(item);
                        default -> entity instanceof Mob mob ? mob.getAmbientSound() : null;
                    };
                    if (sound != null) {
                        sounds.add(soundKey(sound));
                    }
                }
                return Property.single(new ArrayList<>(sounds));
            };
        }, "[the] (damage|hurt) sound[s] of %livingentities%", "%livingentities%'[s] (damage|hurt) sound[s]",
                "[the] death sound[s] of %livingentities%", "%livingentities%'[s] death sound[s]",
                "[the] [high:(tall|high)|(low|normal)] fall damage sound[s] [from [[a] height [of]] %-number%] of %livingentities%",
                "%livingentities%'[s] [high:(tall|high)|low:(low|normal)] fall [damage] sound[s] [from [[a] height [of]] %-number%]",
                "[the] swim[ming] sound[s] of %livingentities%", "%livingentities%'[s] swim[ming] sound[s]",
                "[the] [fast:(fast|speedy)] splash sound[s] of %livingentities%", "%livingentities%'[s] [fast:(fast|speedy)] splash sound[s]",
                "[the] eat[ing] sound[s] of %livingentities% [(with|using|[while] eating [a]) %-itemtype%]",
                "%livingentities%'[s] eat[ing] sound[s]",
                "[the] drink[ing] sound[s] of %livingentities% [(with|using|[while] drinking [a]) %-itemtype%]",
                "%livingentities%'[s] drink[ing] sound[s]",
                "[the] ambient sound[s] of %livingentities%", "%livingentities%'[s] ambient sound[s]");
    }

    /** A sound's key without its namespace, as Skript gives it: {@code entity.zombie.hurt}. */
    @SuppressWarnings("removal")
    static String soundKey(Sound sound) {
        return sound.getKey().getKey();
    }

    /** Entity snapshots, the templates entities can be spawned from. */
    private static void registerSnapshots(Registrar registrar) {
        registrar.expression("ExprEntitySnapshot", "entitysnapshot", info -> {
            String type = info.argumentType(0);
            if ("player".equals(type)) {
                throw new SyntaxError("One or more listed entities can not return an entity snapshot.");
            }
            return new Property<>(Object.class, EntityProperties::snapshot);
        }, Patterns.property("entity snapshot", "entities/entitytypes"));
        registrar.expression("ExprSpawnEggEntity", "entitysnapshot", info -> new SpawnEggEntity(),
                Patterns.property("spawn egg entity", "itemstacks/itemtypes/slots"));
    }

    /** The entity snapshot type, registered before spawning, which can spawn them. */
    static void registerTypes(Registrar registrar) {
        registrar.type("entitysnapshot", "entity snapshot", EntitySnapshot.class);
        registrar.text(EntitySnapshot.class, snapshot -> EntityKind.of(snapshot.getEntityType()) + " snapshot");
        registrar.converter(EntitySnapshot.class, EntityKind.class, snapshot -> EntityKind.of(snapshot.getEntityType()));
        // Snapshots of the same data are the same, as Skript compares them.
        registrar.comparison(EntitySnapshot.class, EntitySnapshot.class, (one, two) -> one.getEntityType() == two.getEntityType()
                && one.getAsString().equalsIgnoreCase(two.getAsString()));
    }

    /** A snapshot of an entity, or of a new entity of a type, which isn't spawned. */
    static EntitySnapshot snapshot(Object value) {
        if (value instanceof Player || value instanceof org.bukkit.entity.FishHook) {
            return null;
        }
        if (value instanceof Entity entity) {
            return entity.createSnapshot();
        }
        if (value instanceof EntityKind kind && kind.type() != null && kind.type().getEntityClass() != null
                && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorlds().getFirst();
            Entity entity = world.createEntity(world.getSpawnLocation(), kind.type().getEntityClass());
            EntitySnapshot snapshot = entity.createSnapshot();
            entity.remove();
            return snapshot;
        }
        return null;
    }

    /** {@code the spawn egg entity of %itemstacks/itemtypes/slots%}: what a spawn egg spawns; settable. */
    private static final class SpawnEggEntity implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> snapshots = new ArrayList<>();
            for (Object value : Property.of(arguments[0])) {
                ItemStack stack = stack(value);
                if (stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.SpawnEggMeta egg
                        && egg.getSpawnedEntity() != null) {
                    snapshots.add(egg.getSpawnedEntity());
                }
            }
            return Property.single(snapshots);
        }

        private static ItemStack stack(Object value) {
            return switch (value) {
                case ItemType item -> item.stack();
                case ItemStack stack -> stack;
                case Items.Slot slot -> slot.item();
                case null, default -> null;
            };
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {EntitySnapshot.class, Entity.class, EntityKind.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            EntitySnapshot snapshot = delta[0] instanceof EntitySnapshot given ? given : snapshot(delta[0]);
            if (snapshot == null) {
                return;
            }
            for (Object value : Property.of(arguments[0])) {
                ItemStack stack = stack(value);
                if (stack == null || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.SpawnEggMeta egg)) {
                    continue;
                }
                egg.setSpawnedEntity(snapshot);
                switch (value) {
                    case ItemType item -> item.editMeta(meta -> ((org.bukkit.inventory.meta.SpawnEggMeta) meta)
                            .setSpawnedEntity(snapshot));
                    case Items.Slot slot -> {
                        stack.setItemMeta(egg);
                        slot.set(stack);
                    }
                    default -> stack.setItemMeta(egg);
                }
            }
        }
    }
}
