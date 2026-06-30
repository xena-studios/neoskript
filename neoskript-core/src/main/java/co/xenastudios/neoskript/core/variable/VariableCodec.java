package co.xenastudios.neoskript.core.variable;

/**
 * Encodes/decodes a variable value to a (type-tag, string) pair, shared by all persistence backends.
 * Primitives are handled directly; other values go through the {@link ValueSerializers} registry.
 */
public final class VariableCodec {

    /** A persistable representation: a type tag and its serialized value. */
    public record Encoded(String type, String value) {
    }

    private VariableCodec() {
    }

    /**
     * Encodes a value, or returns {@code null} if it cannot be persisted.
     */
    public static Encoded encode(Object value) {
        if (value instanceof Number number) {
            return new Encoded("number", Double.toString(number.doubleValue()));
        }
        if (value instanceof Boolean bool) {
            return new Encoded("boolean", bool.toString());
        }
        if (value instanceof String text) {
            return new Encoded("string", text);
        }
        ValueSerializer serializer = ValueSerializers.forValue(value);
        return serializer == null ? null : new Encoded(serializer.id(), serializer.serialize(value));
    }

    /**
     * Decodes a (type, value) pair back to a value, or {@code null} if the type is unknown.
     */
    public static Object decode(String type, String value) {
        return switch (type) {
            case "number" -> parseNumber(value);
            case "boolean" -> Boolean.valueOf(value);
            case "string" -> value;
            default -> {
                ValueSerializer serializer = ValueSerializers.byId(type);
                yield serializer == null ? null : serializer.deserialize(value);
            }
        };
    }

    private static Double parseNumber(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
