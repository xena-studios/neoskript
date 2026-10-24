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
            Location offset = location.clone();
            for (Object vector : (Object[]) args[1]) {
                if (vector instanceof org.bukkit.util.Vector v) {
                    offset.add(v);
                }
            }
            return offset;
        }, "%location% offset by [[the] vectors] %vectors%", "%location%[ ]~[~][ ]%vectors%");
        registrar.expression("ExprSpawn", "location", info -> new Spawn(), "[the] spawn[s] [(point|location)[s]] [of %worlds%]",
                "%worlds%'[s] spawn[s] [(point|location)[s]]");
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
        // A block's location is its centre, as in Skript.
        registrar.converter(Block.class, Location.class, block -> block.getLocation().add(0.5, 0.5, 0.5));
        registrar.converter(Location.class, World.class, Location::getWorld);
        registrar.converter(Entity.class, World.class, Entity::getWorld);
        // 'spawn of "world"': a world by its name.
        registrar.converter(String.class, World.class, Bukkit::getWorld);

        registrar.expression("ExprLocationOf", "location", info -> (context, args) ->
                        args[0] instanceof Location location ? location.clone() : null,
                "(location|position) of %location%", "%location%'[s] (location|position)");


        // The world a location, entity or chunk is in; a location's can be changed.
        registrar.expression("ExprWorld", "world", info -> new SettableProperty<>(Object.class, World.class, owner -> switch (owner) {
            case Location location -> location.getWorld();
            case Entity entity -> entity.getWorld();
            case org.bukkit.Chunk chunk -> chunk.getWorld();
            default -> null;
        }, (owner, world) -> {
            if (owner instanceof Location location) {
                location.setWorld(world);
            }
        }), "[the] world of %locations/entities/chunks%", "%locations/entities/chunks%'[s] world");
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
                        if (direction instanceof WorldSyntax.Direction d) {
                            target.add(d.from(target));
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
    private static final class Spawn implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
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
