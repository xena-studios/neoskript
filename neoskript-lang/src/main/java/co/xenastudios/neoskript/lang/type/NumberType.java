package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Optional;

/**
 * The built-in {@code number} type, backed by {@link Double}.
 */
public final class NumberType implements Type<Double> {

    @Override
    public Class<Double> typeClass() {
        return Double.class;
    }

    @Override
    public String codeName() {
        return "number";
    }

    @Override
    public Optional<Double> parse(String input) {
        try {
            return Optional.of(Double.parseDouble(input.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toDisplayString(Double value) {
        // Render whole numbers without a trailing ".0", matching Skript's display behaviour.
        if (value == Math.rint(value) && !value.isInfinite()) {
            return Long.toString(value.longValue());
        }
        return Double.toString(value);
    }
}
