package co.xenastudios.neoskript.lang.type;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * A relative direction, mirroring Skript's {@code direction} type. An <em>absolute</em> direction is
 * a fixed offset (north/up/…); a <em>relative</em> direction (in front of/behind/left/right) is
 * resolved against a base location's facing. {@link #relativeTo} applies the direction to a base.
 */
public final class Direction {

    /** forward/up/left components (blocks); {@code relative} toggles facing-relative resolution. */
    private final double forward;
    private final double up;
    private final double left;
    private final boolean relative;

    public Direction(double forward, double up, double left, boolean relative) {
        this.forward = forward;
        this.up = up;
        this.left = left;
        this.relative = relative;
    }

    /** @return this direction scaled by {@code length} blocks */
    public Direction scaled(double length) {
        return new Direction(forward * length, up * length, left * length, relative);
    }

    /** Applies this direction to a base object (location/entity/block), returning the new location. */
    public Location relativeTo(Object base) {
        Location loc;
        if (base instanceof Location l) {
            loc = l.clone();
        } else if (base instanceof Entity e) {
            loc = e.getLocation();
        } else if (base instanceof org.bukkit.block.Block b) {
            loc = b.getLocation();
        } else {
            return null;
        }
        if (!relative) {
            return loc.add(forward, up, left);
        }
        Vector facing = loc.getDirection().setY(0);
        if (facing.lengthSquared() == 0) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Vector leftVec = new Vector(-facing.getZ(), 0, facing.getX());
        Vector offset = facing.multiply(forward).add(leftVec.multiply(left));
        offset.setY(up);
        return loc.add(offset);
    }

    @Override
    public String toString() {
        return "direction(" + forward + ", " + up + ", " + left + (relative ? ", relative)" : ")");
    }
}
