/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Skript's {@code direction} ({@code ch.njol.skript.util.Direction}): either relative, a pitch, yaw and
 * length that turn with what they're applied to ({@code 2 meters to the left}), or absolute, an x, y and z
 * offset ({@code 5 north}).
 */
final class Direction {

    /** A direction that doesn't point anywhere, i.e. equal to 'at'. */
    static final Direction ZERO = new Direction(new double[] {0, 0, 0});

    /** A direction that points where the object it's applied to faces. */
    static final Direction IDENTITY = new Direction(0, 0, 1);

    /** Use this as pitch to force a horizontal direction. */
    static final double IGNORE_PITCH = 0xF1A7; // FLAT

    static final BlockFace BF_X = BlockFace.EAST;
    static final BlockFace BF_Y = BlockFace.UP;
    static final BlockFace BF_Z = BlockFace.SOUTH;

    // Rotation or offset, as in Skript.
    private final double pitchOrX;
    private final double yawOrY;
    private final double lengthOrZ;

    /** Whether this direction rotates with what it's applied to rather than only translating it. */
    private final boolean relative;

    /**
     * The distance written in the direction expression that made this ({@code 5} in {@code 5 above}), or
     * {@code null}: {@code blocks 5 above X} goes 5 blocks. Skript reads it off the expression; it's no part
     * of the direction's value, so it's left out of equality and storage.
     */
    private final Number writtenAmount;

    /** Whether this came from an absolute direction expression ({@code north}), which can follow another. */
    private final boolean chainable;

    Direction(double[] mod) {
        if (mod.length != 3) {
            throw new IllegalArgumentException();
        }
        this.relative = false;
        this.pitchOrX = mod[0];
        this.yawOrY = mod[1];
        this.lengthOrZ = mod[2];
        this.writtenAmount = null;
        this.chainable = false;
    }

    Direction(double pitch, double yaw, double length) {
        this(true, pitch, yaw, length, null, false);
    }

    Direction(BlockFace face, double length) {
        this(new Vector(face.getModX(), face.getModY(), face.getModZ()).normalize().multiply(length));
    }

    Direction(Vector vector) {
        this(false, vector.getX(), vector.getY(), vector.getZ(), null, false);
    }

    private Direction(boolean relative, double pitchOrX, double yawOrY, double lengthOrZ, Number writtenAmount,
            boolean chainable) {
        this.relative = relative;
        this.pitchOrX = pitchOrX;
        this.yawOrY = yawOrY;
        this.lengthOrZ = lengthOrZ;
        this.writtenAmount = writtenAmount;
        this.chainable = chainable;
    }

    /** This direction, as made by a direction expression with {@code amount} written (or {@code null}). */
    Direction written(Number amount, boolean chainable) {
        return new Direction(relative, pitchOrX, yawOrY, lengthOrZ, amount, chainable);
    }

    /** The distance written in the direction expression that made this, or {@code null}. */
    Number writtenAmount() {
        return writtenAmount;
    }

    /** Whether this came from an absolute direction expression, as {@code north}. */
    boolean chainable() {
        return chainable;
    }

    Location getRelative(Location location) {
        return location.clone().add(getDirection(location));
    }

    Location getRelative(Entity entity) {
        return entity.getLocation().add(getDirection(entity.getLocation()));
    }

    Location getRelative(Block block) {
        return block.getLocation().add(getDirection(block));
    }

    /** The offset without anything to be relative to: relative directions are relative to 0 pitch and yaw. */
    Vector getDirection() {
        if (!relative) {
            return new Vector(pitchOrX, yawOrY, lengthOrZ);
        }
        return getDirection(0, 0);
    }

    Vector getDirection(Location location) {
        if (!relative) {
            return new Vector(pitchOrX, yawOrY, lengthOrZ);
        }
        return getDirection(pitchOrX == IGNORE_PITCH ? 0 : pitchToRadians(location.getPitch()),
                yawToRadians(location.getYaw()));
    }

    Vector getDirection(Entity entity) {
        return getDirection(entity.getLocation());
    }

    Vector getDirection(Block block) {
        if (!relative) {
            return new Vector(pitchOrX, yawOrY, lengthOrZ);
        }
        BlockFace face = getFacing(block);
        // Only up and down have a z mod, as Skript reckons.
        return getDirection(pitchOrX == IGNORE_PITCH ? 0 : face.getModZ() * Math.PI / 2,
                Math.atan2(face.getModZ(), face.getModX()));
    }

    private Vector getDirection(double p, double y) {
        if (pitchOrX == IGNORE_PITCH) {
            return new Vector(Math.cos(y + yawOrY) * lengthOrZ, 0, Math.sin(y + yawOrY) * lengthOrZ);
        }
        double lxz = Math.cos(p + pitchOrX) * lengthOrZ;
        return new Vector(Math.cos(y + yawOrY) * lxz, Math.sin(p + pitchOrX) * Math.cos(yawOrY) * lengthOrZ,
                Math.sin(y + yawOrY) * lxz);
    }

    boolean isRelative() {
        return relative;
    }

    @Override
    public int hashCode() {
        return (relative ? 1 : -1) * Arrays.hashCode(new double[] {pitchOrX, yawOrY, lengthOrZ});
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof Direction other && relative == other.relative && pitchOrX == other.pitchOrX
                && yawOrY == other.yawOrY && other.lengthOrZ == lengthOrZ;
    }

    /** Notch pitch to mathematical pitch, from the x/z plane to the y axis. */
    static double pitchToRadians(float pitch) {
        return -Math.toRadians(pitch);
    }

    /** Mathematical pitch to Notch pitch. */
    static float getPitch(double pitch) {
        return (float) Math.toDegrees(-pitch);
    }

    /** Notch yaw to mathematical yaw, from the x axis to the z axis. */
    static double yawToRadians(float yaw) {
        return Math.toRadians(yaw) + Math.PI / 2;
    }

    /** Mathematical yaw to Notch yaw. */
    static float getYaw(double yaw) {
        return (float) Math.toDegrees(yaw - Math.PI / 2);
    }

    /** The facing of the block, or {@link BlockFace#SELF} if it doesn't have one. */
    static BlockFace getFacing(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof org.bukkit.block.data.Directional directional)) {
            return BlockFace.SELF;
        }
        return directional.getFacing();
    }

    static BlockFace getFacing(double yaw, double pitch) {
        if (-Math.PI / 4 < pitch && pitch < Math.PI / 4) {
            if (yaw < Math.PI / 4 || yaw >= Math.PI * 7 / 4) {
                return BF_X;
            }
            if (yaw < Math.PI * 3 / 4) {
                return BF_Z;
            }
            if (yaw < Math.PI * 5 / 4) {
                return BF_X.getOppositeFace();
            }
            return BF_Z.getOppositeFace();
        }
        if (pitch > 0) {
            return BlockFace.UP;
        }
        return BlockFace.DOWN;
    }

    static BlockFace getFacing(Location location, boolean horizontal) {
        double yaw = (yawToRadians(location.getYaw()) + 2 * Math.PI) % (2 * Math.PI);
        double pitch = horizontal ? 0 : pitchToRadians(location.getPitch());
        return getFacing(yaw, pitch);
    }

    static BlockFace getFacing(Vector vector, boolean horizontal) {
        double pitch = horizontal ? 0
                : Math.atan2(vector.getY(), Math.sqrt(Math.pow(vector.getX(), 2) + Math.pow(vector.getZ(), 2)));
        double yaw = Math.atan2(vector.getZ(), vector.getX());
        return getFacing(yaw, pitch);
    }

    /** The block face most closely aligned to a normalized vector. */
    static BlockFace toNearestBlockFace(Vector vector) {
        double maxDot = -1;
        BlockFace nearest = BlockFace.NORTH;
        for (BlockFace face : BlockFace.values()) {
            double dot = face.getDirection().dot(vector);
            if (dot > maxDot) {
                maxDot = dot;
                nearest = face;
            }
        }
        return nearest;
    }

    /** The cartesian block face most closely aligned to a normalized vector. */
    static BlockFace toNearestCartesianBlockFace(Vector vector) {
        double maxDot = -1;
        BlockFace nearest = null;
        for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN}) {
            double dot = face.getDirection().dot(vector);
            if (dot > maxDot) {
                maxDot = dot;
                nearest = face;
            }
        }
        return nearest;
    }

    /**
     * Skript's {@code BlockUtils.getLocation}: the middle of a block, looking where the block faces if it
     * does.
     */
    static Location blockLocation(Block block) {
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        BlockFace face = getFacing(block);
        if (face != BlockFace.SELF) {
            location.setPitch(getPitch(Math.asin(face.getModY())));
            location.setYaw(getYaw(Math.atan2(face.getModZ(), face.getModX())));
        }
        return location;
    }

    /**
     * Skript's {@code Direction.combine} of {@code %directions% %locations%}: each location moved by each
     * direction in turn, each relative to where the location has got to. Values that aren't directions or
     * locations are skipped.
     */
    static List<Location> combine(Object directions, Object locations) {
        Object[] ds = Values.array(directions);
        List<Location> result = new ArrayList<>();
        for (Object place : Values.array(locations)) {
            if (place instanceof Location location) {
                Location moved = location;
                for (Object direction : ds) {
                    if (direction instanceof Direction d) {
                        moved = d.getRelative(moved);
                    }
                }
                // Skript hands back the locations themselves where there's no direction.
                result.add(moved == location ? location.clone() : moved);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return relative ? toString(pitchOrX == IGNORE_PITCH ? 0 : pitchOrX, yawOrY, lengthOrZ)
                : toString(new double[] {pitchOrX, yawOrY, lengthOrZ});
    }

    private static final String[] ABSOLUTE = {"east", "west", "above", "below", "south", "north"};
    private static final String[] RELATIVE = {"in front of", "behind", "to the left of", "to the right of", "above",
            "below"};

    static String toString(double pitch, double yaw, double length) {
        double front = Math.cos(pitch) * Math.cos(yaw) * length;
        double left = Math.cos(pitch) * Math.sin(yaw) * length;
        double above = Math.sin(pitch) * length;
        return toString(new double[] {front, left, above}, RELATIVE);
    }

    static String toString(double[] mod) {
        if (mod[0] == 0 && mod[1] == 0 && mod[2] == 0) {
            return "at";
        }
        return toString(mod, ABSOLUTE);
    }

    static String toString(Vector direction) {
        return toString(new double[] {direction.getX(), direction.getY(), direction.getZ()});
    }

    private static String toString(double[] mod, String[] names) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            double d = mod[i];
            if (d == 0) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(" and ");
            }
            if (d != 1 && d != -1) {
                double amount = Math.abs(d);
                text.append(Values.decimal(amount)).append(amount == 1 ? " meter" : " meters").append(' ');
            }
            text.append(d > 0 ? names[2 * i] : names[2 * i + 1]);
        }
        return text.toString();
    }

    /**
     * Skript's old text form ({@code relative:pitch,yaw,length} or {@code false:x,y,z}), from before
     * Yggdrasil, or {@code null} if it isn't one.
     */
    static Direction deserialize(String text) {
        String[] split = text.split(":");
        if (split.length != 2) {
            return null;
        }
        boolean relative = Boolean.parseBoolean(split[0]);
        String[] parts = split[1].split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            double a = Double.parseDouble(parts[0]);
            double b = Double.parseDouble(parts[1]);
            double c = Double.parseDouble(parts[2]);
            return relative ? new Direction(a, b, c) : new Direction(new double[] {a, b, c});
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * How directions are saved, as Skript's Yggdrasil serializer writes their fields: {@code pitchOrX},
     * {@code lengthOrZ}, {@code yawOrY} and {@code relative}, in its field map's order. Older saves with
     * {@code mod} or {@code pitch}/{@code yaw}/{@code length} fields aren't read.
     */
    static final VariableCodec<Direction> CODEC = new VariableCodec<>() {
        @Override
        public void encode(Direction direction, BinaryWriter out) {
            out.beginObject(4);
            out.doubleField("pitchOrX", direction.pitchOrX);
            out.doubleField("lengthOrZ", direction.lengthOrZ);
            out.doubleField("yawOrY", direction.yawOrY);
            out.booleanField("relative", direction.relative);
        }

        @Override
        public Direction decode(BinaryReader in) {
            in.beginObject();
            double pitchOrX = in.doubleField("pitchOrX");
            double lengthOrZ = in.doubleField("lengthOrZ");
            double yawOrY = in.doubleField("yawOrY");
            boolean relative = in.booleanField("relative");
            return new Direction(relative, pitchOrX, yawOrY, lengthOrZ, null, false);
        }
    };
}
