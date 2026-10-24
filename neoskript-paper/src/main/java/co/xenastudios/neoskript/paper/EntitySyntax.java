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
import java.util.Map;
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

        // Resetting a spawner makes it a pig spawner, as a new one is; clearing it leaves it spawning nothing.
        // Trial spawners have a normal and an ominous configuration; both are set.
        java.util.function.BiConsumer<org.bukkit.block.Block, org.bukkit.entity.EntityType> spawnerType = (block, type) -> {
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.CreatureSpawner spawner) {
                spawner.setSpawnedType(type);
                spawner.update();
            } else if (state instanceof org.bukkit.block.TrialSpawner trial) {
                trial.getNormalConfiguration().setSpawnedType(type);
                trial.getOminousConfiguration().setSpawnedType(type);
                trial.update();
            }
        };
        registrar.expression("ExprSpawnerType", "entitytype", info -> new SettableProperty<>(org.bukkit.block.Block.class,
                EntityKind.class, block -> {
                    org.bukkit.entity.EntityType type = block.getState() instanceof org.bukkit.block.CreatureSpawner spawner
                            ? spawner.getSpawnedType() : block.getState() instanceof org.bukkit.block.TrialSpawner trial
                                    ? trial.getNormalConfiguration().getSpawnedType() : null;
                    return type == null ? null : EntityKind.of(type);
                },
                (block, kind) -> {
                    if (kind.type() != null) {
                        spawnerType.accept(block, kind.type());
                    }
                }).resettingTo((java.util.function.Consumer<Object>) block -> spawnerType.accept((org.bukkit.block.Block) block,
                        org.bukkit.entity.EntityType.PIG)).deleting(block -> spawnerType.accept(block, null)),
                "[the] (spawner|entity|creature) type[s] of %blocks%", "%blocks%'[s] (spawner|entity|creature) type[s]");

        registrar.expression("ExprLastDroppedItem", "entity", info -> (context, args) -> LAST_DROPPED.get(),
                "[the] [last[ly]] dropped item");

        registrar.condition("CondIsBetween", info -> {
            boolean negated = info.hasTag("not");
            boolean[] all = {info.and(0), info.and(1), info.and(2)};
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                Object[] lows = co.xenastudios.neoskript.runtime.Values.array(args[1]);
                Object[] highs = co.xenastudios.neoskript.runtime.Values.array(args[2]);
                if (values.length == 0 || lows.length == 0 || highs.length == 0) {
                    return false;
                }
                // As in Skript, each value against each bound, by whether the lists are 'and' or 'or' lists.
                return check(values, all[0], value -> check(lows, all[1], low -> check(highs, all[2], high -> {
                    Integer first = co.xenastudios.neoskript.runtime.Comparison.order(value, low);
                    Integer second = co.xenastudios.neoskript.runtime.Comparison.order(value, high);
                    if (value instanceof co.xenastudios.neoskript.runtime.TimeOfDay
                            && low instanceof co.xenastudios.neoskript.runtime.TimeOfDay
                            && high instanceof co.xenastudios.neoskript.runtime.TimeOfDay && first != null && second != null) {
                        // Times go round: 23:00 is between 20:00 and 01:00, but not between 01:00 and 20:00.
                        Integer bounds = co.xenastudios.neoskript.runtime.Comparison.order(low, high);
                        return bounds != null && bounds >= 0 ? first >= 0 || second <= 0 : first >= 0 && second <= 0;
                    }
                    // Between, whichever bound is the lower one.
                    return first != null && second != null
                            && ((first >= 0 && second <= 0) || (first <= 0 && second >= 0));
                }))) != negated;
            };
        }, "%objects% (is|are)[not:(n't| not)] between %objects% and %objects%");

        registerSettableConditions(registrar);
        registrar.effect("EffIncendiary", info -> {
            boolean fire = !info.hasTag("not");
            boolean event = info.pattern() == 2;
            return (context, args) -> {
                if (event) {
                    if (context.event() instanceof org.bukkit.event.entity.ExplosionPrimeEvent prime) {
                        prime.setFire(fire);
                    }
                    return;
                }
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof org.bukkit.entity.Explosive explosive) {
                        explosive.setIsIncendiary(fire);
                    }
                }
            };
        }, "make %entities% [not:not] incendiary", "make %entities%'[s] explosion [not:not] (incendiary|fiery)",
                "make [the] [event(-| )]explosion [not:not] (incendiary|fiery)");
        registrar.expression("ExprDomestication", "integer", info -> new Domestication(info.hasTag("max")),
                co.xenastudios.neoskript.api.registration.Patterns.property("[max:max[imum]] domestication level",
                        "livingentities"));
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
                Entity.class, Number.class, entity -> switch (entity) {
                    case org.bukkit.entity.Explosive explosive -> (double) explosive.getYield();
                    case org.bukkit.entity.Creeper creeper -> (double) creeper.getExplosionRadius();
                    case org.bukkit.entity.Ghast ghast -> (double) ghast.getExplosionPower();
                    default -> null;
                }, (entity, yield) -> {
                    double value = yield.doubleValue();
                    if (Double.isNaN(value)) {
                        return;
                    }
                    switch (entity) {
                        case org.bukkit.entity.Explosive explosive -> explosive.setYield((float) Math.max(0, value));
                        case org.bukkit.entity.Creeper creeper -> creeper.setExplosionRadius((int) Math.max(0, Math.min(value, Integer.MAX_VALUE)));
                        // A ghast's fireballs are between 0 and 127 strong.
                        case org.bukkit.entity.Ghast ghast -> ghast.setExplosionPower((int) Math.max(0, Math.min(value, 127)));
                        default -> {}
                    }
                });
        // Only animals fall in love; anything else's love time is none at all.
        SettableProperty.register(registrar, "ExprLoveTime", "timespan", "love[d] time", "livingentities",
                LivingEntity.class, co.xenastudios.neoskript.runtime.Timespan.class,
                entity -> new co.xenastudios.neoskript.runtime.Timespan(entity instanceof org.bukkit.entity.Animals animal
                        ? animal.getLoveModeTicks() * 50L : 0),
                (entity, time) -> {
                    if (entity instanceof org.bukkit.entity.Animals animal) {
                        animal.setLoveModeTicks((int) Math.max(0, Math.min(Integer.MAX_VALUE, time.ticks())));
                    }
                }, new co.xenastudios.neoskript.runtime.Timespan(0));
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
                for (Object value : (Object[]) args[0]) {
                    // Experience drops as an orb: 'drop 10 xp at {_spawn}'.
                    if (value instanceof Experience experience) {
                        location.getWorld().spawn(location, org.bukkit.entity.ExperienceOrb.class,
                                orb -> orb.setExperience(experience.xp()));
                    }
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
        }, "drop %itemtypes/experiences% [(at|on) %locations%] [still:without velocity]");

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
                            // Nothing can ride itself.
                            if (args[1] instanceof Entity vehicle && vehicle != entity) {
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
        // The player who tamed an animal; set to another, or cleared to make it wild.
        registrar.expression("ExprEntityOwner", "offlineplayer", info -> new SettableProperty<>(org.bukkit.entity.Tameable.class,
                org.bukkit.OfflinePlayer.class, tameable -> tameable.getOwner() instanceof org.bukkit.OfflinePlayer player
                        ? player : null, (tameable, player) -> tameable.setOwner(player))
                .deleting(tameable -> tameable.setOwner(null)),
                "[the] (owner|tamer) of %livingentities%", "%livingentities%'[s] (owner|tamer)");
        // A slime's size counts from 0 (Minecraft's 1) to 126; a phantom's from 0 to 64. Not-a-number and
        // infinite sizes are ignored.
        registrar.expression("ExprEntitySize", "integer", info -> new SettableProperty<>(LivingEntity.class, Number.class,
                entity -> entity instanceof org.bukkit.entity.Slime slime ? (Object) (long) (slime.getSize() - 1)
                        : entity instanceof org.bukkit.entity.Phantom phantom ? (Object) (long) phantom.getSize() : null,
                (entity, size) -> {
                    double value = size.doubleValue();
                    if (!Double.isFinite(value)) {
                        return;
                    }
                    if (entity instanceof org.bukkit.entity.Slime slime) {
                        slime.setSize((int) Math.max(0, Math.min(126, value)) + 1);
                    } else if (entity instanceof org.bukkit.entity.Phantom phantom) {
                        phantom.setSize((int) Math.max(0, Math.min(64, value)));
                    }
                }).resettingTo(0L), "[the] entity size of %livingentities%", "%livingentities%'[s] entity size");
        registrar.effect("EffCustomName", info -> {
            boolean show = info.hasTag("show");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Entity entity) {
                        entity.setCustomNameVisible(show);
                    }
                }
            };
        }, "(show:show|hide) [the] custom name[s] of %entities%", "(show:show|hide) %entities%'[s] custom name[s]");
        // Panda genes: 'the main gene of {_panda} is lazy'.
        Map<String, org.bukkit.entity.Panda.Gene> genes = new java.util.LinkedHashMap<>();
        for (org.bukkit.entity.Panda.Gene gene : org.bukkit.entity.Panda.Gene.values()) {
            genes.put(gene.name().toLowerCase(java.util.Locale.ROOT), gene);
        }
        registrar.namedType("gene", "gene", org.bukkit.entity.Panda.Gene.class, genes);
        registrar.text(org.bukkit.entity.Panda.Gene.class, gene -> gene.name().toLowerCase(java.util.Locale.ROOT));
        registrar.expression("ExprPandaGene", "gene", info -> {
            boolean hidden = info.hasTag("hidden");
            return new SettableProperty<>(org.bukkit.entity.Panda.class, org.bukkit.entity.Panda.Gene.class,
                    panda -> hidden ? panda.getHiddenGene() : panda.getMainGene(), (panda, gene) -> {
                        if (hidden) {
                            panda.setHiddenGene(gene);
                        } else {
                            panda.setMainGene(gene);
                        }
                    });
        }, "[the] (main|hidden:hidden) gene[s] of %livingentities%", "%livingentities%'[s] (main|hidden:hidden) gene[s]");

        // Allays: how long until they can duplicate again, whether they can, and making them.
        registrar.expression("ExprDuplicateCooldown", "timespan", info -> new SettableProperty<>(org.bukkit.entity.Allay.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                allay -> new co.xenastudios.neoskript.runtime.Timespan(allay.getDuplicationCooldown() * 50),
                (allay, time) -> allay.setDuplicationCooldown(Math.max(0, time.ticks()))),
                "[the] duplicat(e|ion) cooldown [time] of %livingentities%", "%livingentities%'[s] duplicat(e|ion) cooldown [time]");
        registrar.effect("EffAllayDuplicate", info -> (context, args) -> {
            for (Object value : (Object[]) args[0]) {
                if (value instanceof org.bukkit.entity.Allay allay) {
                    allay.duplicateAllay();
                }
            }
        }, "make %livingentities% (duplicate|clone)");

        registrar.effect("EffPersistent", info -> {
            boolean persist = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Entity entity) {
                        entity.setPersistent(persist);
                    } else if (value instanceof org.bukkit.block.Block block
                            && block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                        leaves.setPersistent(persist);
                        block.setBlockData(leaves);
                    }
                }
            };
        }, "(make|force) %entities/blocks% [to] persist[ent]", "prevent %entities/blocks% from persisting");
        registrar.effect("EffCommandBlockConditional", info -> {
            boolean conditional = !info.hasTag("un");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof org.bukkit.block.Block block
                            && block.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock command) {
                        command.setConditional(conditional);
                        block.setBlockData(command);
                    }
                }
            };
        }, "make command block[s] %blocks% [un:un]conditional");
        // The command a command block or command block minecart runs; none until it's given one.
        registrar.expression("ExprCommandBlockCommand", "string", info -> new SettableProperty<>(Object.class, String.class,
                owner -> {
                    String command = owner instanceof org.bukkit.block.Block block
                            && block.getState() instanceof org.bukkit.block.CommandBlock state ? state.getCommand()
                            : owner instanceof org.bukkit.entity.minecart.CommandMinecart minecart ? minecart.getCommand() : null;
                    return command == null || command.isEmpty() ? null : command;
                }, (owner, command) -> {
                    if (owner instanceof org.bukkit.block.Block block && block.getState() instanceof org.bukkit.block.CommandBlock state) {
                        state.setCommand(command);
                        state.update(true, false);
                    } else if (owner instanceof org.bukkit.entity.minecart.CommandMinecart minecart) {
                        minecart.setCommand(command);
                    }
                }).resettingTo("").deleting(owner -> {
                    if (owner instanceof org.bukkit.block.Block block && block.getState() instanceof org.bukkit.block.CommandBlock state) {
                        state.setCommand("");
                        state.update(true, false);
                    } else if (owner instanceof org.bukkit.entity.minecart.CommandMinecart minecart) {
                        minecart.setCommand("");
                    }
                }), "[the] [command[ ]block] command of %blocks/entities%", "%blocks/entities%'[s] [command[ ]block] command");
        registrar.effect("EffExplodeCreeper", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof org.bukkit.entity.Creeper creeper) {
                        switch (pattern) {
                            case 0 -> creeper.explode();
                            case 1 -> creeper.ignite();
                            default -> creeper.setIgnited(false);
                        }
                    }
                }
            };
        }, "[instantly|immediately] explode [creeper[s]] %livingentities%",
                "start (ignition|explosion) [process] of %livingentities%", "stop (ignition|explosion) [process] of %livingentities%");
        // 'offline player from {_uuid}', 'entity from {_uuid}': what a UUID belongs to, if it's there.
        registrar.expression("ExprFromUUID", "object", info -> {
            String kind = info.hasTag("offline") ? "offline" : info.hasTag("player") ? "player"
                    : info.hasTag("world") ? "world" : "entity";
            return (context, args) -> {
                List<Object> found = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    java.util.UUID uuid = value instanceof java.util.UUID id ? id : null;
                    if (uuid == null) {
                        continue;
                    }
                    Object thing = switch (kind) {
                        case "offline" -> org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        case "player" -> org.bukkit.Bukkit.getPlayer(uuid);
                        case "world" -> org.bukkit.Bukkit.getWorld(uuid);
                        default -> org.bukkit.Bukkit.getEntity(uuid);
                    };
                    if (thing != null) {
                        found.add(thing);
                    }
                }
                return found.size() == 1 ? found.getFirst() : found.toArray();
            };
        }, "(offline:offline player|player:player|world:world|entit(y|ies))[s] from %uuids%");
        // How angry a warden is at something, from 0 to 150.
        registrar.expression("ExprWardenEntityAnger", "integer", info -> new WardenAnger(),
"[the] anger level[s] of %livingentities% (towards|against) %entities%",
                "%livingentities%'[s] anger level[s] (towards|against) %entities%");
        // Plugin metadata on entities, blocks and worlds: 'metadata "x" of {_e}'. Numbers can be added to.
        registrar.expression("ExprMetadata", "object", info -> new Metadata(), "metadata [(value|tag)[s]] %texts% of %entities/blocks/worlds%",
                "%entities/blocks/worlds%'[s] metadata [(value|tag)[s]] %texts%");
        // The entity a warden is angriest at; setting it makes the warden as angry as it gets at that one.
        registrar.expression("ExprWardenAngryAt", "livingentity", info -> new SettableProperty<>(org.bukkit.entity.Warden.class,
                LivingEntity.class, org.bukkit.entity.Warden::getEntityAngryAt, (warden, target) -> warden.setAnger(target, 150)),
                "[the] most angered entity of %livingentities%", "%livingentities%'[s] most angered entity");
        // Why an entity was damaged, by Skript's names: 'burning', 'dragon's breath', 'potion'.
        Map<String, org.bukkit.event.entity.EntityDamageEvent.DamageCause> causes = new java.util.LinkedHashMap<>();
        for (org.bukkit.event.entity.EntityDamageEvent.DamageCause cause : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
            causes.put(cause.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), cause);
        }
        Map<String, String> causeNames = Map.of("burning", "FIRE_TICK", "dragon's breath", "DRAGON_BREATH",
                "potion", "MAGIC", "sweep attack", "ENTITY_SWEEP_ATTACK", "wither effect", "WITHER",
                "attack", "ENTITY_ATTACK", "explosion", "BLOCK_EXPLOSION");
        causeNames.forEach((name, cause) -> {
            try {
                causes.putIfAbsent(name, org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(cause));
            } catch (IllegalArgumentException missing) {
                // Not on this version.
            }
        });
        registrar.namedType("damagecause", "damage cause", org.bukkit.event.entity.EntityDamageEvent.DamageCause.class, causes);
        registrar.text(org.bukkit.event.entity.EntityDamageEvent.DamageCause.class, cause -> {
            for (Map.Entry<String, String> named : causeNames.entrySet()) {
                if (named.getValue().equals(cause.name()) && !named.getKey().equals("attack") && !named.getKey().equals("explosion")
                        && !named.getKey().equals("wither effect")) {
                    return named.getKey();
                }
            }
            return cause.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        });
        condition(registrar, "CondIsBurning", "entities", "(burning|ignited|on fire)", Entity.class,
                entity -> entity.getFireTicks() > 0);
        registrar.expression("ExprPassenger", "entity", info -> new Passengers(),
                "[all [[of] the]|the] passenger[s] of %entities%", "%entities%'[s] passenger[s]");
        // 'is riding', 'is riding a villager', 'is riding {_vehicle}'.
        registrar.condition("CondIsRiding", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] vehicles = args[1] == null ? null : (Object[]) args[1];
                Object[] riders = (Object[]) args[0];
                if (riders.length == 0) {
                    return negated;
                }
                return co.xenastudios.neoskript.api.element.Condition.check(riders, and, rider -> {
                    Entity vehicle = rider instanceof Entity entity ? entity.getVehicle() : null;
                    boolean riding = vehicle != null;
                    if (riding && vehicles != null) {
                        riding = false;
                        for (Object wanted : vehicles) {
                            riding |= wanted instanceof EntityKind kind ? kind.matches(vehicle)
                                    : wanted instanceof co.xenastudios.neoskript.runtime.TypeValue type
                                            ? type.type().isInstance(vehicle) : vehicle.equals(wanted);
                        }
                    }
                    return riding != negated;
                });
            };
        }, "%entities% (is|are) riding [%-entitytypes/entities%]",
                "%entities% (isn't|is not|aren't|are not) riding [%-entitytypes/entities%]");
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
                (entity, time) -> entity.setFreezeTicks((int) Math.max(0, Math.min(Integer.MAX_VALUE, time.ticks()))));
        SettableProperty.register(registrar, "ExprPortalCooldown", "timespan", "portal cooldown", "entities", Entity.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                entity -> new co.xenastudios.neoskript.runtime.Timespan(entity.getPortalCooldown() * 50L),
                (entity, time) -> entity.setPortalCooldown((int) Math.max(0, Math.min(Integer.MAX_VALUE, time.ticks()))),
                // Minecraft's own cooldown after using a portal: 15 seconds.
                new co.xenastudios.neoskript.runtime.Timespan(15000));
        SettableProperty.register(registrar, "ExprNoDamageTicks", "integer", LivingEntity.class, Number.class,
                living -> (long) living.getNoDamageTicks(), (living, ticks) -> living.setNoDamageTicks(Math.max(0, ticks.intValue())),
                "[the] (invulnerability|invincibility|no damage) tick[s] [of %livingentities%]",
                "%livingentities%'[s] (invulnerability|invincibility|no damage) tick[s]");
        SettableProperty.register(registrar, "ExprArrowsStuck", "integer", LivingEntity.class, Number.class,
                living -> (long) living.getArrowsInBody(), (living, count) -> living.setArrowsInBody(Math.max(0, count.intValue())),
                "[number of] arrow[s] stuck in %livingentities%");
        registrar.expression("ExprVillagerLevel", "integer", info -> new SettableVillager(info.hasTag("experience")),
                "[the] villager (level|experience:experience) of %livingentities%",
                "%livingentities%'[s] villager (level|experience:experience)");
        registrar.expression("ExprInteractionDimensions", "number", info -> {
            boolean height = info.hasTag("height");
            return new SettableProperty<>(org.bukkit.entity.Interaction.class, Number.class,
                    interaction -> (double) (height ? interaction.getInteractionHeight() : interaction.getInteractionWidth()),
                    (interaction, value) -> {
                        // Never smaller than nothing.
                        if (height) {
                            interaction.setInteractionHeight(Math.max(value.floatValue(), 0));
                        } else {
                            interaction.setInteractionWidth(Math.max(value.floatValue(), 0));
                        }
                    }).resettingTo(1.0);
        },
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
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class}
                    : mode == Mode.RESET ? new Class<?>[0] : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            // Resetting goes back to a new villager's: level 1, no experience.
            int by = mode == Mode.RESET ? (experience ? 0 : 1) : delta.length > 0 && delta[0] instanceof Number n ? n.intValue() : 0;
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
            if (total) {
                return null;
            }
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class}
                    : mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            double by = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
            for (Object owner : owners(arguments[1])) {
                if (owner instanceof org.bukkit.attribute.Attributable attributable
                        && arguments[0] instanceof org.bukkit.attribute.Attribute attribute) {
                    org.bukkit.attribute.AttributeInstance instance = attributable.getAttribute(attribute);
                    if (instance != null) {
                        double current = instance.getBaseValue();
                        instance.setBaseValue(switch (mode) {
                            case ADD -> current + by;
                            case REMOVE -> current - by;
                            case DELETE -> 0;
                            case RESET -> instance.getDefaultValue();
                            default -> by;
                        });
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
        // Creative and spectator players can't be hurt, so those game modes are invulnerable.
        condition(registrar, "CondIsInvulnerable", "entities/gamemodes", "(invulnerable|invincible)", Object.class,
                value -> value instanceof Entity entity ? entity.isInvulnerable()
                        : value == org.bukkit.GameMode.CREATIVE || value == org.bukkit.GameMode.SPECTATOR, (value, on) -> {
                    if (value instanceof Entity entity) {
                        entity.setInvulnerable(on);
                    }
                });
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
        condition(registrar, "CondStriderIsShivering", "livingentities", "shivering", org.bukkit.entity.Strider.class,
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
        // Horses' saddles too, as well as pigs' and striders'.
        condition(registrar, "CondIsSaddled", "livingentities", "[properly] saddled", LivingEntity.class,
                EntitySyntax::saddled, (living, saddled) -> equip(living, new org.bukkit.inventory.ItemStack(org.bukkit.Material.SADDLE),
                        !saddled));
        condition(registrar, "CondIsInvisible", "livingentities", "(invisible|flip:visible)", LivingEntity.class,
                LivingEntity::isInvisible, LivingEntity::setInvisible);
        condition(registrar, "CondIsLeftHanded", "livingentities", "(left|flip:right)( |-)handed", org.bukkit.entity.Mob.class,
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
        condition(registrar, "CondIsFrozen", "entities", "frozen", Entity.class, Entity::isFrozen,
                (entity, frozen) -> entity.setFreezeTicks(frozen ? entity.getMaxFreezeTicks() : 0));
        registrar.effect("EffPvP", info -> {
            boolean enable = info.pattern() == 0;
            return (context, args) -> {
                for (Object world : owners(args[0])) {
                    if (world instanceof org.bukkit.World w) {
                        w.setPVP(enable);
                    }
                }
            };
        }, "enable PvP [in %worlds%]", "disable PvP [in %worlds%]");
        registrar.effect("EffTame", info -> {
            boolean tame = !info.hasTag("un");
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Tameable tameable) {
                        tameable.setTamed(tame);
                    }
                }
            };
        }, "[un:un]tame %entities%");
        registrar.effect("EffTooltip", info -> {
            boolean hide = info.hasTag("hide");
            boolean additional = info.hasTag("additional");
            Tooltip tooltip = new Tooltip(true, additional);
            return (context, args) -> tooltip.set(context, args, !hide);
        }, "(show|hide:hide) [the] [entire|additional:additional] tool[ ]tip[s] of %itemtypes%",
                "(show|hide:hide) %itemtypes%'[s] [entire|additional:additional] tool[ ]tip[s]");
        registrar.effect("EffStriderShivering", info -> {
            boolean start = !info.hasTag("stop");
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Strider strider) {
                        strider.setShivering(start);
                    }
                }
            };
        }, "make %livingentities% (start|stop:stop) shivering");
        registrar.effect("EffPlayingDead", info -> {
            boolean start = !info.hasTag("stop");
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Axolotl axolotl) {
                        axolotl.setPlayingDead(start);
                    }
                }
            };
        }, "make %livingentities% (start|stop:stop) (playing|pretending to be) dead");
        registrar.effect("EffWakeupSleep", info -> {
            boolean sleep = info.pattern() == 0;
            return (context, args) -> {
                Location at = sleep && args.length > 1 ? switch (args[1]) {
                    case Location location -> location;
                    case org.bukkit.block.Block block -> block.getLocation();
                    case null, default -> null;
                } : null;
                for (Object entity : owners(args[0])) {
                    switch (entity) {
                        case org.bukkit.entity.Fox fox -> fox.setSleeping(sleep);
                        case org.bukkit.entity.Bat bat -> bat.setAwake(!sleep);
                        case org.bukkit.entity.Villager villager -> {
                            if (!sleep) {
                                villager.wakeup();
                            } else if (at != null) {
                                villager.sleep(at);
                            }
                        }
                        case org.bukkit.entity.HumanEntity human when !sleep -> human.wakeup(false);
                        default -> {}
                    }
                }
            };
        }, "make %livingentities% (start sleeping|[go to] sleep) [at %location/block%]",
                "make %livingentities% (stop sleeping|wake up)");
        conditionPatterns(registrar, "CondIsTicking", Entity.class, Entity::isTicking, null,
                "%entities% (is|are) ticking", "%entities% (isn't|is not|aren't|are not) ticking");
        conditionPatterns(registrar, "CondIsEdible", ItemType.class, item -> item.material().isEdible(), null,
                "%itemtypes% (is|are) edible", "%itemtypes% (isn't|is not|aren't|are not) edible");
        conditionPatterns(registrar, "CondIsFlammable", ItemType.class, item -> item.material().isFlammable(), null,
                "%itemtypes% (is|are) flammable", "%itemtypes% (isn't|is not|aren't|are not) flammable");
        conditionPatterns(registrar, "CondIsSolid", ItemType.class, item -> item.material().isSolid(), null,
                "%itemtypes% (is|are) solid", "%itemtypes% (isn't|is not|aren't|are not) solid");
        conditionPatterns(registrar, "CondIsTransparent", ItemType.class, item -> item.material().isTransparent(), null,
                "%itemtypes% (is|are) transparent", "%itemtypes% (isn't|is not|aren't|are not) transparent");
        conditionPatterns(registrar, "CondIsForcedToGlint", ItemType.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().hasEnchantmentGlintOverride()
                        && item.stack().getItemMeta().getEnchantmentGlintOverride(),
                // Made not to glint, the override is off, which is still an override.
                (item, value) -> item.editMeta(meta -> meta.setEnchantmentGlintOverride(value)),
                "%itemtypes% (is|are) forced to glint", "%itemtypes% (is|are) forced to not glint",
                "%itemtypes% (isn't|is not|aren't|are not) forced to glint");
        registrar.effect("EffForceEnchantmentGlint", info -> {
            Boolean glint = info.pattern() == 0 ? Boolean.TRUE : info.pattern() == 1 ? Boolean.FALSE : null;
            return (context, args) -> {
                for (Object item : owners(args[0])) {
                    if (item instanceof ItemType type) {
                        type.editMeta(meta -> meta.setEnchantmentGlintOverride(glint));
                    }
                }
            };
        }, "make %itemtypes% glint", "make %itemtypes% not glint", "make %itemtypes% glint normally");
        registrar.expression("ExprItemWithGlint", "itemtype", info -> {
            boolean with = info.pattern() == 0;
            return (context, args) -> mapItems(args[0], item -> item.withMeta(meta -> meta.setEnchantmentGlintOverride(with)));
        }, "%itemtypes% with enchantment glint", "%itemtypes% without enchantment glint");
        registrar.expression("ExprItemWithFireResistance", "itemtype", info -> {
            boolean with = info.pattern() != 1;
            return (context, args) -> mapItems(args[0], item -> item.withMeta(meta -> meta.setFireResistant(with)));
        }, "%itemtypes% with fire resistance", "%itemtypes% without fire resistance", "fire resistant %itemtypes%");
        registrar.expression("ExprItemWithTooltip", "itemtype", info -> {
            boolean hide = info.pattern() == 1;
            boolean additional = info.hasTag("additional");
            return (context, args) -> mapItems(args[0], item -> item.withMeta(meta -> {
                if (additional) {
                    if (hide) {
                        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    } else {
                        meta.removeItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    }
                } else {
                    meta.setHideTooltip(hide);
                }
            }));
        }, "%itemtypes% with [entire|additional:additional] tool[ ]tip[s]",
                "%itemtypes% without [entire|additional:additional] tool[ ]tip[s]");
        // Villager types and professions, by name; looked up when used, as they're registry entries.
        Map<String, Keyed> villagerTypes = new java.util.LinkedHashMap<>();
        for (String type : List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga")) {
            villagerTypes.put(type, new Keyed("villager type", type));
        }
        registrar.namedType("villagertype", "villager type", Keyed.class, villagerTypes);
        Map<String, Keyed> professions = new java.util.LinkedHashMap<>();
        for (String profession : List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
                "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith")) {
            professions.put(profession + " profession", new Keyed("villager profession", profession));
        }
        professions.put("no profession", new Keyed("villager profession", "none"));
        registrar.namedType("villagerprofession", "villager profession", Keyed.class, professions);
        registrar.expression("ExprVillagerType", "villagertype", info -> new SettableProperty<>(org.bukkit.entity.Villager.class,
                Keyed.class, villager -> new Keyed("villager type", villager.getVillagerType().getKey().getKey()),
                (villager, type) -> {
                    org.bukkit.entity.Villager.Type found = org.bukkit.Registry.VILLAGER_TYPE.get(
                            org.bukkit.NamespacedKey.minecraft(type.key()));
                    if (found != null) {
                        villager.setVillagerType(found);
                    }
                }), "[the] villager type[s] of %livingentities%", "%livingentities%'[s] villager type[s]");
        registrar.expression("ExprVillagerProfession", "villagerprofession", info -> new Profession(),
                "[the] villager profession[s] of %livingentities%", "%livingentities%'[s] villager profession[s]");
        registrar.effect("EffZombify", info -> {
            boolean zombify = info.pattern() == 0;
            return co.xenastudios.neoskript.api.element.Update.of(0, (context, args) -> {
                Object[] entities = owners(args[0]);
                Object[] changed = new Object[entities.length];
                for (int i = 0; i < entities.length; i++) {
                    Object entity = entities[i];
                    if (zombify && entity instanceof org.bukkit.entity.Villager villager) {
                        // The villager becomes a new entity, which takes its place in the variable.
                        org.bukkit.entity.ZombieVillager zombie = villager.zombify();
                        changed[i] = zombie == null ? entity : zombie;
                    } else {
                        if (!zombify && entity instanceof org.bukkit.entity.ZombieVillager zombie) {
                            zombie.setConversionTime(1);
                        }
                        changed[i] = entity;
                    }
                }
                return changed;
            });
        }, "zombify %~objects%", "unzombify %~objects%");
        registrar.expression("ExprWhitelist", "offlineplayer", info -> new WhitelistedPlayers(), "[the] white[ ]list[ed players]");
        registrar.expression("ExprRemainingAir", "timespan", info -> new SettableProperty<>(LivingEntity.class,
                co.xenastudios.neoskript.runtime.Timespan.class,
                living -> new co.xenastudios.neoskript.runtime.Timespan(living.getRemainingAir() * 50L),
                (living, time) -> living.setRemainingAir((int) Math.max(0, Math.min(Integer.MAX_VALUE, time.ticks()))))
                // Resetting fills the lungs: 15 seconds.
                .resettingTo(new co.xenastudios.neoskript.runtime.Timespan(15000)),
                "[the] remaining air of %livingentities%", "%livingentities%'[s] remaining air");
        // 'equip X with Y': each item in its place, as worn: armour, a horse's armour or saddle, a pig's saddle.
        registrar.effect("EffEquip", info -> {
            boolean unequip = info.pattern() >= 2;
            boolean allArmour = info.pattern() == 2;
            return (context, args) -> {
                Object[] entities = owners(args[unequip && !allArmour ? 1 : 0]);
                Object[] items = allArmour ? new Object[0] : owners(args[unequip ? 0 : 1]);
                for (Object entity : entities) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    if (allArmour) {
                        org.bukkit.inventory.EntityEquipment equipment = living.getEquipment();
                        if (equipment != null) {
                            equipment.setArmorContents(new org.bukkit.inventory.ItemStack[4]);
                        }
                        continue;
                    }
                    for (Object value : items) {
                        if (value instanceof ItemType item) {
                            equip(living, item.stack(), unequip);
                        }
                    }
                }
            };
            // 'unequip X's armour' is tried first: their armour slots aren't items to take off.
        }, "equip [%livingentities%] with %itemtypes%", "make %livingentities% wear %itemtypes%",
                "unequip %livingentities%'[s] (armo[u]r|equipment)", "unequip %itemtypes% [from %livingentities%]");
        registrar.condition("CondIsWearing", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] entities = owners(args[0]);
                if (entities.length == 0) {
                    return negated;
                }
                for (Object entity : entities) {
                    boolean wears = entity instanceof LivingEntity living && java.util.Arrays.stream(owners(args[1]))
                            .allMatch(item -> item instanceof ItemType type && wearing(living, type));
                    if (wears == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%livingentities% (is|are) wearing %itemtypes%", "%livingentities% (isn't|is not|aren't|are not) wearing %itemtypes%");
        EntitySyntax.conditionPatterns(registrar, "CondHasSaddle", LivingEntity.class, EntitySyntax::saddled, null,
                "%livingentities% (has|have) [a] saddle[s]", "%livingentities% (doesn't|does not|do not|don't) have [a] saddle[s]");
        registrar.effect("EffEnforceWhitelist", info -> {
            boolean enforce = !info.hasTag("un");
            return (context, args) -> org.bukkit.Bukkit.setWhitelistEnforced(enforce);
        }, "[un:un]enforce [the] [server] white[ ]list");
        registrar.effect("EffSilence", info -> {
            boolean silent = info.pattern() == 0;
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof Entity e) {
                        e.setSilent(silent);
                    }
                }
            };
        }, "make %entities% silent", "make %entities% not silent");
        registrar.effect("EffBreedable", info -> {
            boolean breedable = info.pattern() == 0;
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Breedable animal) {
                        // As in Skript, something made breedable grows up so it can.
                        if (breedable && !animal.isAdult()) {
                            animal.setAdult();
                        }
                        animal.setBreed(breedable);
                    }
                }
            };
        }, "make %livingentities% breedable", "make %livingentities% (non(-| )|un)breedable");
        registrar.effect("EffAllowAging", info -> {
            boolean lock = info.pattern() == 0;
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Breedable animal) {
                        animal.setAgeLock(lock);
                    }
                }
            };
        }, "lock age of %livingentities%", "unlock age of %livingentities%");
        registrar.effect("EffCharge", info -> {
            boolean charge = !info.hasTag("un");
            return (context, args) -> {
                for (Object entity : owners(args[0])) {
                    if (entity instanceof org.bukkit.entity.Creeper creeper) {
                        creeper.setPowered(charge);
                    } else if (entity instanceof WitherSkull skull) {
                        skull.setCharged(charge);
                    }
                }
            };
        }, "make %entities% (charged|un:(uncharged|not charged))", "[un:un]charge %entities%");
        registrar.effect("EffFireResistant", info -> {
            boolean resistant = !info.hasTag("not");
            return (context, args) -> {
                for (Object item : owners(args[0])) {
                    if (item instanceof ItemType type) {
                        type.editMeta(meta -> meta.setFireResistant(resistant));
                    }
                }
            };
        }, "make %itemtypes% [not:not] (fire resistant|resistant to fire)");
        registrar.condition("CondIsWhitelisted", info -> new Whitelisted(info.pattern()),
                "[the] server (is|is in) (white[ ]list mode|white[ ]listed)",
                "[the] server (isn't|is not) (in white[ ]list mode|white[ ]listed)",
                "[the] server white[ ]list (is|is being) enforced", "[the] server white[ ]list (isn't|is not) enforced",
                "%offlineplayers% (is|are) white[ ]listed", "%offlineplayers% (isn't|is not|aren't|are not) white[ ]listed");
        condition(registrar, "CondIgnitionProcess", "livingentities",
                "(going to explode|in the (process|middle) of exploding)", org.bukkit.entity.Creeper.class,
                org.bukkit.entity.Creeper::isIgnited, org.bukkit.entity.Creeper::setIgnited);
        condition(registrar, "CondIsChargingFireball", "livingentities", "charging [a] fireball", org.bukkit.entity.Ghast.class,
                org.bukkit.entity.Ghast::isCharging, org.bukkit.entity.Ghast::setCharging);
        condition(registrar, "CondIsDancing", "livingentities", "dancing", org.bukkit.entity.Allay.class,
                org.bukkit.entity.Allay::isDancing, (allay, dancing) -> {
                    if (dancing) {
                        allay.startDancing();
                    } else {
                        allay.stopDancing();
                    }
                });
        condition(registrar, "CondIsDashing", "livingentities", "dashing", org.bukkit.entity.Camel.class,
                org.bukkit.entity.Camel::isDashing, org.bukkit.entity.Camel::setDashing);
        condition(registrar, "CondIsJumping", "livingentities", "jumping", LivingEntity.class, LivingEntity::isJumping,
                LivingEntity::setJumping);
        conditionPatterns(registrar, "CondEntityUnload", LivingEntity.class, LivingEntity::getRemoveWhenFarAway,
                LivingEntity::setRemoveWhenFarAway, "%livingentities% can despawn (on chunk unload|when far away)",
                "%livingentities% (can't|cannot|can not) despawn (on chunk unload|when far away)");
        conditionPatterns(registrar, "CondItemDespawn", org.bukkit.entity.Item.class, item -> !item.isUnlimitedLifetime(),
                (item, despawns) -> item.setUnlimitedLifetime(!despawns), "%entities% can (despawn naturally|naturally despawn)",
                "%entities% (can't|cannot|can not) (despawn naturally|naturally despawn)");
        conditionPatterns(registrar, "CondPvP", org.bukkit.World.class, org.bukkit.World::getPVP, org.bukkit.World::setPVP,
                "(is PvP|PvP is) enabled [in %worlds%]", "(is PvP|PvP is) disabled [in %worlds%]");
        registrar.condition("CondGoatHasHorns", info -> new GoatHorns(info.pattern() == 1,
                info.hasTag("left") ? 1 : info.hasTag("right") ? 2 : info.hasTag("both") ? 3 : 0),
                "%livingentities% (has|have) ((any|a) horn|left:[a] left horn|right:[a] right horn|both:both horns)",
                "%livingentities% (doesn't|does not|do not|don't) have ((any|a) horn|left:[a] left horn|right:[a] right horn|both:both horns)");
        registrar.condition("CondTooltip", info -> new Tooltip(info.pattern() % 2 == 1 != info.hasTag("shown"),
                info.hasTag("additional")),
                "[the] [entire|additional:additional] tool[ ]tip[s] of %itemtypes% (is|are) (shown:shown|hidden)",
                "[the] [entire|additional:additional] tool[ ]tip[s] of %itemtypes% (isn't|is not|aren't|are not) (shown:shown|hidden)",
                "%itemtypes%'[s] [entire|additional:additional] tool[ ]tip[s] (is|are) (shown:shown|hidden)",
                "%itemtypes%'[s] [entire|additional:additional] tool[ ]tip[s] (isn't|is not|aren't|are not) (shown:shown|hidden)");
        condition(registrar, "CondIsUnbreakable", "itemtypes", "(unbreakable|flip:breakable)", ItemType.class,
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
            // Something the property doesn't apply to doesn't have it, so 'isn't' holds for it, as in Skript.
            boolean and = info.and(0);
            // Nothing has no properties: '{_none} is not invulnerable' holds, as does '{_none} is not breakable'.
            boolean saysNot = info.pattern() >= half;
            Predicate<Object[]> holds = values -> {
                if (values.length == 0) {
                    return saysNot;
                }
                return co.xenastudios.neoskript.api.element.Condition.check(values, and,
                        value -> (type.isInstance(value) && test.test(type.cast(value))) != negated);
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

    /** Whether the server is in whitelist mode, its whitelist is enforced, or players are whitelisted; settable. */
    private record Whitelisted(int pattern) implements co.xenastudios.neoskript.api.element.SettableCondition {
        @Override
        public boolean check(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
            boolean negated = pattern % 2 == 1;
            if (pattern < 4) {
                return (pattern >= 2 ? org.bukkit.Bukkit.isWhitelistEnforced() : org.bukkit.Bukkit.hasWhitelist()) != negated;
            }
            Object[] players = owners(args[0]);
            for (Object player : players) {
                if (!(player instanceof org.bukkit.OfflinePlayer offline) || offline.isWhitelisted() == negated) {
                    return false;
                }
            }
            return players.length > 0;
        }

        @Override
        public void set(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, boolean value) {
            boolean to = value != (pattern % 2 == 1);
            if (pattern >= 2 && pattern < 4) {
                org.bukkit.Bukkit.setWhitelistEnforced(to);
            } else if (pattern < 2) {
                org.bukkit.Bukkit.setWhitelist(to);
            } else {
                for (Object player : owners(args[0])) {
                    if (player instanceof org.bukkit.OfflinePlayer offline) {
                        offline.setWhitelisted(to);
                    }
                }
            }
        }
    }

    /** Whether goats have a horn: any (0), the left (1), the right (2) or both (3); settable. */
    private record GoatHorns(boolean negated, int which) implements co.xenastudios.neoskript.api.element.SettableCondition {
        @Override
        public boolean check(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
            Object[] goats = owners(args[0]);
            for (Object value : goats) {
                if (!(value instanceof org.bukkit.entity.Goat goat)) {
                    return false;
                }
                boolean has = switch (which) {
                    case 1 -> goat.hasLeftHorn();
                    case 2 -> goat.hasRightHorn();
                    case 3 -> goat.hasLeftHorn() && goat.hasRightHorn();
                    default -> goat.hasLeftHorn() || goat.hasRightHorn();
                };
                if (has == negated) {
                    return false;
                }
            }
            return goats.length > 0;
        }

        @Override
        public void set(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, boolean value) {
            boolean to = value != negated;
            for (Object goat : owners(args[0])) {
                if (goat instanceof org.bukkit.entity.Goat g) {
                    if (which != 2) {
                        g.setLeftHorn(to);
                    }
                    if (which != 1) {
                        g.setRightHorn(to);
                    }
                }
            }
        }
    }

    /** Whether items' entire (or additional) tooltip is hidden; settable. */
    private record Tooltip(boolean shown, boolean additional) implements co.xenastudios.neoskript.api.element.SettableCondition {
        @Override
        public boolean check(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
            Object[] items = owners(args[0]);
            for (Object value : items) {
                if (!(value instanceof ItemType item) || hidden(item.stack().getItemMeta()) == shown) {
                    return false;
                }
            }
            return items.length > 0;
        }

        private boolean hidden(org.bukkit.inventory.meta.ItemMeta meta) {
            if (meta == null) {
                return false;
            }
            return additional ? meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP) : meta.isHideTooltip();
        }

        @Override
        public void set(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, boolean value) {
            boolean hide = value != shown;
            for (Object owner : owners(args[0])) {
                if (owner instanceof ItemType item) {
                    item.editMeta(meta -> {
                        if (!additional) {
                            meta.setHideTooltip(hide);
                        } else if (hide) {
                            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                        } else {
                            meta.removeItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                        }
                    });
                }
            }
        }
    }

    /** Each item through {@code change}; one value alone if there's one. */
    private static Object mapItems(Object items, java.util.function.Function<ItemType, Object> change) {
        List<Object> changed = new ArrayList<>();
        for (Object value : owners(items)) {
            if (value instanceof ItemType item) {
                changed.add(change.apply(item));
            }
        }
        return changed.size() == 1 ? changed.getFirst() : changed.toArray();
    }

    /** A registry entry by its key, named before it's looked up: a villager type or profession. */
    record Keyed(String kind, String key) {
        @Override
        public String toString() {
            return key.equals("none") ? "no profession" : key + (kind.equals("villager profession") ? " profession" : "");
        }
    }

    /** {@code the villager profession of %livingentities%}: settable, and deleting it leaves none. */
    private static final class Profession implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Villager villager) {
                    values.add(new Keyed("villager profession", villager.getProfession().getKey().getKey()));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Keyed.class} : mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            String key = mode == Mode.SET && delta != null && delta.length > 0 && delta[0] instanceof Keyed keyed ? keyed.key() : "none";
            org.bukkit.entity.Villager.Profession profession = org.bukkit.Registry.VILLAGER_PROFESSION.get(
                    org.bukkit.NamespacedKey.minecraft(key));
            if (profession == null) {
                return;
            }
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof org.bukkit.entity.Villager villager) {
                    villager.setProfession(profession);
                }
            }
        }
    }

    /** {@code the whitelist}: the whitelisted players, who can be added and removed; resetting empties it. */
    private static final class WhitelistedPlayers implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            return org.bukkit.Bukkit.getWhitelistedPlayers().toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, REMOVE -> new Class<?>[] {org.bukkit.OfflinePlayer.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            if (mode == Mode.RESET || mode == Mode.DELETE) {
                for (org.bukkit.OfflinePlayer player : org.bukkit.Bukkit.getWhitelistedPlayers()) {
                    player.setWhitelisted(false);
                }
                return;
            }
            for (Object value : delta == null ? new Object[0] : delta) {
                if (value instanceof org.bukkit.OfflinePlayer player) {
                    player.setWhitelisted(mode == Mode.ADD);
                }
            }
        }
    }

    /** Puts {@code stack} on {@code living} where it's worn, or takes it off with {@code remove}. */
    private static void equip(LivingEntity living, org.bukkit.inventory.ItemStack stack, boolean remove) {
        org.bukkit.Material material = stack.getType();
        boolean saddle = material == org.bukkit.Material.SADDLE;
        if (living instanceof org.bukkit.entity.AbstractHorse horse && (saddle || material.name().endsWith("_HORSE_ARMOR"))) {
            org.bukkit.inventory.AbstractHorseInventory inventory = horse.getInventory();
            if (saddle) {
                inventory.setSaddle(remove ? null : stack);
            } else if (inventory instanceof org.bukkit.inventory.HorseInventory horses
                    && (!remove || horses.getArmor() != null && horses.getArmor().getType() == material)) {
                horses.setArmor(remove ? null : stack);
            }
            return;
        }
        if (saddle && living instanceof org.bukkit.entity.Steerable steerable) {
            steerable.setSaddle(!remove);
            return;
        }
        org.bukkit.inventory.EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }
        org.bukkit.inventory.EquipmentSlot slot = material.getEquipmentSlot();
        if (remove) {
            org.bukkit.inventory.ItemStack worn = equipment.getItem(slot);
            if (worn.getType() == material) {
                equipment.setItem(slot, null);
            }
        } else {
            equipment.setItem(slot, stack);
        }
    }

    /** Whether {@code living} is wearing {@code item}: in its armour, its body slot, or a horse's armour slot. */
    private static boolean wearing(LivingEntity living, ItemType item) {
        org.bukkit.Material material = item.material();
        if (living instanceof org.bukkit.entity.AbstractHorse horse) {
            for (org.bukkit.inventory.ItemStack stack : horse.getInventory().getContents()) {
                if (stack != null && stack.getType() == material) {
                    return true;
                }
            }
        }
        org.bukkit.inventory.EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return false;
        }
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
            if (slot == org.bukkit.inventory.EquipmentSlot.HAND || slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                    || !living.canUseEquipmentSlot(slot)) {
                continue;
            }
            if (equipment.getItem(slot).getType() == material) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code living} has a saddle on: a horse's saddle slot, or a pig's or strider's. */
    private static boolean saddled(LivingEntity living) {
        return switch (living) {
            case org.bukkit.entity.AbstractHorse horse -> horse.getInventory().getSaddle() != null;
            case org.bukkit.entity.Steerable steerable -> steerable.hasSaddle();
            default -> false;
        };
    }

    /** The entities riding others; set, added to, removed from or cleared. */
    private record Passengers() implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> passengers = new ArrayList<>();
            for (Object vehicle : owners(arguments[0])) {
                if (vehicle instanceof Entity entity) {
                    passengers.addAll(entity.getPassengers());
                }
            }
            return passengers.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Entity.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            for (Object value : owners(arguments[0])) {
                if (!(value instanceof Entity vehicle)) {
                    continue;
                }
                if (mode == Mode.SET || mode == Mode.DELETE || mode == Mode.RESET) {
                    vehicle.eject();
                }
                for (Object passenger : delta == null ? new Object[0] : delta) {
                    // Nothing can ride itself.
                    if (passenger instanceof Entity entity && entity != vehicle) {
                        if (mode == Mode.REMOVE) {
                            vehicle.removePassenger(entity);
                        } else {
                            vehicle.addPassenger(entity);
                        }
                    }
                }
            }
        }
    }

    /** How angry wardens are at entities, kept between 0 and 150; cleared to calm. */
    private record WardenAnger() implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
            List<Object> levels = new ArrayList<>();
            for (Object value : owners(args[0])) {
                if (value instanceof org.bukkit.entity.Warden warden) {
                    for (Object target : owners(args[1])) {
                        if (target instanceof Entity entity) {
                            levels.add((long) warden.getAnger(entity));
                        }
                    }
                }
            }
            return levels.size() == 1 ? levels.getFirst() : levels.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Number.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, Object[] delta,
                Mode mode) {
            double by = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
            for (Object value : owners(args[0])) {
                if (!(value instanceof org.bukkit.entity.Warden warden)) {
                    continue;
                }
                for (Object target : owners(args[1])) {
                    if (!(target instanceof Entity entity)) {
                        continue;
                    }
                    double level = switch (mode) {
                        case ADD -> warden.getAnger(entity) + by;
                        case REMOVE -> warden.getAnger(entity) - by;
                        case DELETE, RESET -> 0;
                        default -> by;
                    };
                    if (level <= 0) {
                        warden.clearAnger(entity);
                    } else {
                        warden.setAnger(entity, (int) Math.min(150, level));
                    }
                }
            }
        }
    }

    /** Values plugins attach to things, kept under NeoSkript's name. */
    private record Metadata() implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args) {
            List<Object> values = new ArrayList<>();
            for (Object holder : owners(args[1])) {
                if (holder instanceof org.bukkit.metadata.Metadatable metadatable) {
                    for (Object key : owners(args[0])) {
                        Object value = value(metadatable, co.xenastudios.neoskript.runtime.Values.text(key));
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        private static Object value(org.bukkit.metadata.Metadatable holder, String key) {
            org.bukkit.plugin.Plugin plugin = plugin();
            for (org.bukkit.metadata.MetadataValue value : holder.getMetadata(key)) {
                if (value.getOwningPlugin() == plugin) {
                    return value.value();
                }
            }
            return null;
        }

        private static org.bukkit.plugin.Plugin plugin() {
            return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(EntitySyntax.class);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Object.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] args, Object[] delta,
                Mode mode) {
            org.bukkit.plugin.Plugin plugin = plugin();
            Object given = delta != null && delta.length > 0 ? delta[0] : null;
            for (Object holder : owners(args[1])) {
                if (!(holder instanceof org.bukkit.metadata.Metadatable metadatable)) {
                    continue;
                }
                for (Object keyValue : owners(args[0])) {
                    String key = co.xenastudios.neoskript.runtime.Values.text(keyValue);
                    Object current = value(metadatable, key);
                    Object next = switch (mode) {
                        case DELETE, RESET -> null;
                        case SET -> given;
                        // Only what can be added together is: a number to a number, or anything to nothing.
                        case ADD -> current == null ? given : current instanceof Number && given instanceof Number
                                ? co.xenastudios.neoskript.runtime.Arithmetic.add(current, given) : current;
                        default -> current instanceof Number && given instanceof Number
                                ? co.xenastudios.neoskript.runtime.Arithmetic.subtract(current, given) : current;
                    };
                    if (next == null) {
                        metadatable.removeMetadata(key, plugin);
                    } else {
                        metadatable.setMetadata(key, new org.bukkit.metadata.FixedMetadataValue(plugin, next));
                    }
                }
            }
        }
    }

    /** Whether all of {@code values}, or with {@code all} false any of them, pass {@code test}. */
    private static boolean check(Object[] values, boolean all, java.util.function.Predicate<Object> test) {
        for (Object value : values) {
            if (test.test(value) != all) {
                return !all;
            }
        }
        return all;
    }

    /**
     * {@code the (max) domestication level of a horse}: how close it is to being tame. Changes work in whole
     * numbers as Skript's do, then stay at 1 or more, and the level at most the max.
     */
    private record Domestication(boolean max) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> levels = new ArrayList<>();
            for (Object value : Values.array(arguments[0])) {
                if (value instanceof org.bukkit.entity.AbstractHorse horse) {
                    levels.add((long) (max ? horse.getMaxDomestication() : horse.getDomestication()));
                }
            }
            return levels.size() == 1 ? levels.getFirst() : levels.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE, RESET -> new Class<?>[] {Number.class};
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments,
                Object[] delta, Mode mode) {
            int change = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.intValue() : 0;
            for (Object value : Values.array(arguments[0])) {
                if (!(value instanceof org.bukkit.entity.AbstractHorse horse)) {
                    continue;
                }
                int level = max ? horse.getMaxDomestication() : horse.getDomestication();
                level = switch (mode) {
                    case SET -> change;
                    case ADD -> level + change;
                    case REMOVE -> level - change;
                    default -> 1;
                };
                if (max) {
                    level = Math.max(level, 1);
                    horse.setMaxDomestication(level);
                    if (horse.getDomestication() > level) {
                        horse.setDomestication(level);
                    }
                } else {
                    horse.setDomestication(Math.max(1, Math.min(level, horse.getMaxDomestication())));
                }
            }
        }
    }
}
