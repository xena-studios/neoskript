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
public final class EnumType<T> implements Type<T>, ValueSet {

    private final String codeName;
    private final Class<T> typeClass;
    private T[] constants;
    private boolean resolved;

    public EnumType(String codeName, Class<T> typeClass) {
        this.codeName = codeName;
        this.typeClass = typeClass;
    }

    /**
     * Resolves the enum constants on first use. Deferred so that constructing the type does not load
     * a registry-backed enum's static initializer (which needs a running server).
     */
    private T[] constants() {
        if (!resolved) {
            try {
                constants = typeClass.getEnumConstants();
            } catch (Throwable noServer) {
                constants = null;
            }
            resolved = true;
        }
        return constants;
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
        T[] values = constants();
        if (values == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (T constant : values) {
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

    @Override
    public java.util.List<?> allValues() {
        T[] values = constants();
        return values == null ? java.util.List.of() : java.util.List.of(values);
    }
}
