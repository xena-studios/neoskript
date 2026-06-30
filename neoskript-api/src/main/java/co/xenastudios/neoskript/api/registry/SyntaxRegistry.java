package co.xenastudios.neoskript.api.registry;

import co.xenastudios.neoskript.api.syntax.ConditionFactory;
import co.xenastudios.neoskript.api.syntax.EffectFactory;
import co.xenastudios.neoskript.api.syntax.ExpressionFactory;

/**
 * The entry point through which built-in syntaxes and addons contribute parseable elements.
 *
 * <p>Each registration associates a Skript-style pattern (e.g. {@code "broadcast %string%"}) with a
 * factory that builds a fresh syntax-element instance per occurrence in a script, bound to the
 * arguments captured from the pattern. Patterns are compiled and indexed by the parse engine so
 * that, at parse time, a line is only matched against candidate syntaxes that can possibly apply.
 */
public interface SyntaxRegistry {

    /**
     * Registers an effect.
     *
     * @param pattern the syntax pattern
     * @param factory builds a new effect from the matched arguments
     */
    void registerEffect(String pattern, EffectFactory factory);

    /**
     * Registers a condition.
     *
     * @param pattern the syntax pattern
     * @param factory builds a new condition from the matched arguments
     */
    void registerCondition(String pattern, ConditionFactory factory);

    /**
     * Registers an expression.
     *
     * @param pattern    the syntax pattern
     * @param returnType the element type the expression yields
     * @param factory    builds a new expression from the matched arguments
     * @param <T>        the element type
     */
    <T> void registerExpression(String pattern, Class<T> returnType, ExpressionFactory<T> factory);

    /**
     * @return the total number of registered syntax elements
     */
    int size();
}
