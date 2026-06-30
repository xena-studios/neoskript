package co.xenastudios.neoskript.api.registry;

import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Effect;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.function.Supplier;

/**
 * The entry point through which built-in syntaxes and addons contribute parseable elements.
 *
 * <p>Each registration associates a Skript-style pattern (e.g. {@code "broadcast %text%"}) with a
 * factory that produces a fresh syntax-element instance per occurrence in a script. Patterns are
 * compiled and indexed by the parse engine so that, at parse time, a line is only matched against
 * candidate syntaxes that can possibly apply.
 */
public interface SyntaxRegistry {

    /**
     * Registers an effect.
     *
     * @param pattern the syntax pattern
     * @param factory produces a new {@link Effect} instance for each occurrence
     */
    void registerEffect(String pattern, Supplier<? extends Effect> factory);

    /**
     * Registers a condition.
     *
     * @param pattern the syntax pattern
     * @param factory produces a new {@link Condition} instance for each occurrence
     */
    void registerCondition(String pattern, Supplier<? extends Condition> factory);

    /**
     * Registers an expression.
     *
     * @param pattern    the syntax pattern
     * @param returnType the element type the expression yields
     * @param factory    produces a new {@link Expression} instance for each occurrence
     * @param <T>        the element type
     */
    <T> void registerExpression(String pattern, Class<T> returnType, Supplier<? extends Expression<T>> factory);

    /**
     * @return the total number of registered syntax elements
     */
    int size();
}
