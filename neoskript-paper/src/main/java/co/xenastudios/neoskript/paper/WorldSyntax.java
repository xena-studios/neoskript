/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** The world: entity types and spawning, entities' health, blocks, vectors and coordinates. */
final class WorldSyntax {

    private WorldSyntax() {}

    /** Who shot what isn't a projectile, like a pig, for {@code the shooter} in {@code shoot a pig:}. */
    private static final Map<Entity, org.bukkit.entity.LivingEntity> SHOOTERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Shoots one {@code kind} from {@code shooter}, an entity or a location, as Skript does: projectiles are
     * launched, fireballs start in front of the eyes, anything else starts halfway up and is pushed.
     */
    private static Entity shoot(EntityKind kind, Object shooter, double speed, Direction direction) {
        if (!kind.spawnable()) {
            return null;
        }
        Class<? extends Entity> type = kind.type().getEntityClass();
        Entity entity;
        Vector velocity;
        if (shooter instanceof org.bukkit.entity.LivingEntity living) {
            Location from = living.getLocation();
            velocity = direction.getDirection(from).multiply(speed);
            if (type != null && org.bukkit.entity.Fireball.class.isAssignableFrom(type)) {
                Location at = living.getEyeLocation().add(velocity.clone().normalize().multiply(0.5));
                entity = living.getWorld().spawn(at, type);
                ((org.bukkit.entity.Projectile) entity).setShooter(living);
            } else if (type != null && org.bukkit.entity.Projectile.class.isAssignableFrom(type)) {
                @SuppressWarnings("unchecked")
                Class<? extends org.bukkit.entity.Projectile> projectile = (Class<? extends org.bukkit.entity.Projectile>) type;
                entity = living.launchProjectile(projectile, velocity);
            } else {
                from.setY(from.getY() + living.getEyeHeight() / 2);
                entity = kind.spawn(from);
                if (entity != null) {
                    SHOOTERS.put(entity, living);
                }
            }
        } else if (shooter instanceof Location location) {
            velocity = direction.getDirection(location).multiply(speed);
            entity = kind.spawn(location);
        } else {
            return null;
        }
        if (entity != null) {
            entity.setVelocity(velocity);
        }
        return entity;
    }

    /** The entities spawned last on each thread, for {@code the last spawned pig}. */
    private static final ThreadLocal<Entity> LAST_SPAWNED = new ThreadLocal<>();

    /**
     * Entity types, registered before item types: words naming both, like {@code cod}, are the entity type
     * where nothing says which, as in Skript.
     */
    static void registerEntityTypes(Registrar registrar) {
        registrar.type("entitytype", "entity type", EntityKind.class);
        // Before items, kinds without an amount: 'cod' is the fish. With one, after them: '32 ender pearls' are
        // items, though '5 villagers' are still villagers.
        registrar.literalParser("entitytype", text -> EntityKind.counted(text) ? null : EntityKind.parse(text));
        registrar.parser("entitytype", EntityKind::parse);
        // Entity types written in the plural, which are all of those entities: 'size of zombies'.
        registrar.type("pluralentitytype", "plural entity type", EntityKind.class);
        // Never with an amount: '32 ender pearls' are items.
        registrar.literalParser("pluralentitytype", text -> !EntityKind.counted(text) && EntityKind.isPlural(text)
                ? EntityKind.parse(text) : null);
    }

    /** Entity types with an amount, tried after items: '5 villagers', '2 zombie'. */
    static void registerCountedEntityTypes(Registrar registrar) {
        registrar.type("countedentitytype", "entity type", EntityKind.class);
        registrar.subtype("countedentitytype", "entitytype");
        registrar.literalParser("countedentitytype", text -> EntityKind.counted(text) ? EntityKind.parse(text) : null);
    }

    static void register(Registrar registrar) {
        registrar.type("livingentity", "living entity", LivingEntity.class);
        registrar.type("block", "block", Block.class);
        registrar.type("vector", "vector", Vector.class);
        registrar.arithmetic('+', Vector.class, Vector.class, (x, y) -> x.clone().add(y));
        registrar.arithmetic('-', Vector.class, Vector.class, (x, y) -> x.clone().subtract(y));
        registrar.arithmetic('*', Vector.class, Vector.class, (x, y) -> x.clone().multiply(y));
        registrar.arithmetic('/', Vector.class, Vector.class, (x, y) -> x.clone().divide(y));
        registrar.arithmetic('*', Vector.class, Number.class, (x, y) -> x.clone().multiply(y.doubleValue()));
        registrar.arithmetic('*', Number.class, Vector.class, (x, y) -> y.clone().multiply(x.doubleValue()));
        registrar.arithmetic('/', Vector.class, Number.class, (x, y) -> x.clone().multiply(1 / y.doubleValue()));
        registrar.arithmeticDefault(Vector.class, Vector::new);
        // Vectors and locations change in place ('set x of {_v} to 1'), so variables and functions get copies.
        registrar.cloner(Vector.class, Vector::clone);
        registrar.cloner(Location.class, Location::clone);
        registrar.subtype("livingentity", "entity");
        registrar.subtype("player", "livingentity");
        registrar.text(Entity.class, entity -> entity.customName() != null
                ? Messages.LEGACY_SECTION.serialize(entity.customName()) : EntityKind.name(entity.getType()));
        registrar.text(Block.class, block -> ItemType.name(block.getType()));
        // In a variable's name, a block is where it is, as in Skript: '{_types::%loop-block%}'.
        registrar.variableNameText(Block.class, block -> block.getWorld().getName() + ":" + block.getX() + ","
                + block.getY() + "," + block.getZ());
        registrar.text(Vector.class, vector -> "x: " + Values.decimal(vector.getX()) + ", y: "
                + Values.decimal(vector.getY()) + ", z: " + Values.decimal(vector.getZ()));
        // '{_entity} is a pig', 'lucy axolotl is axolotl'.
        registrar.comparison(Entity.class, EntityKind.class, (entity, kind) -> kind.matches(entity));
        registrar.comparison(EntityKind.class, EntityKind.class, (kind, other) -> other.includes(kind));
        registrar.condition("CondIsSpawnable", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] kinds = (Object[]) args[0];
                for (Object kind : kinds) {
                    if (!(kind instanceof EntityKind k) || k.spawnable() == negated) {
                        return false;
                    }
                }
                return kinds.length > 0;
            };
        }, "%entitytypes% (is|are) spawnable [in %world%]", "%entitytypes% (isn't|is not|aren't|are not) spawnable [in %world%]");
        registrar.converter(Location.class, Block.class, location -> location.getWorld() == null ? null : location.getBlock());
        registrar.converter(Block.class, ItemType.class, block -> new ItemType(new org.bukkit.inventory.ItemStack(
                block.getType().isItem() ? block.getType() : Material.AIR), false));

        registrar.storage("vector", Vector.class, new VariableCodec<>() {
            @Override
            public void encode(Vector vector, BinaryWriter out) {
                out.beginObject(3);
                out.doubleField("x", vector.getX());
                out.doubleField("y", vector.getY());
                out.doubleField("z", vector.getZ());
            }

            @Override
            public Vector decode(BinaryReader in) {
                in.beginObject();
                return new Vector(in.doubleField("x"), in.doubleField("y"), in.doubleField("z"));
            }
        });

        registrar.type("chunk", "chunk", org.bukkit.Chunk.class);
        registrar.text(org.bukkit.Chunk.class, chunk -> "chunk (" + chunk.getX() + ", " + chunk.getZ() + ") of "
                + chunk.getWorld().getName());
        registrar.expression("ExprChunk", "chunk", info -> {
            boolean loaded = info.pattern() == 2;
            if (info.pattern() == 3) {
                // By chunk coordinates, without loading it: 'chunk at 1000, 1000 in world "world" is not loaded'.
                return (context, args) -> args[0] instanceof Number x && args[1] instanceof Number z
                        && args[2] instanceof org.bukkit.World world
                        ? world.getChunkAt((int) Math.floor(x.doubleValue()), (int) Math.floor(z.doubleValue()), false) : null;
            }
            int pattern = info.pattern();
            return (context, args) -> {
                List<Object> chunks = new ArrayList<>();
                // 'chunk 16 north of {_loc}': the directions combined with the locations.
                Object[] values = pattern == 0 ? (args[0] == null ? Values.array(args[1])
                        : Direction.combine(args[0], args[1]).toArray()) : (Object[]) args[0];
                for (Object value : values) {
                    if (loaded && value instanceof org.bukkit.World world) {
                        chunks.addAll(List.of(world.getLoadedChunks()));
                    } else if (!loaded && value instanceof Location location && location.getWorld() != null) {
                        chunks.add(location.getChunk());
                    }
                }
                return chunks.size() == 1 ? chunks.getFirst() : chunks.toArray();
            };
        }, "[(all [[of] the]|the)] chunk[s] (of|%-directions%) %locations%", "%locations%'[s] chunk[s]",
                "[(all [[of] the]|the)] loaded chunks (of|in) %worlds%",
                "[the] chunk [at] %number%, %number% (in|of) %world%");
        registrar.expression("ExprMidpoint", "object", info -> {
            String first = info.argumentType(0);
            String second = info.argumentType(1);
            if (first != null && second != null && !first.equals(second)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError(
                        "You can only get the midpoint between two locations or two vectors.");
            }
            return (context, args) -> {
            if (args[0] instanceof Location a && args[1] instanceof Location b) {
                if (a.getWorld() != b.getWorld()) {
                    co.xenastudios.neoskript.runtime.RuntimeErrors.report(
                            "Cannot get the midpoint of two locations in different worlds.");
                    return null;
                }
                return new Location(a.getWorld(), (a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2,
                        (a.getZ() + b.getZ()) / 2);
            }
            if (args[0] instanceof Vector a && args[1] instanceof Vector b) {
                return a.getMidpoint(b);
            }
            if (args[0] != null && args[1] != null) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report(
                        "You can only get the midpoint between two locations or two vectors.");
            }
            return null;
            };
        }, "[the] mid[-]point (of|between) %location/vector% and %location/vector%");

        registrar.expression("ExprWorlds", "world", info -> (context, args) -> Bukkit.getWorlds().toArray(),
                "[(all [[of] the]|the)] worlds");
        registrar.expression("ExprVectorFromXYZ", "vector", info -> (context, args) -> new Vector(number(args[0]),
                number(args[1]), number(args[2])), "[a] [new] vector [(from|at|to)] %number%,[ ]%number%(,[ ]| and )%number%");
        registrar.condition("CondIsLoaded", info -> {
            boolean negated = info.hasTag("not");
            int pattern = info.pattern();
            return (context, args) -> {
                // 'chunk at {_loc} is loaded': the directions combined with the locations, or the event's.
                Object[] values = pattern == 0 ? Direction.combine(args[0], args[1] == null
                        ? WorldProperties.eventLocation(context.event()) : args[1]).toArray() : (Object[]) args[0];
                // Nothing isn't loaded: 'world("fake-world") is not loaded' holds.
                if (values.length == 0) {
                    return negated;
                }
                for (Object value : values) {
                    boolean loaded = switch (value) {
                        case Location location -> location.getWorld() != null && location.isChunkLoaded();
                        case org.bukkit.Chunk chunk -> chunk.isLoaded();
                        case org.bukkit.World world -> Bukkit.getWorld(world.getUID()) != null;
                        case co.xenastudios.neoskript.runtime.ScriptRef script ->
                                co.xenastudios.neoskript.runtime.ScriptCatalog.get().loaded(script);
                        default -> false;
                    };
                    if (loaded == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "chunk[s] %directions% [%locations%] (is|are)[not:(n't| not)] loaded", "%chunks/worlds% (is|are)[not:(n't| not)] loaded",
                "world[s] %worlds% (is|are)[not:(n't| not)] loaded");

        registerEntities(registrar);
        registerBlocks(registrar);
        registerVectors(registrar);
    }

    private static void registerEntities(Registrar registrar) {
        registrar.section("EffSecSpawn", "entity", info -> {
            if (info.pattern() == 3) {
                // 'spawn 10 xp at X': an experience orb with that many points.
                return (context, args) -> {
                    List<Object> spawned = new ArrayList<>();
                    if (!(args[0] instanceof Experience experience) || args[2] == null) {
                        return spawned.toArray();
                    }
                    for (Object place : Direction.combine(args[1], args[2])) {
                        if (place instanceof Location location && location.getWorld() != null) {
                            Entity orb = location.getWorld().spawn(location, org.bukkit.entity.ExperienceOrb.class,
                                    created -> created.setExperience(experience.xp()));
                            LAST_SPAWNED.set(orb);
                            spawned.add(orb);
                        }
                    }
                    return spawned.toArray();
                };
            }
            boolean counted = info.pattern() >= 1;
            return (context, args) -> {
                int amount = counted && args[0] instanceof Number n ? co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue()) : 1;
                Object[] types = (Object[]) args[counted ? 1 : 0];
                int at = counted ? 2 : 1;
                Object[] locations = Direction.combine(args[at], args[at + 1]).toArray();
                List<Object> spawned = new ArrayList<>();
                for (Object place : locations) {
                    if (!(place instanceof Location location) || location.getWorld() == null) {
                        continue;
                    }
                    for (Object type : types) {
                        // A snapshot spawns as the entity it was taken of: 'spawn {_snapshot} at X'.
                        if (type instanceof org.bukkit.entity.EntitySnapshot snapshot) {
                            for (int i = 0; i < amount; i++) {
                                Entity entity = snapshot.createEntity(location);
                                LAST_SPAWNED.set(entity);
                                spawned.add(entity);
                            }
                            continue;
                        }
                        // Categories like 'monster' can't be spawned, only kinds that are one entity type.
                        if (!(type instanceof EntityKind kind) || !kind.spawnable()) {
                            continue;
                        }
                        for (int i = 0, n = amount * kind.amount(); i < n; i++) {
                            Entity entity = kind.spawn(location);
                            if (entity != null) {
                                LAST_SPAWNED.set(entity);
                                spawned.add(entity);
                            }
                        }
                    }
                }
                return spawned.toArray();
            };
        }, "(spawn|summon) %entitytypes/entitysnapshots% [%directions% %locations%]",
                "(spawn|summon) %number% of %entitytypes/entitysnapshots% [%directions% %locations%]",
                "(spawn|summon) %number% %entitytypes% [%directions% %locations%]",
                "(spawn|summon) %experience% [%directions% %locations%]");
        // 'shoot an arrow from the player', 'make {_loc} shoot a pig at speed 2': its code runs for each one shot.
        registrar.section("EffSecShoot", "entity", info -> {
            int typesAt = info.pattern() == 0 ? 0 : 1;
            boolean directionWritten = !info.isDefault(3);
            return (context, args) -> {
                Object[] types = Values.array(args[typesAt]);
                Object[] shooters = Values.array(args[1 - typesAt]);
                double speed = args[2] == null ? 5 : Values.single(args[2]) instanceof Number n ? n.doubleValue() : Double.NaN;
                // Without a direction, where the shooter faces: Skript's Direction.IDENTITY.
                Object direction = directionWritten ? Values.single(args[3]) : Direction.IDENTITY;
                List<Object> shot = new ArrayList<>();
                if (Double.isNaN(speed) || !(direction instanceof Direction)) {
                    return shot.toArray();
                }
                for (Object shooter : shooters) {
                    for (Object type : types) {
                        if (type instanceof EntityKind kind) {
                            Entity entity = shoot(kind, shooter, speed, (Direction) direction);
                            if (entity != null) {
                                LAST_SPAWNED.set(entity);
                                shot.add(entity);
                            }
                        }
                    }
                }
                return shot.toArray();
            };
        }, "shoot %entitytypes% [from %livingentities/locations%] [(at|with) (speed|velocity) %-number%] [%-direction%]",
                "(make|let) %livingentities/locations% shoot %entitytypes% [(at|with) (speed|velocity) %-number%] [%-direction%]");
        // The shooter of a projectile, or in 'shoot a pig:', of what was shot.
        registrar.expression("ExprShooter", "livingentity", info -> new SettableProperty<>(Entity.class,
                org.bukkit.entity.LivingEntity.class, entity -> entity instanceof org.bukkit.entity.Projectile projectile
                        ? projectile.getShooter() instanceof org.bukkit.entity.LivingEntity living ? living : null
                        : SHOOTERS.get(entity), (entity, shooter) -> {
                    if (entity instanceof org.bukkit.entity.Projectile projectile) {
                        projectile.setShooter(shooter);
                    }
                }), "[the] shooter [of %entities%]");
        registrar.expression("ExprLastSpawnedEntity", "entity", info -> (context, args) -> {
            Entity last = LAST_SPAWNED.get();
            return last != null && (args[0] == null || (args[0] instanceof EntityKind kind && kind.matches(last))) ? last : null;
        }, "[the] [last[ly]] (spawned|shot) %*entitytype%");

        // Replaces the players-only version from step 4: 'all players', 'all zombies in world "world"'.
        registrar.expression("ExprEntities", "entity", info -> {
            // 'zombies' on its own is all zombies, everywhere.
            int pattern = info.pattern() == 5 ? 0 : info.pattern();
            boolean bare = info.pattern() == 5;
            return (context, given) -> {
                Object[] args = bare ? new Object[] {given[0], null, null} : given;
                List<Object> types = List.of((Object[]) args[0]);
                Predicate<Entity> wanted = entity -> {
                    for (Object type : types) {
                        if (type instanceof EntityKind kind && kind.matches(entity)) {
                            return true;
                        }
                    }
                    return false;
                };
                List<Object> found = new ArrayList<>();
                if (pattern >= 2) {
                    // Around a location, or within the box between two corners.
                    if (!(args[2] instanceof Location center) || center.getWorld() == null) {
                        return found.toArray();
                    }
                    if (pattern == 4) {
                        if (!(args[1] instanceof Location corner) || corner.getWorld() != center.getWorld()) {
                            return found.toArray();
                        }
                        org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(corner, center);
                        found.addAll(center.getWorld().getNearbyEntities(box, wanted));
                        return found.toArray();
                    }
                    double radius = args[1] instanceof Number n ? n.doubleValue() : 0;
                    if (!(radius > 0)) {
                        return found.toArray();
                    }
                    double square = radius * radius;
                    for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius, wanted)) {
                        if (entity.getLocation().distanceSquared(center) <= square) {
                            found.add(entity);
                        }
                    }
                    return found.toArray();
                }
                if (args[2] != null) {
                    for (Object chunk : (Object[]) args[2]) {
                        if (chunk instanceof org.bukkit.Chunk c) {
                            for (Entity entity : c.getEntities()) {
                                if (wanted.test(entity)) {
                                    found.add(entity);
                                }
                            }
                        }
                    }
                    return found.toArray();
                }
                List<org.bukkit.World> worlds = new ArrayList<>();
                if (args[1] == null) {
                    worlds.addAll(Bukkit.getWorlds());
                } else {
                    for (Object world : (Object[]) args[1]) {
                        if (world instanceof org.bukkit.World w) {
                            worlds.add(w);
                        }
                    }
                }
                boolean onlyPlayers = types.size() == 1 && types.getFirst() instanceof EntityKind kind
                        && kind.type() == EntityType.PLAYER;
                for (org.bukkit.World world : worlds) {
                    for (Entity entity : onlyPlayers ? new ArrayList<Entity>(world.getPlayers()) : world.getEntities()) {
                        if (wanted.test(entity)) {
                            found.add(entity);
                        }
                    }
                }
                return found.toArray();
            };
        // 'all' or 'the' is needed: 'baby chicken' alone is the entity type.
        }, "(all [[of] the]|the) %*entitytypes% [(in|of) (world[s] %-worlds%|%-chunks%)]",
                "(all [[of] the]|the) entities of type[s] %entitytypes% [(in|of) (world[s] %-worlds%|%-chunks%)]",
                "(all [[of] the]|the) %*entitytypes% (within|[with]in radius) %number% [(block[s]|met(er|re)[s])] (of|around) %location%",
                "(all [[of] the]|the) entities of type[s] %entitytypes% in radius %number% (of|around) %location%",
                "(all [[of] the]|the) %*entitytypes% within %location% and %location%",
                "%*pluralentitytypes%");

        registrar.expression("ExprHealth", "number", info -> new Health(), "[the] health of %livingentities%",
                "%livingentities%'[s] health");
        registrar.effect("EffKill", info -> (context, args) -> {
            for (Object entity : (Object[]) args[0]) {
                if (entity instanceof LivingEntity living) {
                    living.setHealth(0);
                } else if (entity instanceof Entity e) {
                    e.remove();
                }
            }
        }, "kill %entities%");
        // 'delete {_pig}': entities are removed from the world.
        registrar.changer(Entity.class, Changeable.Mode.DELETE, (entity, values) -> {
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove();
            }
        });
    }

    /**
     * Directions, tried after the rest of the library, as Skript's come after its amounts: 'size of blocks above
     * X' is the size of the blocks above X, not '(size of blocks) above X'.
     */
    static void registerLate(Registrar registrar) {
        // Skript's ExprDirection. Pattern 0's marks pick a face of BY_MARK, combined by XOR ('north' 2 and 'east'
        // 4 make north east, 6); 'north 5 east' chains two absolute directions.
        registrar.expression("ExprDirection", "direction", info -> {
            int pattern = info.pattern();
            int mark = info.mark();
            boolean amountWritten = !info.isDefault(0);
            boolean nextWritten = pattern == 0 && !info.isDefault(1);
            // Skript only chains a direction expression, never a variable.
            boolean nextChains = nextWritten && info.argumentType(1) != null;
            BlockFace face = pattern == 0 ? BY_MARK[mark] : null;
            boolean horizontal = (pattern == 1 || pattern == 2) ? mark % 2 != 0 : pattern == 4;
            boolean facing = (pattern == 1 || pattern == 2) && mark >= 2;
            double yaw = Math.PI / 2 * mark;
            return (context, args) -> {
                Object amount = args[0];
                if (amountWritten && !(amount instanceof Number)) {
                    return null;
                }
                double length = amount instanceof Number n ? n.doubleValue() : 1;
                Number written = amountWritten ? (Number) amount : null;
                Direction direction = switch (pattern) {
                    case 0 -> {
                        Vector v = new Vector(face.getModX(), face.getModY(), face.getModZ()).multiply(length);
                        if (nextWritten) {
                            // Only another absolute direction expression can follow, as Skript parses it.
                            if (!nextChains || !(args[1] instanceof Direction next) || !next.chainable()) {
                                yield null;
                            }
                            v.add(next.getDirection());
                        }
                        yield new Direction(v);
                    }
                    case 1, 2 -> relativeTo(args[1], length, horizontal, facing);
                    default -> new Direction(horizontal ? Direction.IGNORE_PITCH : 0, yaw, length);
                };
                return direction == null ? null : direction.written(written, pattern == 0);
            };
        }, "[%-number% [(block|met(er|re))[s]] [to the]] ("
                        + "2¦north[(-| |)(4¦east|5¦west)][(ward(s|ly|)|er(n|ly|))] [of]"
                        + "|3¦south[(-| |)(11¦east|10¦west)][(ward(s|ly|)|er(n|ly|))] [of]"
                        + "|(4¦east|5¦west)[(ward(s|ly|)|er(n|ly|))] [of]"
                        + "|0¦above|0¦over|(0¦up|1¦down)[ward(s|ly|)]|1¦below|1¦under[neath]|1¦beneath"
                        + ") [%-direction%]",
                "[%-number% [(block|met(er|re))[s]]] in [the] (0¦direction|1¦horizontal direction|2¦facing"
                        + "|3¦horizontal facing) of %entity/block% (of|from|)",
                "[%-number% [(block|met(er|re))[s]]] in %entity/block%'[s] (0¦direction|1¦horizontal direction"
                        + "|2¦facing|3¦horizontal facing) (of|from|)",
                "[%-number% [(block|met(er|re))[s]]] (0¦in[ ]front [of]|0¦forward[s]|2¦behind|2¦backwards"
                        + "|[to the] (1¦right|-1¦left) [of])",
                "[%-number% [(block|met(er|re))[s]]] horizontal[ly] (0¦in[ ]front [of]|0¦forward[s]|2¦behind"
                        + "|2¦backwards|to the (1¦right|-1¦left) [of])");
    }

    /** ExprDirection's faces, by parse mark. */
    private static final BlockFace[] BY_MARK = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_WEST};

    /** ExprDirection's {@code in the (horizontal) direction/facing of X}, {@code length} long. */
    private static Direction relativeTo(Object of, double length, boolean horizontal, boolean facing) {
        if (of instanceof Block block) {
            BlockFace face = Direction.getFacing(block);
            if (face == BlockFace.SELF || horizontal && (face == BlockFace.UP || face == BlockFace.DOWN)) {
                return Direction.ZERO;
            }
            return new Direction(face, length);
        }
        if (!(of instanceof Entity entity)) {
            return null;
        }
        Location location = entity.getLocation();
        if (!horizontal) {
            if (!facing) {
                return new Direction(location.getDirection().normalize().multiply(length));
            }
            double pitch = Direction.pitchToRadians(location.getPitch());
            if (pitch > Math.PI / 4) {
                return new Direction(new double[] {0, length, 0});
            }
            if (pitch < -Math.PI / 4) {
                return new Direction(new double[] {0, -length, 0});
            }
        }
        double yaw = Direction.yawToRadians(location.getYaw());
        if (horizontal && !facing) {
            return new Direction(new double[] {Math.cos(yaw) * length, 0, Math.sin(yaw) * length});
        }
        yaw = ((yaw % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        if (yaw >= Math.PI / 4 && yaw < 3 * Math.PI / 4) {
            return new Direction(new double[] {0, 0, length});
        }
        if (yaw >= 3 * Math.PI / 4 && yaw < 5 * Math.PI / 4) {
            return new Direction(new double[] {-length, 0, 0});
        }
        if (yaw >= 5 * Math.PI / 4 && yaw < 7 * Math.PI / 4) {
            return new Direction(new double[] {0, 0, -length});
        }
        return new Direction(new double[] {length, 0, 0});
    }

    private static void registerBlocks(Registrar registrar) {
        registrar.type("direction", "direction", Direction.class);
        registrar.text(Direction.class, Direction::toString);
        registrar.storage("direction", Direction.class, Direction.CODEC);
        // A direction left out is 'at', Skript's default expression for directions.
        registrar.defaultValue("direction", Direction.ZERO);
        // A vector is a direction: 'blocks vector(1, 0, 0) {_loc}'.
        registrar.converter(Vector.class, Direction.class, Direction::new);
        registrar.effect("EffPush", info -> {
            boolean along = info.pattern() == 0;
            boolean away = info.hasTag("away");
            boolean speedWritten = !info.isDefault(2);
            return (context, args) -> {
                Object speed = args[2];
                if (speedWritten && !(speed instanceof Number)) {
                    return;
                }
                java.util.function.Function<Entity, Vector> towards;
                if (along) {
                    if (!(args[1] instanceof Direction direction)) {
                        return;
                    }
                    towards = direction::getDirection;
                } else {
                    if (!(args[1] instanceof Location target)) {
                        return;
                    }
                    // Skript subtracts from the one target vector, so each entity after the first is pushed from
                    // what's left of it.
                    Vector targetVector = target.toVector();
                    towards = entity -> {
                        Vector direction = targetVector.subtract(entity.getLocation().toVector());
                        if (away) {
                            direction.multiply(-1);
                        }
                        return direction;
                    };
                }
                for (Object value : Values.array(args[0])) {
                    if (!(value instanceof Entity entity)) {
                        continue;
                    }
                    Vector push = towards.apply(entity);
                    if (speed instanceof Number n) {
                        push.normalize().multiply(n.doubleValue());
                    }
                    if (!(Double.isFinite(push.getX()) && Double.isFinite(push.getY()) && Double.isFinite(push.getZ()))) {
                        return;
                    }
                    entity.setVelocity(entity.getVelocity().add(push));
                }
            };
        }, "(push|thrust|pull) %entities% [along] %direction% [(at|with) [a] (speed|velocity|force) [of] %-number%]",
                "(push|thrust|pull) %entities% (towards|away:away from) %location% [(at|with) [a] (speed|velocity|force) "
                        + "[of] %-number%]");
        registrar.expression("ExprBlock-2", "block", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (pattern == 0) {
                    // 'block at X' or 'blocks at {_locations::*}'.
                    List<Object> blocks = new ArrayList<>();
                    for (Object value : args[0] instanceof Object[] values ? values : new Object[] {args[0]}) {
                        if (value instanceof Location location && location.getWorld() != null) {
                            blocks.add(location.getBlock());
                        }
                    }
                    return blocks.size() == 1 ? blocks.getFirst() : blocks.toArray();
                }
                // Skript's ExprBlock: 'the block above', the direction combined with the location (the event's
                // without one).
                Object location = args[1] == null ? WorldProperties.eventLocation(context.event()) : args[1];
                List<Location> moved = Direction.combine(args[0], location);
                return moved.isEmpty() || moved.getFirst().getWorld() == null ? null : moved.getFirst().getBlock();
            };
        }, "[the] block[s] (at|of) %locations%", "[the] block %direction% [%location%]");
        // 'the location 2 meters above {_loc}'; without a location, the event's.
        registrar.expression("ExprLocation", "location", info -> (context, args) -> {
            Object location = args[1] == null ? WorldProperties.eventLocation(context.event()) : args[1];
            List<Location> moved = Direction.combine(args[0], location);
            return moved.isEmpty() ? null : moved.getFirst();
        }, "[the] (location|position) %directions% [%location%]");
        registrar.expression("ExprBlocks", "block", info -> {
            int pattern = info.pattern();
            int directionAt = pattern == 1 ? 1 : 0;
            int fromAt = 1 - directionAt;
            // Skript reads a written distance off its direction expression, not off a variable.
            boolean expression = info.argumentType(directionAt) != null;
            boolean pluralFrom = pattern == 0 && info.isList(1);
            return (context, args) -> {
                List<Object> blocks = new ArrayList<>();
                switch (pattern) {
                    case 0, 1 -> {
                        Object fromValue = pattern == 0 && args[1] == null
                                ? WorldProperties.eventLocation(context.event()) : args[fromAt];
                        Object[] from = Values.array(fromValue);
                        if (!(Values.single(args[directionAt]) instanceof Direction direction)) {
                            return new Object[0];
                        }
                        if (pluralFrom || from.length != 1) {
                            // Several places: the block in the direction from each.
                            for (Object place : from) {
                                if (place instanceof Location location && location.isWorldLoaded()) {
                                    blocks.add(direction.getRelative(location).getBlock());
                                }
                            }
                            return blocks.toArray();
                        }
                        Location location = from[0] instanceof Location l ? l
                                : from[0] instanceof Block block ? block.getLocation().add(0.5, 0.5, 0.5) : null;
                        if (location == null || location.getWorld() == null) {
                            return new Object[0];
                        }
                        Vector vector = from[0] instanceof Block block ? direction.getDirection(block)
                                : direction.getDirection(location);
                        if (vector.getX() == 0 && vector.getY() == 0 && vector.getZ() == 0) {
                            return new Object[0];
                        }
                        // The start block and as many more as the maximum target block distance, less one.
                        int distance = 100 - 1;
                        if (expression && direction.writtenAmount() != null) {
                            distance = direction.writtenAmount().intValue();
                        }
                        lineFrom(location, location.clone().add(vector.clone().normalize().multiply(distance)), blocks);
                    }
                    case 2, 3, 4 -> {
                        if (!(args[0] instanceof Location a) || !(args[1] instanceof Location b)
                                || a.getWorld() != b.getWorld() || a.getWorld() == null) {
                            return new Object[0];
                        }
                        if (pattern == 4) {
                            cuboid(a.getWorld(), Math.min(a.getBlockX(), b.getBlockX()),
                                    Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                                    Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()),
                                    Math.max(a.getBlockZ(), b.getBlockZ()), blocks);
                        } else {
                            lineFrom(a.getBlock().getLocation().toCenterLocation(),
                                    b.getBlock().getLocation().toCenterLocation(), blocks);
                        }
                    }
                    default -> {
                        if (!(args[0] instanceof org.bukkit.Chunk chunk)) {
                            return new Object[0];
                        }
                        World world = chunk.getWorld();
                        Block low = chunk.getBlock(0, world.getMinHeight(), 0);
                        Block high = chunk.getBlock(15, world.getMaxHeight() - 1, 15);
                        cuboid(world, low.getX(), low.getY(), low.getZ(), high.getX(), high.getY(), high.getZ(), blocks);
                    }
                }
                return blocks.toArray();
            };
        }, "[(all [[of] the]|the)] blocks %direction% [%locations%]",
                "[(all [[of] the]|the)] blocks from %location% [on] %direction%",
                "[(all [[of] the]|the)] blocks from %location% to %location%",
                "[(all [[of] the]|the)] blocks between %location% and %location%",
                "[(all [[of] the]|the)] blocks within %location% and %location%",
                "[(all [[of] the]|the)] blocks (in|within) %chunk%");
        // 'set block at X to stone': blocks become the item's block.
        registrar.changer(Block.class, Changeable.Mode.SET, (block, values) -> {
            for (Object value : values) {
                if (value instanceof org.bukkit.block.data.BlockData data) {
                    block.setBlockData(data);
                    return;
                }
                Material material = value instanceof ItemType item ? item.material()
                        : value instanceof org.bukkit.inventory.ItemStack stack ? stack.getType() : null;
                if (material != null && material.isBlock()) {
                    block.setType(material);
                    if (value instanceof ItemType item) {
                        copyContainer(block, item);
                    }
                    return;
                }
            }
        });
        registrar.changer(Block.class, Changeable.Mode.DELETE, (block, values) -> block.setType(Material.AIR));
        registerTrees(registrar);

        // 'blocks in radius 2 of X': the blocks whose centres are within the radius, as Skript finds them.
        registrar.expression("ExprBlockSphere", "block", info -> {
            int radiusAt = info.pattern() == 0 ? 0 : 1;
            return (context, args) -> {
                Location center = (Location) Values.single(args[1 - radiusAt]);
                if (!(Values.single(args[radiusAt]) instanceof Number number) || center == null || center.getWorld() == null) {
                    return new Object[0];
                }
                double radius = number.doubleValue();
                double reach = radius + 0.5001;
                World world = center.getWorld();
                int minX = (int) Math.ceil(center.getX() - reach);
                int maxX = (int) Math.floor(center.getX() + reach);
                int minY = (int) Math.ceil(Math.max(center.getY() - reach, world.getMinHeight()));
                int maxY = (int) Math.floor(Math.min(center.getY() + reach, world.getMaxHeight() - 1));
                int minZ = (int) Math.ceil(center.getZ() - reach);
                int maxZ = (int) Math.floor(center.getZ() + reach);
                double squared = radius * radius * 1.00001;
                List<Object> blocks = new ArrayList<>();
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            double dx = x + 0.5 - center.getX();
                            double dy = y + 0.5 - center.getY();
                            double dz = z + 0.5 - center.getZ();
                            if (dx * dx + dy * dy + dz * dz < squared) {
                                blocks.add(world.getBlockAt(x, y, z));
                            }
                        }
                    }
                }
                return blocks.toArray();
            };
        }, "[(all [[of] the]|the)] blocks in radius %number% [(of|around) %location%]",
                "[(all [[of] the]|the)] blocks around %location% in radius %number%");
        registrar.effect("EffBlockUpdate", info -> {
            boolean physics = !info.hasTag("physics");
            return (context, args) -> {
                if (!(Values.single(args[1]) instanceof org.bukkit.block.data.BlockData data)) {
                    return;
                }
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Block block) {
                        block.setBlockData(data, physics);
                    }
                }
            };
        }, "update %blocks% (as|to be) %blockdata% [physics:without [neighbo[u]r[ing]|adjacent] [physics] update[s]]");
    }

    /**
     * Skript's {@code BlockLineIterator}: the blocks a ray from {@code start} to {@code end} passes through, both
     * ends included, added to {@code blocks}.
     */
    static void lineFrom(Location start, Location end, List<Object> blocks) {
        World world = start.getWorld();
        double[] current = {start.getX(), start.getY(), start.getZ()};
        double[] last = {end.getX(), end.getY(), end.getZ()};
        double[] lastCentre = centred(last);
        double[] step = {last[0] - current[0], last[1] - current[1], last[2] - current[2]};
        // JOML's normalize: times the inverse length.
        double inverse = 1.0 / Math.sqrt(step[0] * step[0] + (step[1] * step[1] + step[2] * step[2]));
        for (int i = 0; i < 3; i++) {
            step[i] *= inverse;
        }
        while (true) {
            // Overshot the end: stop, as Skript's iterator does by throwing.
            double back = (last[0] - current[0]) * step[0] + (last[1] - current[1]) * step[1]
                    + (last[2] - current[2]) * step[2];
            if (back < -1) {
                return;
            }
            double[] centre = centred(current);
            blocks.add(world.getBlockAt((int) Math.floor(centre[0]), (int) Math.floor(centre[1]),
                    (int) Math.floor(centre[2])));
            if (Arrays.equals(centre, lastCentre)) {
                return;
            }
            double[] needed = new double[3];
            for (int i = 0; i < 3; i++) {
                needed[i] = (Math.signum(step[i]) * 0.5 + centre[i] - current[i]) / step[i];
                if (Double.isNaN(needed[i])) {
                    needed[i] = Double.POSITIVE_INFINITY;
                }
            }
            // JOML's minComponent, by absolute value.
            double ax = Math.abs(needed[0]);
            double ay = Math.abs(needed[1]);
            double az = Math.abs(needed[2]);
            double t = ax < ay && ax < az ? needed[0] : ay < az ? needed[1] : needed[2];
            // Skript adds Math.ulp(1), the float one.
            t += Math.ulp(1f);
            for (int i = 0; i < 3; i++) {
                // JOML adds without a fused multiply-add by default.
                current[i] = t * step[i] + current[i];
            }
        }
    }

    private static double[] centred(double[] point) {
        return new double[] {Math.floor(point[0]) + 0.5, Math.floor(point[1]) + 0.5, Math.floor(point[2]) + 0.5};
    }

    /** Skript's {@code AABB} iterator: the blocks from the low corner to the high one, x fastest, then z, then y. */
    private static void cuboid(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            List<Object> blocks) {
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }
    }

    /** Skript's tree types: kinds of tree or huge mushroom, by Bukkit's tree types, grown at random among them. */
    record TreeKind(String name, List<String> types) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static void registerTrees(Registrar registrar) {
        Map<String, TreeKind> kinds = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<String, String[]> kind = (names, types) -> {
            String[] all = names.split(", ");
            TreeKind tree = new TreeKind(all[0], List.of(types));
            for (String name : all) {
                kinds.put(name, tree);
            }
        };
        kind.accept("tree, any tree", new String[] {"*"});
        kind.accept("oak tree, any oak tree, any normal tree, any regular tree", new String[] {"TREE", "BIG_TREE"});
        kind.accept("small oak tree, small normal tree, small regular tree", new String[] {"TREE"});
        kind.accept("big oak tree, big normal tree, big regular tree", new String[] {"BIG_TREE"});
        kind.accept("spruce tree, any spruce tree", new String[] {"REDWOOD", "TALL_REDWOOD"});
        kind.accept("small spruce tree", new String[] {"REDWOOD"});
        kind.accept("big spruce tree, tall spruce tree", new String[] {"TALL_REDWOOD"});
        kind.accept("mega spruce tree, giant spruce tree", new String[] {"MEGA_REDWOOD"});
        kind.accept("birch tree, any birch tree", new String[] {"BIRCH", "TALL_BIRCH"});
        kind.accept("small birch tree", new String[] {"BIRCH"});
        kind.accept("tall birch tree", new String[] {"TALL_BIRCH"});
        kind.accept("jungle tree, any jungle tree", new String[] {"SMALL_JUNGLE", "JUNGLE", "COCOA_TREE"});
        kind.accept("small jungle tree", new String[] {"SMALL_JUNGLE"});
        kind.accept("big jungle tree, tall jungle tree, giant jungle tree", new String[] {"JUNGLE"});
        kind.accept("cocoa tree, jungle cocoa tree, cocoa jungle tree", new String[] {"COCOA_TREE"});
        kind.accept("jungle bush, jungle bush tree", new String[] {"JUNGLE_BUSH"});
        kind.accept("acacia tree", new String[] {"ACACIA"});
        kind.accept("dark oak tree", new String[] {"DARK_OAK"});
        kind.accept("swamp tree, swamp oak, swamp oak tree", new String[] {"SWAMP"});
        kind.accept("huge mushroom, any huge mushroom", new String[] {"RED_MUSHROOM", "BROWN_MUSHROOM"});
        kind.accept("huge red mushroom", new String[] {"RED_MUSHROOM"});
        kind.accept("huge brown mushroom", new String[] {"BROWN_MUSHROOM"});
        kind.accept("mangrove tree, any mangrove tree", new String[] {"MANGROVE", "TALL_MANGROVE"});
        kind.accept("small mangrove tree", new String[] {"MANGROVE"});
        kind.accept("big mangrove tree", new String[] {"TALL_MANGROVE"});
        kind.accept("azalea tree", new String[] {"AZALEA"});
        kind.accept("pale oak tree, any pale oak tree", new String[] {"PALE_OAK", "PALE_OAK_CREAKING"});
        kind.accept("normal pale oak tree", new String[] {"PALE_OAK"});
        kind.accept("creaking pale oak tree", new String[] {"PALE_OAK_CREAKING"});
        kind.accept("cherry tree", new String[] {"CHERRY"});
        kind.accept("crimson fungus, crimson tree, crimson fungus tree", new String[] {"CRIMSON_FUNGUS"});
        kind.accept("warped fungus, warped tree, warped fungus tree", new String[] {"WARPED_FUNGUS"});
        kind.accept("chorus plant", new String[] {"CHORUS_PLANT"});
        kind.accept("poplar tree, any poplar tree", new String[] {"POPLAR"});
        registrar.namedType("treetype", "tree type", TreeKind.class, kinds);
        registrar.effect("EffTree", info -> {
            boolean typed = info.pattern() == 1;
            return (context, args) -> {
                Object chosen = typed || args[0] != null ? Values.single(args[0]) : kinds.get("tree");
                if (!(chosen instanceof TreeKind tree)) {
                    return;
                }
                Object[] directions = Values.array(args[1]);
                for (Object value : (Object[]) args[2]) {
                    if (value instanceof Location location && location.getWorld() != null) {
                        Location at = location;
                        for (Object direction : directions) {
                            if (direction instanceof Direction d) {
                                at = d.getRelative(at);
                            }
                        }
                        grow(tree, at);
                    }
                }
            };
        }, "(grow|create|generate) tree [of type %treetype%] %directions% %locations%",
                "(grow|create|generate) %treetype% %directions% %locations%");
    }

    /** Grows one of the tree's types at {@code location}, at random. */
    private static void grow(TreeKind tree, Location location) {
        List<org.bukkit.TreeType> types = new ArrayList<>();
        for (String name : tree.types()) {
            if (name.equals("*")) {
                types.addAll(List.of(org.bukkit.TreeType.values()));
                continue;
            }
            try {
                types.add(org.bukkit.TreeType.valueOf(name));
            } catch (IllegalArgumentException notInThisVersion) {
                // A tree newer than this server.
            }
        }
        if (!types.isEmpty()) {
            location.getWorld().generateTree(location,
                    types.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(types.size())));
        }
    }

    private static void registerVectors(Registrar registrar) {
        // 'vector(5)' is vector(5, 5, 5).
        registrar.function("vector", List.of(Parameter.of("x", "number"), new Parameter("y", "number", false, true),
                new Parameter("z", "number", false, true)), "vector", false, args -> new Vector(number(args[0]),
                number(args[1] == null ? args[0] : args[1]), number(args[2] == null ? args[0] : args[2])));
        registrar.expression("ExprVectorBetweenLocations", "vector", info -> (context, args) ->
                args[0] instanceof Location from && args[1] instanceof Location to
                        ? to.toVector().subtract(from.toVector()) : null,
                "[the] vector (from|between) %location% (to|and) %location%");
        registrar.expression("ExprVectorOfLocation", "vector", info -> (context, args) -> map(args[0],
                value -> value instanceof Location location ? location.toVector() : null),
                "[the] vector (of|from|to) %locations%", "%locations%'[s] vector");
        registrar.expression("ExprLocationFromVector", "location", info -> (context, args) -> {
            if (!(args[0] instanceof Vector vector) || !(args[1] instanceof World world)) {
                return null;
            }
            float yaw = args.length > 2 && args[2] instanceof Number n ? n.floatValue() : 0;
            float pitch = args.length > 3 && args[3] instanceof Number n ? n.floatValue() : 0;
            return new Location(world, vector.getX(), vector.getY(), vector.getZ(), yaw, pitch);
        }, "%vector% to location in %world%", "location (from|of) %vector% in %world%",
                "%vector% [to location] in %world% with yaw %number% and pitch %number%",
                "location (from|of) %vector% in %world% with yaw %number% and pitch %number%");
        registrar.expression("ExprVectorNormalize", "vector", info -> (context, args) -> map(args[0],
                value -> value instanceof Vector vector ? (vector.lengthSquared() == 0 ? vector.clone() : vector.clone().normalize())
                        : null),
                "normali(z|s)e[d] %vectors%", "%vectors% normali(z|s)ed");
        EntitySyntax.conditionPatterns(registrar, "CondIsVectorNormalized", Vector.class, Vector::isNormalized, null,
                "%vectors% (is|are) normali(z|s)ed", "%vectors% (isn't|is not|aren't|are not) normali(z|s)ed");
        registrar.expression("ExprVectorCylindrical", "vector", info -> (context, args) -> {
            if (!(args[0] instanceof Number radius) || !(args[1] instanceof Number yaw) || !(args[2] instanceof Number height)) {
                return null;
            }
            // Skript's yaw 0 points along z, so it's 90 degrees round from the x axis.
            double phi = Math.toRadians(yaw.doubleValue() + 90);
            return new Vector(radius.doubleValue() * Math.cos(phi), height.doubleValue(), radius.doubleValue() * Math.sin(phi));
        }, "[a] [new] cylindrical vector [(from|with)] [radius] %number%, [yaw] %number%(,|[,] and) [height] %number%");
        registrar.expression("ExprVectorSpherical", "vector", info -> (context, args) -> {
            if (!(args[0] instanceof Number radius) || !(args[1] instanceof Number yaw) || !(args[2] instanceof Number pitch)) {
                return null;
            }
            double y = Math.toRadians(yaw.doubleValue());
            double p = Math.toRadians(pitch.doubleValue());
            double r = radius.doubleValue();
            return new Vector(-Math.sin(y) * Math.cos(p) * r, -Math.sin(p) * r, Math.cos(y) * Math.cos(p) * r);
        }, "[a] [new] spherical vector [(from|with)] [radius] %number%, [yaw] %number%(,|[,] and) [pitch] %number%");
        registrar.expression("ExprVectorSquaredLength", "number", info -> (context, args) -> map(args[0],
                value -> value instanceof Vector vector ? vector.lengthSquared() : null),
                "[the] squared length[s] of %vectors%", "%vectors%'[s] squared length[s]");
        registrar.expression("ExprVectorDotProduct", "number", info -> (context, args) ->
                args[0] instanceof Vector a && args[1] instanceof Vector b ? a.dot(b) : null, "%vector% dot %vector%");
        registrar.expression("ExprVectorCrossProduct", "vector", info -> (context, args) ->
                args[0] instanceof Vector a && args[1] instanceof Vector b ? a.getCrossProduct(b) : null,
                "%vector% cross %vector%");
        registrar.expression("ExprVectorAngleBetween", "number", info -> (context, args) ->
                args[0] instanceof Vector a && args[1] instanceof Vector b ? angle(a, b) : null,
                "[the] angle between [[the] vectors] %vector% and %vector%");
        registrar.expression("ExprVectorFromYawAndPitch", "vector", info -> (context, args) -> {
            if (!(args[0] instanceof Number yaw) || !(args[1] instanceof Number pitch)) {
                return null;
            }
            double y = Math.toRadians(yaw.doubleValue());
            double p = Math.toRadians(pitch.doubleValue());
            return new Vector(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        }, "[a] [new] vector (from|with) yaw %number% and pitch %number%");
        registrar.expression("ExprVectorRotateAroundAnother", "vector", info -> (context, args) -> map(args[0],
                value -> value instanceof Vector vector && args[1] instanceof Vector axis && args[2] instanceof Number by
                        ? vector.clone().rotateAroundAxis(axis, Math.toRadians(by.doubleValue())) : null),
                "%vectors% rotated around %vector% by %number% [degrees]");
        registrar.effect("EffVectorRotateAroundAnother", info -> (context, args) -> {
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Vector vector && args[1] instanceof Vector axis && args[2] instanceof Number by) {
                    vector.rotateAroundAxis(axis, Math.toRadians(by.doubleValue()));
                }
            }
        }, "rotate %vectors% around %vector% by %number% [degrees]");
        registrar.expression("ExprYawPitch", "number", info -> new YawPitch(info.hasTag("pitch")),
                "[the] (yaw|pitch:pitch)[s] of %entities/locations%", "%entities/locations%'[s] (yaw|pitch:pitch)[s]");
        registrar.condition("CondHasLineOfSight", info -> {
            boolean negated = info.pattern() >= 1;
            return (context, args) -> {
                Object[] viewers = (Object[]) args[0];
                Object[] targets = (Object[]) args[1];
                if (viewers.length == 0 || targets.length == 0) {
                    return negated;
                }
                for (Object viewer : viewers) {
                    for (Object target : targets) {
                        if (!(viewer instanceof LivingEntity living)) {
                            return false;
                        }
                        boolean sees = switch (target) {
                            case Entity entity -> living.hasLineOfSight(entity);
                            case Location location -> living.hasLineOfSight(location);
                            default -> false;
                        };
                        if (sees == negated) {
                            return false;
                        }
                    }
                }
                return true;
            };
        }, "%livingentities% (has|have) [a] [direct] line of sight to %entities/locations%",
                "%livingentities% (doesn't|does not|do not|don't) have [a] [direct] line of sight to %entities/locations%",
                "%livingentities% (has|have) no [direct] line of sight to %entities/locations%");
        registrar.expression("ExprLowestHighestSolidBlock", "block", info -> {
            boolean highest = !info.hasTag("lowest");
            return (context, args) -> map(args[0], value -> {
                if (!(value instanceof Location location) || location.getWorld() == null) {
                    return null;
                }
                World world = location.getWorld();
                if (highest) {
                    return world.getHighestBlockAt(location);
                }
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
                    if (block.getType().isSolid()) {
                        return block;
                    }
                }
                return null;
            });
        }, "[the] (highest|lowest:lowest) [solid] block[s] (at|of) %locations%");
        registrar.condition("CondIsWithin", info -> {
            boolean negated = info.pattern() % 2 == 1;
            boolean between = info.pattern() < 2;
            boolean subjectsAnd = info.and(0);
            boolean containersAnd = info.and(1);
            return (context, args) -> negated != Condition.check((Object[]) args[0], subjectsAnd, subject -> {
                Location location = subject instanceof Entity entity ? entity.getLocation()
                        : subject instanceof Block block ? block.getLocation().add(0.5, 0.5, 0.5)
                        : subject instanceof Location l ? l : null;
                if (location == null) {
                    return false;
                }
                if (between && args[1] instanceof Location a && args[2] instanceof Location b) {
                    return a.getWorld() == location.getWorld() && org.bukkit.util.BoundingBox.of(a, b).contains(location.toVector());
                }
                Object[] containers = between ? new Object[] {args[1], args[2]} : (Object[]) args[1];
                return Condition.check(containers, between || containersAnd, container -> within(location, container));
            });
        }, "%locations/entities/blocks% (is|are) within %object% and %object%",
                "%locations/entities/blocks% (isn't|is not|aren't|are not) within %object% and %object%",
                "%locations/entities/blocks% (is|are) (within|inside [of]) %entities/chunks/worlds/worldborders/blocks%",
                "%locations/entities/blocks% (isn't|is not|aren't|are not) (within|inside [of]) %entities/chunks/worlds/worldborders/blocks%");
        Map<String, World.Environment> environments = new java.util.LinkedHashMap<>();
        environments.put("normal", World.Environment.NORMAL);
        environments.put("overworld", World.Environment.NORMAL);
        environments.put("the overworld", World.Environment.NORMAL);
        environments.put("nether", World.Environment.NETHER);
        environments.put("the nether", World.Environment.NETHER);
        environments.put("end", World.Environment.THE_END);
        environments.put("the end", World.Environment.THE_END);
        environments.put("custom", World.Environment.CUSTOM);
        registrar.namedType("environment", "environment", World.Environment.class, environments);
        registrar.expression("ExprWorldEnvironment", "environment", info -> (context, args) -> map(args[0],
                value -> value instanceof World world ? world.getEnvironment() : null),
                "[the] [world] environment[s] of %worlds%", "%worlds%'[s] [world] environment[s]");
        registrar.expression("PropExprWXYZ", "number", info -> new Axis(info.hasTag("x") ? 0 : info.hasTag("y") ? 1
                : info.hasTag("w") ? 3 : 2),
                "[the] (w:w|x:x|y:y|z:z)( |-)[component[s]|coord[inate][s]|(pos[ition[s]]|loc[ation][s])] of %objects%",
                "%objects%'[s] (w:w|x:x|y:y|z:z)( |-)[component[s]|coord[inate][s]|(pos[ition[s]]|loc[ation][s])]");
        Locations.registerOffsetAndSpawn(registrar);
        SettableProperty.register(registrar, "ExprVectorLength", "number", Vector.class, Number.class, Vector::length,
                (vector, length) -> {
                    double current = vector.length();
                    if (current != 0) {
                        vector.multiply(length.doubleValue() / current);
                    }
                }, "[the] (vector|standard|normal) length[s] of %vectors%", "%vectors%'[s] (vector|standard|normal) length[s]");
    }

    /** Values Skript's test environment provides: the test world, a location in it, and the block there. */
    static void registerTesting(Registrar registrar) {
        registrar.expression("TestWorld", "world", info -> (context, args) -> Bukkit.getWorlds().getFirst(),
                "[the] test(-| )world");
        // A player who has never joined; the test server is in offline mode, so the name isn't looked up online.
        registrar.expression("TestOfflinePlayer", "offlineplayer", info -> (context, args) -> Bukkit.getOfflinePlayer("Njol"),
                "[the] test(-| )offline(-| )player");
        registrar.expression("TestLocation", "location", info -> (context, args) -> testLocation(), "[the] test(-| )location");
        // Skript's test block: ten blocks east of the test location, out of the way of what's tested there.
        registrar.expression("TestBlock", "block", info -> (context, args) -> testLocation().add(10, 0, 0).getBlock(),
                "[the] test(-| )block");
        // A test's event values are the test environment's: event-location is the test location.
        Class<co.xenastudios.neoskript.runtime.testing.TestEvent> test = co.xenastudios.neoskript.runtime.testing.TestEvent.class;
        registrar.eventValue(test, "location", event -> testLocation());
        registrar.eventValue(test, "block", event -> testLocation().add(10, 0, 0).getBlock());
        registrar.eventValue(test, "world", event -> Bukkit.getWorlds().getFirst());
    }

    /** Skript's test location: a block above the test world's spawn. */
    private static Location testLocation() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation().add(0, 1, 0);
    }

    /** {@code the health of %livingentities%}, in hearts as in Skript, which can be set. */
    private static final class Health implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> health = new ArrayList<>();
            for (Object entity : (Object[]) arguments[0]) {
                if (entity instanceof LivingEntity living) {
                    health.add(living.getHealth() / 2);
                }
            }
            return health.size() == 1 ? health.getFirst() : health.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            double hearts = delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
            for (Object entity : (Object[]) arguments[0]) {
                if (entity instanceof LivingEntity living) {
                    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                    double limit = max == null ? 20 : max.getValue();
                    double health = switch (mode) {
                        case ADD -> living.getHealth() + hearts * 2;
                        case REMOVE -> living.getHealth() - hearts * 2;
                        default -> hearts * 2;
                    };
                    living.setHealth(Math.max(0, Math.min(limit, health)));
                }
            }
        }
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }


    /**
     * {@code the x component of %vectors%}, {@code the y-coordinate of %locations%}: one axis of each, which
     * can be set, added to and taken from. Vectors and locations change in place, as when in a variable.
     */
    private record Axis(int axis) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : (Object[]) arguments[0]) {
                if (value instanceof org.joml.Quaternionf q) {
                    // A quaternion's parts are floats, and compared as floats.
                    values.add(axis == 0 ? q.x : axis == 1 ? q.y : axis == 2 ? q.z : q.w);
                    continue;
                }
                Vector vector = axis == 3 ? null : switch (value) {
                    case Location location -> location.toVector();
                    case Vector v -> v;
                    case Block block -> block.getLocation().toVector();
                    default -> null;
                };
                if (vector != null) {
                    values.add(axis == 0 ? vector.getX() : axis == 1 ? vector.getY() : vector.getZ());
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) {
                return;
            }
            double by = mode == Mode.REMOVE ? -number.doubleValue() : number.doubleValue();
            for (Object value : (Object[]) arguments[0]) {
                switch (value) {
                    case org.joml.Quaternionf q -> {
                        float current = axis == 0 ? q.x : axis == 1 ? q.y : axis == 2 ? q.z : q.w;
                        float next = (float) (mode == Mode.SET ? by : current + by);
                        switch (axis) {
                            case 0 -> q.x = next;
                            case 1 -> q.y = next;
                            case 2 -> q.z = next;
                            default -> q.w = next;
                        }
                    }
                    case Vector vector when axis == 3 -> {}
                    case Location location when axis == 3 -> {}
                    case Vector vector -> vector.copy(changed(vector, by, mode));
                    case Location location -> {
                        Vector moved = changed(location.toVector(), by, mode);
                        location.set(moved.getX(), moved.getY(), moved.getZ());
                    }
                    default -> {}
                }
            }
        }

        private Vector changed(Vector vector, double by, Mode mode) {
            double current = axis == 0 ? vector.getX() : axis == 1 ? vector.getY() : vector.getZ();
            double next = mode == Mode.SET ? by : current + by;
            Vector result = vector.clone();
            switch (axis) {
                case 0 -> result.setX(next);
                case 1 -> result.setY(next);
                default -> result.setZ(next);
            }
            return result;
        }
    }

    /** The angle between two vectors in degrees, worked out in doubles: NaN if either has no length. */
    private static double angle(Vector a, Vector b) {
        double cosine = a.dot(b) / Math.sqrt(a.lengthSquared() * b.lengthSquared());
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine))));
    }

    /** Each value through {@code function}, dropping nothing results; one value alone if there's one. */
    private static Object map(Object values, java.util.function.Function<Object, Object> function) {
        List<Object> results = new ArrayList<>();
        for (Object value : values instanceof Object[] array ? array : new Object[] {values}) {
            Object result = value == null ? null : function.apply(value);
            if (result != null) {
                results.add(result);
            }
        }
        return results.size() == 1 ? results.getFirst() : results.toArray();
    }

    /** {@code the yaw of %entities/locations%}: settable, and locations change in place. */
    private record YawPitch(boolean pitch) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], value -> switch (value) {
                case Entity entity -> (double) (pitch ? entity.getLocation().getPitch() : yaw(entity.getLocation().getYaw()));
                case Location location -> (double) (pitch ? location.getPitch() : yaw(location.getYaw()));
                default -> null;
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE || mode == Mode.RESET
                    ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            float by = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.floatValue() : 0;
            if (!Float.isFinite(by)) {
                // As in Skript, infinity and NaN change nothing.
                return;
            }
            if (pitch && (mode == Mode.ADD || mode == Mode.REMOVE)) {
                // Minecraft's pitch is upside down: adding looks up, as in Skript.
                by = -by;
            }
            for (Object value : (Object[]) arguments[0]) {
                switch (value) {
                    case Location location -> {
                        float next = next(pitch ? location.getPitch() : location.getYaw(), by, mode);
                        if (pitch) {
                            location.setPitch(next);
                        } else {
                            location.setYaw(next);
                        }
                    }
                    case Entity entity -> {
                        Location at = entity.getLocation();
                        float yaw = pitch ? at.getYaw() : next(at.getYaw(), by, mode);
                        float pitchValue = pitch ? next(at.getPitch(), by, mode) : at.getPitch();
                        entity.setRotation(yaw, pitchValue);
                    }
                    default -> {}
                }
            }
        }

        /** A yaw as Skript gives it: from 0 up to 360. */
        private static float yaw(float yaw) {
            float normal = Location.normalizeYaw(yaw);
            return normal < 0 ? normal + 360 : normal;
        }

        private static float next(float current, float by, Mode mode) {
            return switch (mode) {
                case ADD -> current + by;
                case REMOVE -> current - by;
                case RESET -> 0;
                default -> by;
            };
        }
    }

    /** Whether {@code location} is within {@code container}: an entity's or block's shape, a chunk, a world or a border. */
    private static boolean within(Location location, Object container) {
        return switch (container) {
            case Entity entity -> entity.getWorld() == location.getWorld()
                    && entity.getBoundingBox().contains(location.toVector());
            case Block block -> {
                if (block.getWorld() != location.getWorld()) {
                    yield false;
                }
                // Against the block's real shape, as for stairs or carpet.
                Vector inside = location.toVector().subtract(block.getLocation().toVector());
                for (org.bukkit.util.BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                    if (box.contains(inside)) {
                        yield true;
                    }
                }
                yield false;
            }
            case org.bukkit.Chunk chunk -> chunk.getWorld() == location.getWorld()
                    && chunk.getX() == location.getBlockX() >> 4 && chunk.getZ() == location.getBlockZ() >> 4;
            case World world -> world == location.getWorld();
            case org.bukkit.WorldBorder border -> border.isInside(location);
            case null, default -> false;
        };
    }

    /** Copies a container item's name and contents to the block placed for it, as Skript does. */
    private static void copyContainer(Block block, ItemType item) {
        if (!(block.getState() instanceof org.bukkit.block.Container container)
                || !(item.stack().getItemMeta() instanceof org.bukkit.inventory.meta.ItemMeta meta)) {
            return;
        }
        if (meta.displayName() != null) {
            container.customName(meta.displayName());
        }
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta stateMeta && stateMeta.hasBlockState()
                && stateMeta.getBlockState() instanceof org.bukkit.block.Container from) {
            org.bukkit.inventory.Inventory source = from.getSnapshotInventory();
            org.bukkit.inventory.Inventory target = container.getSnapshotInventory();
            for (int i = 0; i < source.getSize() && i < target.getSize(); i++) {
                org.bukkit.inventory.ItemStack stack = source.getItem(i);
                if (stack != null) {
                    target.setItem(i, stack.clone());
                }
            }
        }
        container.update();
    }
}
