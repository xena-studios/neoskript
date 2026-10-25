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
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/** Locations and worlds: where things are, spawn points, distances, teleporting. */
final class Locations {

    private Locations() {}

    /**
     * The location offset ({@code X ~ vector(...)}) and then spawn, registered after blocks as Skript orders them:
     * {@code block at X ~ v} is the block at the offset, and {@code spawn of W ~ v} is offset from the spawn.
     */
    static void registerOffsetAndSpawn(Registrar registrar) {
        registrar.expression("ExprLocationVectorOffset", "location", info -> (context, args) -> {
            if (!(args[0] instanceof Location location)) {
                return null;
            }
            boolean local = info.hasTag("facingrelative");
            Location offset = location.clone();
            for (Object vector : (Object[]) args[1]) {
                if (vector instanceof org.bukkit.util.Vector v) {
                    offset.add(local ? facingRelative(offset, v) : v);
                }
            }
            return offset;
        }, "%location% offset by [[the] vectors] %vectors% [facingrelative:using local axes]",
                "%location%[ ]~[~][ ]%vectors%");
        registrar.expression("ExprSpawn", "location", info -> new Spawn(info.time()),
                "[the] spawn[s] [(point|location)[s]] [of %worlds%]", "%worlds%'[s] spawn[s] [(point|location)[s]]");
        // 'past spawn' in a spawn change event is where the spawn was.
        registrar.timeStates("ExprSpawn", 0, org.bukkit.event.world.SpawnChangeEvent.class);
    }

    /**
     * {@code offset} turned to the way {@code location} faces, as Minecraft's local coordinates are: Skript's
     * local axes, worked out in floats as Skript does.
     */
    private static org.bukkit.util.Vector facingRelative(Location location, org.bukkit.util.Vector offset) {
        float yaw = (float) Math.toRadians(-location.getYaw());
        float pitch = (float) Math.toRadians(location.getPitch());
        org.joml.Vector3f local = offset.toVector3f();
        new org.joml.Quaternionf().rotateYXZ(yaw, pitch, 0f).transform(local);
        return new org.bukkit.util.Vector(local.x, local.y, local.z);
    }

    private static World defaultWorld() {
        World world = Bukkit.getWorld("world");
        return world != null || Bukkit.getWorlds().isEmpty() ? world : Bukkit.getWorlds().getFirst();
    }

    static void register(Registrar registrar) {
        registrar.type("location", "location", Location.class);
        registrar.text(Location.class, location -> "x: " + Values.decimal(location.getX())
                + ", y: " + Values.decimal(location.getY()) + ", z: " + Values.decimal(location.getZ())
                + ", yaw: " + Values.decimal(location.getYaw()) + ", pitch: " + Values.decimal(location.getPitch())
                + (location.getWorld() == null ? "" : " in '" + location.getWorld().getName() + "'"));
        registrar.converter(Entity.class, Location.class, Entity::getLocation);
        // Locations are equal with their yaw and pitch normalized, and coordinates as close as numbers need to be.
        registrar.comparison(Location.class, Location.class, (a, b) -> a.getWorld() == b.getWorld()
                && close(a.getX(), b.getX()) && close(a.getY(), b.getY()) && close(a.getZ(), b.getZ())
                && Location.normalizeYaw(a.getYaw()) + 0.0f == Location.normalizeYaw(b.getYaw()) + 0.0f
                && Location.normalizePitch(a.getPitch()) + 0.0f == Location.normalizePitch(b.getPitch()) + 0.0f);
        // A block's location is its centre, looking where the block faces, as Skript's BlockUtils.getLocation.
        registrar.converter(Block.class, Location.class, Direction::blockLocation);
        // A location is a block when the block's location is it, as Skript compares them through that converter.
        registrar.comparison(Location.class, Block.class, (location, block) -> location.getWorld() == block.getWorld()
                && location.toVector().equals(block.getLocation().add(0.5, 0.5, 0.5).toVector()));
        // Not Skript's: only for placeholders, so 'event-world' isn't the world of an event's location.
        registrar.converter(Location.class, World.class, Location::getWorld, Registrar.PLACEHOLDERS_ONLY);
        registrar.converter(Entity.class, World.class, Entity::getWorld, Registrar.PLACEHOLDERS_ONLY);
        // 'spawn of "world"': a world by its name.
        registrar.converter(String.class, World.class, Bukkit::getWorld);

        registrar.expression("ExprLocationOf", "location", info -> (context, args) ->
                        args[0] instanceof Location location ? location.clone() : null,
                "(location|position) of %location%", "%location%'[s] (location|position)");


        // The world a location, entity or chunk is in; a location's can be changed.
        registrar.expression("ExprWorld", "world", info -> {
            SettableProperty<Object, World> world = new SettableProperty<>(Object.class, World.class, owner -> switch (owner) {
                case Location location -> location.getWorld();
                case Entity entity -> entity.getWorld();
                case org.bukkit.Chunk chunk -> chunk.getWorld();
                default -> null;
            }, (owner, changed) -> {
                if (owner instanceof Location location) {
                    location.setWorld(changed);
                }
            });
            return info.time() == 0 ? world : new TeleportWorld(world, info.time());
        }, "[the] world of %locations/entities/chunks%", "%locations/entities/chunks%'[s] world");
        // 'past world of player' in a teleport event is the world teleported from, the future one the world to.
        registrar.timeStates("ExprWorld", 0, org.bukkit.event.player.PlayerTeleportEvent.class);
        registrar.expression("ExprWorldFromName", "world", info -> (context, args) -> Bukkit.getWorld(Values.text(args[0])),
                "[the] world [(named|with name)] %text%");

        registrar.expression("ExprDistance", "number", info -> (context, args) -> {
            if (args[0] instanceof Location a && args[1] instanceof Location b && a.getWorld() == b.getWorld()) {
                return a.distance(b);
            }
            return null;
        }, "[the] distance between %location% and %location%");

        // What a teleport keeps: 'teleport X to Y retaining passengers and xyz velocity'.
        java.util.Map<String, TeleportFlags> flags = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<String, io.papermc.paper.entity.TeleportFlag[]> flag = (names, kept) -> {
            String[] all = names.split(", ");
            TeleportFlags value = new TeleportFlags(all[0], List.of(kept));
            for (String name : all) {
                flags.put(name, value);
            }
        };
        flag.accept("opened inventory, open inventory, inventory",
                new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY});
        flag.accept("passengers, passenger",
                new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS});
        flag.accept("vehicle",
                new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_VEHICLE});
        flag.accept("directional velocity, pitch and yaw velocity, yaw and pitch velocity",
                new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION});
        flag.accept("pitch velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.PITCH});
        flag.accept("yaw velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.YAW});
        flag.accept("xyz velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X,
                io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Y, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z});
        flag.accept("x velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X});
        flag.accept("y velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Y});
        flag.accept("z velocity", new io.papermc.paper.entity.TeleportFlag[] {io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z});
        registrar.namedType("teleportflag", "teleport flag", TeleportFlags.class, flags);

        registrar.effect("EffTeleport", info -> {
            boolean directed = info.pattern() == 1;
            return (context, args) -> {
                if (!(Values.single(args[directed ? 2 : 1]) instanceof Location given)) {
                    return;
                }
                Object direction = directed ? Values.single(args[1]) : null;
                List<io.papermc.paper.entity.TeleportFlag> kept = new java.util.ArrayList<>();
                for (Object value : Values.array(args[directed ? 3 : 2])) {
                    if (value instanceof TeleportFlags each) {
                        kept.addAll(each.flags());
                    }
                }
                io.papermc.paper.entity.TeleportFlag[] keep = kept.toArray(io.papermc.paper.entity.TeleportFlag[]::new);
                for (Object entity : (Object[]) args[0]) {
                    if (entity instanceof Entity e) {
                        Location target = given.clone();
                        // A location without a world is in the entity's world.
                        if (target.getWorld() == null) {
                            target.setWorld(e.getWorld());
                        }
                        if (direction instanceof Direction d) {
                            target = d.getRelative(target);
                        }
                        try {
                            e.teleport(target, keep);
                        } catch (UnsupportedOperationException folia) {
                            // Folia only teleports asynchronously.
                            e.teleportAsync(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, keep);
                        }
                    }
                }
            };
        }, "[force] teleport %entities% to %location% [[while] retaining %teleportflags%]",
                "[force] teleport %entities% %direction% %location% [[while] retaining %teleportflags%]");

        registrar.function("location", List.of(Parameter.of("x", "number"), Parameter.of("y", "number"),
                Parameter.of("z", "number"),
                // Left out, Skript's default: the world called "world"; given as nothing, no world.
                Parameter.defaulting("world", "world", Locations::defaultWorld),
                new Parameter("yaw", "number", false, true), new Parameter("pitch", "number", false, true)),
                // The world may be given by name: location(0, 0, 0, "world"). A coordinate left unset gives nothing.
                "location", false, args -> args[0] == null || args[1] == null || args[2] == null ? null
                        : new Location(args[3] instanceof World w ? w : args[3] instanceof String name ? Bukkit.getWorld(name) : null,
                                number(args[0]), number(args[1]), number(args[2]), (float) number(args[4]), (float) number(args[5])));
        registrar.function("world", List.of(Parameter.of("name", "string")), "world", false,
                args -> Bukkit.getWorld(Values.text(args[0])));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /** {@code the spawn of world "x"}, which can be set. */
    /**
     * The world of what's given before or after a teleport, as Skript's: the world teleported from or to, for
     * each of them.
     */
    private record TeleportWorld(SettableProperty<Object, World> world, int time) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(context.event() instanceof org.bukkit.event.player.PlayerTeleportEvent teleport)) {
                return world.get(context, arguments);
            }
            Location where = time > 0 ? teleport.getTo() : teleport.getFrom();
            List<Object> worlds = new ArrayList<>();
            for (Object owner : PlayerSyntax.owners(arguments[0])) {
                if (owner instanceof Location || owner instanceof Entity || owner instanceof org.bukkit.Chunk) {
                    worlds.add(where.getWorld());
                }
            }
            return worlds.size() == 1 ? worlds.getFirst() : worlds.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return world.acceptChange(mode);
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            world.change(context, arguments, delta, mode);
        }
    }

    /** The spawn of worlds; before a spawn change event ({@code past spawn}), where it was. */
    private record Spawn(int time) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (time < 0 && context.event() instanceof org.bukkit.event.world.SpawnChangeEvent change) {
                return change.getPreviousLocation();
            }
            List<Object> spawns = new ArrayList<>();
            for (Object world : worlds(arguments)) {
                if (world instanceof World w) {
                    spawns.add(w.getSpawnLocation());
                }
            }
            return spawns.size() == 1 ? spawns.getFirst() : spawns.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Location.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta.length > 0 && delta[0] instanceof Location location) {
                for (Object world : worlds(arguments)) {
                    if (world instanceof World w) {
                        w.setSpawnLocation(location);
                    }
                }
            }
        }

        private static Object[] worlds(Object[] arguments) {
            return arguments[0] == null ? new Object[] {Bukkit.getWorlds().getFirst()} : (Object[]) arguments[0];
        }
    }

    /** Whether two coordinates are equal, give or take floating point error, as numbers are compared. */
    private static boolean close(double a, double b) {
        return a == b || Math.abs(a - b) < 1e-10;
    }

    /** One of Skript's teleport flags: what a teleport keeps, as Paper's flags. */
    record TeleportFlags(String name, List<io.papermc.paper.entity.TeleportFlag> flags) {
        @Override
        public String toString() {
            return name;
        }
    }
}
