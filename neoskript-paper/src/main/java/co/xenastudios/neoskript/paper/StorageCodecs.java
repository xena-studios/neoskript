/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

/**
 * How worlds, locations and players are saved, matching Skript's {@code variables.csv} byte for byte. A
 * world that isn't loaded can't be read; the variable's bytes are then kept as they are.
 */
final class StorageCodecs {

    private StorageCodecs() {}

    static void register(Registrar registrar) {
        registrar.storage("world", World.class, new VariableCodec<>() {
            @Override
            public void encode(World world, BinaryWriter out) {
                writeWorld(world, out);
            }

            @Override
            public World decode(BinaryReader in) {
                return readWorld(in);
            }
        });

        registrar.storage("location", Location.class, new VariableCodec<>() {
            @Override
            public void encode(Location location, BinaryWriter out) {
                out.beginObject(6);
                out.objectField("world", "world");
                writeWorld(location.getWorld(), out);
                out.doubleField("x", location.getX());
                out.doubleField("y", location.getY());
                out.doubleField("z", location.getZ());
                out.floatField("pitch", location.getPitch());
                out.floatField("yaw", location.getYaw());
            }

            @Override
            public Location decode(BinaryReader in) {
                in.beginObject();
                if (!in.objectField("world").equals("world")) {
                    return null;
                }
                World world = readWorld(in);
                double x = in.doubleField("x");
                double y = in.doubleField("y");
                double z = in.doubleField("z");
                float pitch = in.floatField("pitch");
                float yaw = in.floatField("yaw");
                return world == null ? null : new Location(world, x, y, z, yaw, pitch);
            }
        });

        registrar.storage("offlineplayer", OfflinePlayer.class, new VariableCodec<>() {
            @Override
            public void encode(OfflinePlayer player, BinaryWriter out) {
                UUID uuid = player.getUniqueId();
                out.beginObject(1);
                out.objectField("uuid", "UUID");
                out.beginObject(2);
                out.longField("mostsigbits", uuid.getMostSignificantBits());
                out.longField("leastsigbits", uuid.getLeastSignificantBits());
            }

            @Override
            public OfflinePlayer decode(BinaryReader in) {
                in.beginObject();
                if (!in.objectField("uuid").equals("UUID")) {
                    return null;
                }
                in.beginObject();
                long most = in.longField("mostsigbits");
                long least = in.longField("leastsigbits");
                return Bukkit.getOfflinePlayer(new UUID(most, least));
            }
        });
    }

    private static void writeWorld(World world, BinaryWriter out) {
        out.beginObject(1);
        out.stringField("name", world.getName());
    }

    private static World readWorld(BinaryReader in) {
        in.beginObject();
        return Bukkit.getWorld(in.stringField("name"));
    }
}
