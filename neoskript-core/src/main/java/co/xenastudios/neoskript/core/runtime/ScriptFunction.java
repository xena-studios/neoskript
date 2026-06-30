package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A callable function — either a user-defined {@link FunctionDefinition} or a native built-in
 * (e.g. {@code abs}, {@code sqrt}). Resolved by name through the {@link FunctionRegistry}.
 */
@FunctionalInterface
public interface ScriptFunction {

    /**
     * Calls the function.
     *
     * @param arguments the evaluated argument values
     * @param caller    the calling context
     * @return the return value, or {@code null}
     */
    Object call(List<Object> arguments, TriggerContext caller);
}
