package co.xenastudios.neoskript.core.runtime;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holds user-defined functions by name. Populated as scripts are parsed and queried at runtime by
 * function-call expressions, so calls may appear before the definition in source.
 */
public final class FunctionRegistry {

    private final Map<String, FunctionDefinition> functions = new HashMap<>();

    /**
     * Registers (or replaces) a function.
     *
     * @param function the function definition
     */
    public void register(FunctionDefinition function) {
        functions.put(function.name().toLowerCase(Locale.ROOT), function);
    }

    /**
     * Looks up a function by name.
     *
     * @param name the function name (case-insensitive)
     * @return the definition, or {@code null} if none is registered
     */
    public FunctionDefinition get(String name) {
        return functions.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return the number of registered functions */
    public int size() {
        return functions.size();
    }
}
