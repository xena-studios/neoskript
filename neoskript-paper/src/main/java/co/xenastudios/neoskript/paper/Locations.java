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

    static void register(Registrar registrar) {
        registrar.type("location", "location", Location.class);
        registrar.text(Location.class, location -> "x: " + Values.decimal(location.getX())
                + ", y: " + Values.decimal(location.getY()) + ", z: " + Values.decimal(location.getZ())
                + ", yaw: " + Values.decimal(location.getYaw()) + ", pitch: " + Values.decimal(location.getPitch())
                + (location.getWorld() == null ? "" : " in '" + location.getWorld().getName() + "'"));
        registrar.converter(Entity.class, Location.class, Entity::getLocation);
        registrar.converter(Block.class, Location.class, Block::getLocation);
        registrar.converter(Location.class, World.class, Location::getWorld);
        registrar.converter(Entity.class, World.class, Entity::getWorld);

        registrar.expression("ExprLocationOf", "location", info -> (context, args) ->
                        args[0] instanceof Location location ? location.clone() : null,
                "(location|position) of %location%", "%location%'[s] (location|position)");

        registrar.expression("ExprSpawn", "location", info -> new Spawn(), "[the] spawn[s] [(point|location)[s]] [of %worlds%]",
                "%worlds%'[s] spawn[s] [(point|location)[s]]");

        registrar.expression("ExprWorldFromName", "world", info -> (context, args) -> Bukkit.getWorld(Values.text(args[0])),
                "[the] world [(named|with name)] %text%");

        registrar.expression("ExprDistance", "number", info -> (context, args) -> {
            if (args[0] instanceof Location a && args[1] instanceof Location b && a.getWorld() == b.getWorld()) {
                return a.distance(b);
            }
            return null;
        }, "[the] distance between %location% and %location%");

        registrar.effect("EffTeleport", info -> (context, args) -> {
            if (!(args[1] instanceof Location location)) {
                return;
            }
            for (Object entity : (Object[]) args[0]) {
                if (entity instanceof Entity e) {
                    // A location without a world is in the entity's world.
                    Location target = location.getWorld() == null ? location.clone() : location;
                    if (target.getWorld() == null) {
                        target.setWorld(e.getWorld());
                    }
                    try {
                        e.teleport(target);
                    } catch (UnsupportedOperationException folia) {
                        // Folia only teleports asynchronously.
                        e.teleportAsync(target);
                    }
                }
            }
        }, "[force] teleport %entities% to %location%");

        registrar.function("location", List.of(Parameter.of("x", "number"), Parameter.of("y", "number"),
                Parameter.of("z", "number"), new Parameter("world", "world", false, true),
                new Parameter("yaw", "number", false, true), new Parameter("pitch", "number", false, true)),
                "location", false, args -> new Location(args[3] instanceof World w ? w : null,
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
}
