package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.type.Type;
import co.xenastudios.neoskript.core.type.TypeRegistry;

import java.util.List;

/**
 * A user-defined function: a name, ordered parameter names, and a body of statements. Functions are
 * dynamically typed; declared parameter and return types (e.g. {@code function f(n: number) :: text})
 * are best-effort coerced — a string argument to a typed parameter is parsed into that type, and the
 * returned value is coerced to the declared return type.
 */
public final class FunctionDefinition implements ScriptFunction {

    /** Maximum nested function-call depth before aborting (guards against infinite recursion). */
    public static final int MAX_DEPTH = 800;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private final String name;
    private final List<String> parameters;
    private final List<Expression<?>> defaults;
    private final List<String> parameterTypes;
    private final String returnType;
    private final List<Statement> body;

    public FunctionDefinition(String name, List<String> parameters, List<Statement> body) {
        this(name, parameters, java.util.Collections.nCopies(parameters.size(), null), body);
    }

    public FunctionDefinition(String name, List<String> parameters, List<Expression<?>> defaults,
                              List<Statement> body) {
        this(name, parameters, defaults, java.util.Collections.nCopies(parameters.size(), null), null, body);
    }

    public FunctionDefinition(String name, List<String> parameters, List<Expression<?>> defaults,
                              List<String> parameterTypes, String returnType, List<Statement> body) {
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.defaults = new java.util.ArrayList<>(defaults);
        this.parameterTypes = new java.util.ArrayList<>(parameterTypes);
        this.returnType = returnType;
        this.body = List.copyOf(body);
    }

    /**
     * Coerces a value toward a declared type's code name: values already of the type pass through;
     * strings are parsed via the type. Returns the original value when no coercion applies.
     */
    private static Object coerce(Object value, String typeName) {
        if (typeName == null || value == null) {
            return value;
        }
        TypeRegistry registry = Renderer.typeRegistry();
        if (registry == null) {
            return value;
        }
        Type<?> type = registry.byCodeName(typeName);
        if (type == null || type.typeClass().isInstance(value)) {
            return value;
        }
        if (value instanceof String s) {
            Object parsed = type.parse(s).orElse(null);
            return parsed != null ? parsed : value;
        }
        return value;
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
    @Override
    public Object call(List<Object> arguments, TriggerContext caller) {
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
                Object value = i < arguments.size() ? arguments.get(i) : null;
                if (value == null && defaults.get(i) != null) {
                    value = defaults.get(i).getSingle(scope); // default may reference earlier params
                }
                scope.setLocal(parameters.get(i), coerce(value, parameterTypes.get(i)));
            }
            try {
                IfSection.runAll(body, scope);
            } catch (ReturnSignal signal) {
                return coerce(signal.value(), returnType);
            } catch (StopSignal ignored) {
                // `stop` inside a function ends the function, not the caller's trigger.
            }
            return null;
        } finally {
            DEPTH.set(depth);
        }
    }
}
