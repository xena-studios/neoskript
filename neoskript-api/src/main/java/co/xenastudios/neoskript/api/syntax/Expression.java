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

    /**
     * Declares whether this expression can be changed in the given mode, and with what value type(s).
     *
     * <p>Returning {@code null} (the default) means the expression is read-only for that mode. A
     * non-null array lists the accepted delta element types ({@code Object.class} for "anything");
     * an empty array means the mode is accepted but takes no delta (e.g. {@link ChangeMode#DELETE}).
     *
     * @param mode the change mode
     * @return the accepted delta types, or {@code null} if this mode is not supported
     */
    default Class<?>[] acceptChange(ChangeMode mode) {
        return null;
    }

    /**
     * Applies a change to this expression. Only called for modes that {@link #acceptChange(ChangeMode)}
     * accepts.
     *
     * @param ctx   the current execution context
     * @param delta the new/added/removed values, or {@code null} for {@link ChangeMode#DELETE}/
     *              {@link ChangeMode#RESET}
     * @param mode  the change mode
     * @throws UnsupportedOperationException if this expression does not support being changed
     */
    default void change(TriggerContext ctx, Object[] delta, ChangeMode mode) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " cannot be changed (" + mode + ")");
    }
}
