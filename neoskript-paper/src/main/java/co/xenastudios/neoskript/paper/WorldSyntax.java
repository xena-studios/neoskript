/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** The world: entity types and spawning, entities' health, blocks, vectors and coordinates. */
final class WorldSyntax {

    private WorldSyntax() {}

    /** The entities spawned last on each thread, for {@code the last spawned pig}. */
    private static final ThreadLocal<Entity> LAST_SPAWNED = new ThreadLocal<>();

    static void register(Registrar registrar) {
        registrar.type("entitytype", "entity type", EntityKind.class);
        registrar.type("livingentity", "living entity", LivingEntity.class);
        registrar.type("block", "block", Block.class);
        registrar.type("vector", "vector", Vector.class);
        registrar.subtype("livingentity", "entity");
        registrar.subtype("player", "livingentity");
        registrar.literalParser("entitytype", EntityKind::parse);
        registrar.parser("entitytype", EntityKind::parse);
        registrar.text(Entity.class, entity -> entity.customName() != null
                ? Messages.LEGACY_SECTION.serialize(entity.customName()) : EntityKind.name(entity.getType()));
        registrar.text(Block.class, block -> ItemType.name(block.getType()));
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
            return (context, args) -> {
                List<Object> chunks = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    if (loaded && value instanceof org.bukkit.World world) {
                        chunks.addAll(List.of(world.getLoadedChunks()));
                    } else if (!loaded && value instanceof Location location && location.getWorld() != null) {
                        chunks.add(location.getChunk());
                    }
                }
                return chunks.size() == 1 ? chunks.getFirst() : chunks.toArray();
            };
        }, "[(all [[of] the]|the)] chunk[s] (of|at) %locations%", "%locations%'[s] chunk[s]",
                "[(all [[of] the]|the)] loaded chunks (of|in) %worlds%");
        registrar.expression("ExprMidpoint", "object", info -> (context, args) -> {
            if (args[0] instanceof Location a && args[1] instanceof Location b) {
                return a.getWorld() != b.getWorld() ? null : new Location(a.getWorld(), (a.getX() + b.getX()) / 2,
                        (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
            }
            if (args[0] instanceof Vector a && args[1] instanceof Vector b) {
                return a.getMidpoint(b);
            }
            return null;
        }, "[the] mid[-]point (of|between) %location/vector% and %location/vector%");

        registrar.expression("ExprWorlds", "world", info -> (context, args) -> Bukkit.getWorlds().toArray(),
                "[(all [[of] the]|the)] worlds");
        registrar.expression("ExprVectorFromXYZ", "vector", info -> (context, args) -> new Vector(number(args[0]),
                number(args[1]), number(args[2])), "[a] [new] vector [(from|at|to)] %number%,[ ]%number%(,[ ]| and )%number%");
        registrar.condition("CondIsLoaded", info -> {
            boolean negated = info.hasTag("not");
            int pattern = info.pattern();
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                if (values.length == 0) {
                    return false;
                }
                for (Object value : values) {
                    boolean loaded = switch (value) {
                        case Location location -> location.getWorld() != null && location.isChunkLoaded();
                        case org.bukkit.Chunk chunk -> chunk.isLoaded();
                        case org.bukkit.World world -> Bukkit.getWorld(world.getUID()) != null;
                        default -> false;
                    };
                    if (loaded == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "chunk[s] (at|of) %locations% (is|are)[not:(n't| not)] loaded", "%chunks/worlds% (is|are)[not:(n't| not)] loaded",
                "world[s] %worlds% (is|are)[not:(n't| not)] loaded");

        registerEntities(registrar);
        registerBlocks(registrar);
        registerVectors(registrar);
    }

    private static void registerEntities(Registrar registrar) {
        registrar.section("EffSecSpawn", "entity", info -> {
            boolean counted = info.pattern() == 1;
            return (context, args) -> {
                int amount = counted && args[0] instanceof Number n ? (int) Math.round(n.doubleValue()) : 1;
                Object[] types = (Object[]) args[counted ? 1 : 0];
                Object[] locations = args[counted ? 2 : 1] == null ? new Object[0] : (Object[]) args[counted ? 2 : 1];
                List<Object> spawned = new ArrayList<>();
                for (Object place : locations) {
                    if (!(place instanceof Location location) || location.getWorld() == null) {
                        continue;
                    }
                    for (Object type : types) {
                        // Categories like 'monster' can't be spawned, only kinds that are one entity type.
                        if (!(type instanceof EntityKind kind) || !kind.spawnable()) {
                            continue;
                        }
                        for (int i = 0; i < amount; i++) {
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
        }, "(spawn|summon) %entitytypes% [(at|on) %locations%]",
                "(spawn|summon) %number% of %entitytypes% [(at|on) %locations%]");
        registrar.expression("ExprLastSpawnedEntity", "entity", info -> (context, args) -> {
            Entity last = LAST_SPAWNED.get();
            return last != null && (args[0] == null || (args[0] instanceof EntityKind kind && kind.matches(last))) ? last : null;
        }, "[the] [last[ly]] (spawned|shot) %*entitytype%");

        // Replaces the players-only version from step 4: 'all players', 'all zombies in world "world"'.
        registrar.expression("ExprEntities", "entity", info -> (context, args) -> {
            List<Object> found = new ArrayList<>();
            List<Object> types = List.of((Object[]) args[0]);
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
                    for (Object type : types) {
                        if (type instanceof EntityKind kind && kind.matches(entity)) {
                            found.add(entity);
                            break;
                        }
                    }
                }
            }
            return found.toArray();
        // 'all' or 'the' is needed: 'baby chicken' alone is the entity type.
        }, "(all [[of] the]|the) %*entitytypes% [(in|of) world[s] %worlds%]");

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

    private static void registerBlocks(Registrar registrar) {
        registrar.expression("ExprBlock-2", "block", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (!(args[0] instanceof Location location) || location.getWorld() == null) {
                    return null;
                }
                return switch (pattern) {
                    case 1 -> location.getBlock().getRelative(0, 1, 0);
                    case 2 -> location.getBlock().getRelative(0, -1, 0);
                    default -> location.getBlock();
                };
            };
        }, "[the] block (at|of) %location%", "[the] block above %location%", "[the] block below %location%");
        // 'set block at X to stone': blocks become the item's block.
        registrar.changer(Block.class, Changeable.Mode.SET, (block, values) -> {
            for (Object value : values) {
                Material material = value instanceof ItemType item ? item.material()
                        : value instanceof org.bukkit.inventory.ItemStack stack ? stack.getType() : null;
                if (material != null && material.isBlock()) {
                    block.setType(material);
                    return;
                }
            }
        });
        registrar.changer(Block.class, Changeable.Mode.DELETE, (block, values) -> block.setType(Material.AIR));
    }

    private static void registerVectors(Registrar registrar) {
        registrar.function("vector", List.of(Parameter.of("x", "number"), Parameter.of("y", "number"),
                Parameter.of("z", "number")), "vector", false, args -> new Vector(number(args[0]), number(args[1]),
                number(args[2])));
        registrar.expression("PropExprWXYZ", "number", info -> {
            char axis = info.hasTag("x") ? 'x' : info.hasTag("y") ? 'y' : 'z';
            return (context, args) -> {
                List<Object> values = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    Vector vector = switch (value) {
                        case Location location -> location.toVector();
                        case Vector v -> v;
                        case Block block -> block.getLocation().toVector();
                        default -> null;
                    };
                    if (vector != null) {
                        values.add(axis == 'x' ? vector.getX() : axis == 'y' ? vector.getY() : vector.getZ());
                    }
                }
                return values.size() == 1 ? values.getFirst() : values.toArray();
            };
        }, "[the] (x:x|y:y|z:z)( |-)[component[s]|coord[inate][s]|(pos[ition[s]]|loc[ation][s])] of %objects%",
                "%objects%'[s] (x:x|y:y|z:z)( |-)[component[s]|coord[inate][s]|(pos[ition[s]]|loc[ation][s])]");
        registrar.expression("ExprLocationVectorOffset", "location", info -> (context, args) -> {
            if (!(args[0] instanceof Location location)) {
                return null;
            }
            Location offset = location.clone();
            for (Object vector : (Object[]) args[1]) {
                if (vector instanceof Vector v) {
                    offset.add(v);
                }
            }
            return offset;
        }, "%location% offset by [[the] vectors] %vectors%", "%location%[ ]~[~][ ]%vectors%");
        registrar.expression("ExprVectorLength", "number", info -> (context, args) -> {
            List<Object> lengths = new ArrayList<>();
            for (Object vector : (Object[]) args[0]) {
                if (vector instanceof Vector v) {
                    lengths.add(v.length());
                }
            }
            return lengths.size() == 1 ? lengths.getFirst() : lengths.toArray();
        }, "[the] (vector|standard|normal) length[s] of %vectors%", "%vectors%'[s] (vector|standard|normal) length[s]");
    }

    /** Values Skript's test environment provides: the test world, a location in it, and the block there. */
    static void registerTesting(Registrar registrar) {
        registrar.expression("TestWorld", "world", info -> (context, args) -> Bukkit.getWorlds().getFirst(),
                "[the] test-world");
        registrar.expression("TestLocation", "location", info -> (context, args) -> testLocation(), "[the] test-location");
        registrar.expression("TestBlock", "block", info -> (context, args) -> testLocation().getBlock(), "[the] test-block");
        // A test's event values are the test environment's: event-location is the test location.
        Class<co.xenastudios.neoskript.runtime.testing.TestEvent> test = co.xenastudios.neoskript.runtime.testing.TestEvent.class;
        registrar.eventValue(test, "location", event -> testLocation());
        registrar.eventValue(test, "block", event -> testLocation().getBlock());
        registrar.eventValue(test, "world", event -> Bukkit.getWorlds().getFirst());
    }

    private static Location testLocation() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation().toBlockLocation();
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

}
