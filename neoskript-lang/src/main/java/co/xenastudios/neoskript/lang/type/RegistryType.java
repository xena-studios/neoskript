package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link Type} backed by a Bukkit {@link Registry} (biome, enchantment, attribute, …). The registry
 * is resolved lazily — touching {@code Registry.X} requires a running server, so it must not happen at
 * type-registration time (which also runs in server-less unit tests). Parsing accepts a key name with
 * spaces or hyphens for underscores ({@code "deep dark"} → {@code deep_dark}); display renders the key
 * path.
 *
 * @param <T> the keyed registry value type
 */
public final class RegistryType<T extends Keyed> implements Type<T> {

    private final String codeName;
    private final Class<T> typeClass;
    private final Supplier<Registry<T>> registry;

    public RegistryType(String codeName, Class<T> typeClass, Supplier<Registry<T>> registry) {
        this.codeName = codeName;
        this.typeClass = typeClass;
        this.registry = registry;
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
        Registry<T> reg;
        try {
            reg = registry.get();
        } catch (Throwable noServer) {
            return Optional.empty();
        }
        if (reg == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(reg.get(key));
        } catch (Throwable invalid) {
            return Optional.empty();
        }
    }

    @Override
    public String toDisplayString(T value) {
        return value.getKey().getKey();
    }
}
