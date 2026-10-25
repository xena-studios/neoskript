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

    /** A location: its world, coordinates and rotation. */
    static final VariableCodec<Location> LOCATION = new VariableCodec<>() {
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
    };

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

        registrar.storage("location", Location.class, LOCATION);

        // Enums are saved by their constant's name, as Skript's enum serializer does.
        enumStorage(registrar, "clicktype", org.bukkit.event.inventory.ClickType.class);
        enumStorage(registrar, "damagecause", org.bukkit.event.entity.EntityDamageEvent.DamageCause.class);
        enumStorage(registrar, "difficulty", org.bukkit.Difficulty.class);
        enumStorage(registrar, "equipmentslot", org.bukkit.inventory.EquipmentSlot.class);
        enumStorage(registrar, "fireworktype", org.bukkit.FireworkEffect.Type.class);
        enumStorage(registrar, "soundcategory", org.bukkit.SoundCategory.class);
        enumStorage(registrar, "spawnreason", org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class);
        enumStorage(registrar, "teleportcause", org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class);
        enumStorage(registrar, "weathertype", WorldProperties.Weather.class);
        // An inventory type is Bukkit's enum in Skript.
        registrar.storage("inventorytype", Items.InventoryKind.class, new VariableCodec<>() {
            @Override
            public void encode(Items.InventoryKind kind, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("name", kind.name());
            }

            @Override
            public Items.InventoryKind decode(BinaryReader in) {
                in.beginObject();
                return new Items.InventoryKind(in.stringField("name"));
            }
        });
        // A time of day is its ticks, as Skript's Time saves its one field.
        registrar.storage("time", co.xenastudios.neoskript.runtime.TimeOfDay.class, new VariableCodec<>() {
            @Override
            public void encode(co.xenastudios.neoskript.runtime.TimeOfDay time, BinaryWriter out) {
                out.beginObject(1);
                out.intField("time", time.ticks());
            }

            @Override
            public co.xenastudios.neoskript.runtime.TimeOfDay decode(BinaryReader in) {
                in.beginObject();
                return new co.xenastudios.neoskript.runtime.TimeOfDay(in.intField("time"));
            }
        });
        // A block is its world and coordinates.
        registrar.storage("block", org.bukkit.block.Block.class, new VariableCodec<>() {
            @Override
            public void encode(org.bukkit.block.Block block, BinaryWriter out) {
                out.beginObject(4);
                out.objectField("world", "world");
                writeWorld(block.getWorld(), out);
                out.intField("x", block.getX());
                out.intField("y", block.getY());
                out.intField("z", block.getZ());
            }

            @Override
            public org.bukkit.block.Block decode(BinaryReader in) {
                in.beginObject();
                if (!in.objectField("world").equals("world")) {
                    return null;
                }
                World world = readWorld(in);
                int x = in.intField("x");
                int y = in.intField("y");
                int z = in.intField("z");
                return world == null ? null : world.getBlockAt(x, y, z);
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

    /** Saves values of an enum by their constant's name. */
    private static <E extends Enum<E>> void enumStorage(Registrar registrar, String name, Class<E> type) {
        registrar.storage(name, type, new VariableCodec<>() {
            @Override
            public void encode(E value, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("name", value.name());
            }

            @Override
            public E decode(BinaryReader in) {
                in.beginObject();
                try {
                    return Enum.valueOf(type, in.stringField("name"));
                } catch (IllegalArgumentException unknown) {
                    return null;
                }
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
