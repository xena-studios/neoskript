package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Locale;
import java.util.Optional;

/**
 * A generic {@link Type} backed by a Java enum, resolved reflectively so it compiles and runs
 * regardless of whether the backing class is statically declared {@code enum} on the compile
 * classpath (Bukkit migrates several former enums to registry interfaces between versions).
 *
 * <p>Parsing accepts the constant name case-insensitively with spaces or hyphens standing in for
 * underscores ({@code "instant health"} → {@code INSTANT_HEALTH}); display renders the constant name
 * lowercased with underscores as spaces. If the backing class is not an enum at runtime, parsing
 * yields {@link Optional#empty()} rather than failing.
 *
 * @param <T> the enum type represented
 */
public final class EnumType<T> implements Type<T> {

    private final String codeName;
    private final Class<T> typeClass;
    private final T[] constants;

    public EnumType(String codeName, Class<T> typeClass) {
        this.codeName = codeName;
        this.typeClass = typeClass;
        this.constants = typeClass.getEnumConstants();
    }

    @Override
    public Class<T> typeClass() {
        return typeClass;
    }

    @Override
    public String codeName() {
        return codeName;
    }

    @Override
    public Optional<T> parse(String input) {
        if (constants == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (T constant : constants) {
            if (((Enum<?>) constant).name().equals(normalized)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toDisplayString(T value) {
        return ((Enum<?>) value).name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
