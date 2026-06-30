package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * The default {@link ExecutionEngine}: a direct tree-walking interpreter. Always correct and the
 * fallback for the future bytecode backend.
 */
public final class InterpretedEngine implements ExecutionEngine {

    @Override
    public void run(Trigger trigger, TriggerContext ctx) {
        trigger.execute(ctx);
    }
}
