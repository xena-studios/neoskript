package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Locale;
import java.util.Optional;

/**
 * The built-in {@code boolean} type.
 */
public final class BooleanType implements Type<Boolean> {

    @Override
    public Class<Boolean> typeClass() {
        return Boolean.class;
    }

    @Override
    public String codeName() {
        return "boolean";
    }

    @Override
    public Optional<Boolean> parse(String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> Optional.of(Boolean.TRUE);
            case "false", "no", "off" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    @Override
    public String toDisplayString(Boolean value) {
        return value ? "true" : "false";
    }
}
