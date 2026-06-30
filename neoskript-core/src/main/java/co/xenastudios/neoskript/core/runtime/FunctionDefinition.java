package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A user-defined function: a name, ordered parameter names, and a body of statements. Phase 2
 * functions are dynamically typed; parameter type annotations in the source are accepted but not yet
 * enforced (Phase 2 polish / Phase 3).
 */
public final class FunctionDefinition {

    /** Maximum nested function-call depth before aborting (guards against infinite recursion). */
    public static final int MAX_DEPTH = 800;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

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
        int depth = DEPTH.get();
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException(
                    "Maximum function call depth (" + MAX_DEPTH + ") exceeded in '" + name
                            + "' — possible infinite recursion");
        }
        DEPTH.set(depth + 1);
        try {
            TriggerContext scope = new ChildContext(caller);
            for (int i = 0; i < parameters.size(); i++) {
                scope.setLocal(parameters.get(i), i < arguments.size() ? arguments.get(i) : null);
            }
            try {
                IfSection.runAll(body, scope);
            } catch (ReturnSignal signal) {
                return signal.value();
            } catch (StopSignal ignored) {
                // `stop` inside a function ends the function, not the caller's trigger.
            }
            return null;
        } finally {
            DEPTH.set(depth);
        }
    }
}
