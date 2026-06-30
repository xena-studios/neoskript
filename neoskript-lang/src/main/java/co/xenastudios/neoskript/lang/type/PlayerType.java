package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The built-in {@code player} type. Renders as the player's name and parses an exact online name.
 */
public final class PlayerType implements Type<Player> {

    @Override
    public Class<Player> typeClass() {
        return Player.class;
    }

    @Override
    public String codeName() {
        return "player";
    }

    @Override
    public Optional<Player> parse(String input) {
        return Optional.ofNullable(Bukkit.getPlayerExact(input.trim()));
    }

    @Override
    public String toDisplayString(Player value) {
        return value.getName();
    }
}
