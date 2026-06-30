package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Optional;

/**
 * The built-in {@code world} type. Renders as the world name and parses a loaded world by name.
 */
public final class WorldType implements Type<World> {

    @Override
    public Class<World> typeClass() {
        return World.class;
    }

    @Override
    public String codeName() {
        return "world";
    }

    @Override
    public Optional<World> parse(String input) {
        return Optional.ofNullable(Bukkit.getWorld(input.trim()));
    }

    @Override
    public String toDisplayString(World value) {
        return value.getName();
    }
}
