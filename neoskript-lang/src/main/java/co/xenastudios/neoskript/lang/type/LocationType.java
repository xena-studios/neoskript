package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Location;

import java.util.Optional;

/**
 * The built-in {@code location} type. Rendered as {@code x, y, z (world)}.
 */
public final class LocationType implements Type<Location> {

    @Override
    public Class<Location> typeClass() {
        return Location.class;
    }

    @Override
    public String codeName() {
        return "location";
    }

    @Override
    public Optional<Location> parse(String input) {
        return Optional.empty();
    }

    @Override
    public String toDisplayString(Location value) {
        String world = value.getWorld() == null ? "" : " (" + value.getWorld().getName() + ")";
        return String.format("%.1f, %.1f, %.1f%s", value.getX(), value.getY(), value.getZ(), world);
    }
}
