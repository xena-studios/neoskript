package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * Strategy for running a {@link Trigger}. This is the seam for the planned JIT: the default
 * {@link InterpretedEngine} walks the statement tree, while a future ASM-backed engine can compile
 * hot triggers to bytecode and fall back to the interpreter for anything unsupported. Hotness is
 * tracked by {@link HotPathTracker}.
 */
public interface ExecutionEngine {

    /**
     * Runs a trigger against a context.
     *
     * @param trigger the trigger to run
     * @param ctx     the execution context
     */
    void run(Trigger trigger, TriggerContext ctx);
}
