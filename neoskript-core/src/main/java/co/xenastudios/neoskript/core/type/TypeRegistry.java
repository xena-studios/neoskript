package co.xenastudios.neoskript.core.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of value {@link Type}s, looked up by code name (for patterns) or by runtime value (for
 * display). Built-in types are registered at load and feed {@link co.xenastudios.neoskript.core.runtime.Renderer}.
 *
 * <p>This is the foundation of the type system; the converter/comparator graph described in the plan
 * builds on top of it.
 */
public final class TypeRegistry {

    private final List<Type<?>> types = new ArrayList<>();
    private final Map<String, Type<?>> byCodeName = new HashMap<>();

    /**
     * Registers a type.
     *
     * @param type the type to register
     */
    public void register(Type<?> type) {
        types.add(type);
        byCodeName.put(type.codeName().toLowerCase(Locale.ROOT), type);
    }

    /**
     * @param codeName the type's code name (e.g. {@code "number"})
     * @return the type, or {@code null} if unknown
     */
    public Type<?> byCodeName(String codeName) {
        return byCodeName.get(codeName.toLowerCase(Locale.ROOT));
    }

    /**
     * Finds the most appropriate registered type for a runtime value (the first whose class the value
     * is an instance of, in registration order).
     *
     * @param value the value
     * @return the matching type, or {@code null} if none is registered for it
     */
    public Type<?> forValue(Object value) {
        if (value == null) {
            return null;
        }
        for (Type<?> type : types) {
            if (type.typeClass().isInstance(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Renders a value via its registered type's display form.
     *
     * @param value the value
     * @return the display string, or {@code null} if no type matches
     */
    @SuppressWarnings("unchecked")
    public String display(Object value) {
        Type<?> type = forValue(value);
        return type == null ? null : ((Type<Object>) type).toDisplayString(value);
    }

    /** @return the number of registered types */
    public int size() {
        return types.size();
    }
}
