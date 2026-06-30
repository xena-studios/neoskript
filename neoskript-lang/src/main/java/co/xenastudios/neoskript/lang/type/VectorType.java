package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * The built-in {@code vector} type. Rendered as {@code x, y, z}.
 */
public final class VectorType implements Type<Vector> {

    @Override
    public Class<Vector> typeClass() {
        return Vector.class;
    }

    @Override
    public String codeName() {
        return "vector";
    }

    @Override
    public Optional<Vector> parse(String input) {
        return Optional.empty();
    }

    @Override
    public String toDisplayString(Vector value) {
        return String.format("%.2f, %.2f, %.2f", value.getX(), value.getY(), value.getZ());
    }
}
