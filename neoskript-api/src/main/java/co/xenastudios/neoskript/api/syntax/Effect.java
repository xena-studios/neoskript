package co.xenastudios.neoskript.api.syntax;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * An action that does something but yields no value — the script-level statement
 * (e.g. {@code send "hi" to player}).
 */
@FunctionalInterface
public interface Effect extends SyntaxElement {

    /**
     * Performs this effect.
     *
     * @param ctx the current execution context
     */
    void execute(TriggerContext ctx);
}
