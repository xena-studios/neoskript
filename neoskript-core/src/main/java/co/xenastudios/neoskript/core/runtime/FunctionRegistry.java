package co.xenastudios.neoskript.core.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds callable functions by name — user-defined {@link FunctionDefinition}s and native built-ins
 * ({@link ScriptFunction}s). Populated as scripts are parsed and at startup, and queried at runtime
 * by function-call expressions, so calls may appear before the definition in source. Backed by a
 * concurrent map so scripts can be parsed in parallel.
 */
public final class FunctionRegistry {

    private final Map<String, ScriptFunction> functions = new ConcurrentHashMap<>();

    /** The most recently constructed registry, so syntax like {@code run %executable%} can resolve it. */
    private static volatile FunctionRegistry active;

    public FunctionRegistry() {
        active = this;
    }

    /** @return the active function registry (the plugin's), or {@code null} before one is created */
    public static FunctionRegistry active() {
        return active;
    }

    /**
     * Registers (or replaces) a user-defined function.
     *
     * @param function the function definition
     */
    public void register(FunctionDefinition function) {
        functions.put(function.name().toLowerCase(Locale.ROOT), function);
    }

    /**
     * Registers (or replaces) a function by name — used for native built-ins.
     *
     * @param name     the function name (case-insensitive)
     * @param function the implementation
     */
    public void register(String name, ScriptFunction function) {
        functions.put(name.toLowerCase(Locale.ROOT), function);
    }

    /**
     * Looks up a function by name.
     *
     * @param name the function name (case-insensitive)
     * @return the function, or {@code null} if none is registered
     */
    public ScriptFunction get(String name) {
        return functions.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return the number of registered functions */
    public int size() {
        return functions.size();
    }

    /**
     * Removes user-defined functions while keeping native built-ins (used when reloading scripts, so
     * built-ins registered once at startup survive).
     */
    public void clear() {
        functions.values().removeIf(FunctionDefinition.class::isInstance);
    }
}
