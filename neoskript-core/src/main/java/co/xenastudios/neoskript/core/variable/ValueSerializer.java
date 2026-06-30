package co.xenastudios.neoskript.core.variable;

/**
 * Serializes a non-primitive variable value to/from a string so it can be persisted. Registered into
 * {@link ValueSerializers}; the persistence layer uses {@link #id()} as the stored type tag.
 */
public interface ValueSerializer {

    /** @return a stable type tag stored alongside the value (e.g. {@code "location"}) */
    String id();

    /** @return {@code true} if this serializer handles the given value */
    boolean handles(Object value);

    /** Serializes a value this serializer {@linkplain #handles handles} to a string. */
    String serialize(Object value);

    /** Reconstructs a value from a previously {@linkplain #serialize serialized} string. */
    Object deserialize(String data);
}
