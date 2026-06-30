package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A user-defined function: a name, ordered parameter names, and a body of statements. Phase 2
 * functions are dynamically typed; parameter type annotations in the source are accepted but not yet
 * enforced (Phase 2 polish / Phase 3).
 */
public final class FunctionDefinition {

    private final String name;
    private final List<String> parameters;
    private final List<Statement> body;

    public FunctionDefinition(String name, List<String> parameters, List<Statement> body) {
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.body = List.copyOf(body);
    }

    /** @return the function name */
    public String name() {
        return name;
    }

    /** @return the ordered parameter names */
    public List<String> parameters() {
        return parameters;
    }

    /**
     * Invokes the function with the given argument values, in a fresh local scope sharing the
     * caller's global variables.
     *
     * @param arguments the evaluated argument values, positionally matched to parameters
     * @param caller    the calling context, whose globals the function shares
     * @return the value passed to {@code return}, or {@code null} if the function returned no value
     */
    public Object invoke(List<Object> arguments, TriggerContext caller) {
        TriggerContext scope = new ChildContext(caller);
        for (int i = 0; i < parameters.size(); i++) {
            scope.setLocal(parameters.get(i), i < arguments.size() ? arguments.get(i) : null);
        }
        try {
            IfSection.runAll(body, scope);
        } catch (ReturnSignal signal) {
            return signal.value();
        }
        return null;
    }
}
