package co.xenastudios.neoskript.api.syntax;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * A boolean test used in {@code if} sections and {@code while} loops
 * (e.g. {@code player is op}).
 */
@FunctionalInterface
public interface Condition extends SyntaxElement {

    /**
     * Evaluates this condition.
     *
     * @param ctx the current execution context
     * @return {@code true} if the condition holds
     */
    boolean check(TriggerContext ctx);
}
