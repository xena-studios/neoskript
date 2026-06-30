package co.xenastudios.neoskript.core.variable;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Process-wide registry of {@link ValueSerializer}s, populated at startup by the language/platform
 * layer and consulted by the persistence stores. Primitives (number/boolean/string) are handled
 * directly by the stores and do not need a serializer here.
 */
public final class ValueSerializers {

    private static final List<ValueSerializer> SERIALIZERS = new CopyOnWriteArrayList<>();

    private ValueSerializers() {
    }

    /** Registers a serializer. */
    public static void register(ValueSerializer serializer) {
        SERIALIZERS.add(serializer);
    }

    /** @return the first serializer that handles {@code value}, or {@code null} */
    public static ValueSerializer forValue(Object value) {
        for (ValueSerializer serializer : SERIALIZERS) {
            if (serializer.handles(value)) {
                return serializer;
            }
        }
        return null;
    }

    /** @return the serializer with the given id, or {@code null} */
    public static ValueSerializer byId(String id) {
        for (ValueSerializer serializer : SERIALIZERS) {
            if (serializer.id().equals(id)) {
                return serializer;
            }
        }
        return null;
    }
}
