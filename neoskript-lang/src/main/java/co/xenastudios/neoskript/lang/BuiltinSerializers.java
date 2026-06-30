package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.core.variable.ValueSerializer;
import co.xenastudios.neoskript.core.variable.ValueSerializers;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Registers {@link ValueSerializer}s for the built-in rich types so they persist between restarts.
 * Values are stored as simple pipe/colon-delimited strings.
 */
public final class BuiltinSerializers {

    private BuiltinSerializers() {
    }

    /** Registers all built-in value serializers. */
    public static void registerAll() {
        ValueSerializers.register(serializer("location", Location.class,
                loc -> (loc.getWorld() == null ? "" : loc.getWorld().getName()) + "|"
                        + loc.getX() + "|" + loc.getY() + "|" + loc.getZ() + "|"
                        + loc.getYaw() + "|" + loc.getPitch(),
                BuiltinSerializers::parseLocation));

        ValueSerializers.register(serializer("vector", Vector.class,
                vec -> vec.getX() + "|" + vec.getY() + "|" + vec.getZ(),
                data -> {
                    String[] p = data.split("\\|");
                    return p.length < 3 ? null
                            : new Vector(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                }));

        ValueSerializers.register(serializer("item", ItemStack.class,
                item -> item.getType().name() + ":" + item.getAmount(),
                data -> {
                    String[] p = data.split(":");
                    Material material = Material.matchMaterial(p[0]);
                    return material == null ? null : new ItemStack(material, p.length > 1 ? Integer.parseInt(p[1]) : 1);
                }));

        ValueSerializers.register(serializer("gamemode", GameMode.class,
                mode -> mode.name(),
                data -> {
                    try {
                        return GameMode.valueOf(data.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }));

        ValueSerializers.register(serializer("world", World.class, World::getName, Bukkit::getWorld));
    }

    private static Location parseLocation(String data) {
        String[] p = data.split("\\|");
        if (p.length < 4) {
            return null;
        }
        World world = p[0].isEmpty() ? null : Bukkit.getWorld(p[0]);
        Location location = new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]));
        if (p.length >= 6) {
            location.setYaw(Float.parseFloat(p[4]));
            location.setPitch(Float.parseFloat(p[5]));
        }
        return location;
    }

    private static <T> ValueSerializer serializer(String id, Class<T> type,
                                                  java.util.function.Function<T, String> serialize,
                                                  java.util.function.Function<String, Object> deserialize) {
        return new ValueSerializer() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean handles(Object value) {
                return type.isInstance(value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public String serialize(Object value) {
                return serialize.apply((T) value);
            }

            @Override
            public Object deserialize(String data) {
                return deserialize.apply(data);
            }
        };
    }
}
