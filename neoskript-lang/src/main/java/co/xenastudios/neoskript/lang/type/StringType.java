package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Optional;

/**
 * The built-in {@code text} type, backed by {@link String}.
 */
public final class StringType implements Type<String> {

    @Override
    public Class<String> typeClass() {
        return String.class;
    }

    @Override
    public String codeName() {
        return "text";
    }

    @Override
    public Optional<String> parse(String input) {
        return Optional.of(input);
    }

    @Override
    public String toDisplayString(String value) {
        return value;
    }
}
