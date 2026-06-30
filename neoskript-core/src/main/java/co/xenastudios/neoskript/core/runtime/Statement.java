package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * One executable item in a trigger body: an effect or a section ({@code if}, {@code while},
 * {@code loop}). The linker produces a tree of statements; the interpreter walks it.
 */
@FunctionalInterface
public interface Statement {

    /**
     * Runs this statement.
     *
     * @param ctx the execution context
     */
    void run(TriggerContext ctx);
}
