package co.xenastudios.neoskript.api.syntax;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * A value, or list of values, that can be evaluated at runtime (e.g. {@code player's health},
 * {@code all players}).
 *
 * @param <T> the element type produced by this expression
 */
public interface Expression<T> extends SyntaxElement {

    /**
     * Evaluates the expression to all of its values.
     *
     * @param ctx the current execution context
     * @return the values, never {@code null} (use an empty array for "no value")
     */
    T[] getAll(TriggerContext ctx);

    /**
     * Evaluates the expression to a single value. For multi-valued expressions the choice of which
     * value to return is expression-defined (typically the first, or a random element).
     *
     * @param ctx the current execution context
     * @return the single value, or {@code null} if there is none
     */
    T getSingle(TriggerContext ctx);

    /**
     * @return the element type this expression produces
     */
    Class<T> returnType();

    /**
     * @return {@code true} if this expression always yields at most one value
     */
    boolean isSingle();
}
