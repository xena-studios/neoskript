package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.GameMode;

import java.util.Locale;
import java.util.Optional;

/**
 * The built-in {@code gamemode} type.
 */
public final class GameModeType implements Type<GameMode> {

    @Override
    public Class<GameMode> typeClass() {
        return GameMode.class;
    }

    @Override
    public String codeName() {
        return "gamemode";
    }

    @Override
    public Optional<GameMode> parse(String input) {
        try {
            return Optional.of(GameMode.valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toDisplayString(GameMode value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
