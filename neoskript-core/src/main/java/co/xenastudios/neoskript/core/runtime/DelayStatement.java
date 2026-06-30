package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

/**
 * A {@code wait <timespan>} in a trigger. Honoured only on the scheduler-driven execution path
 * ({@link Trigger#execute(TriggerContext, DelayScheduler)}); on the plain synchronous path it is a
 * no-op. The parser only allows it at the top level of a trigger (not inside sections/functions),
 * so it never appears where it couldn't be honoured.
 */
public final class DelayStatement implements Statement {

    private final long ticks;

    public DelayStatement(long ticks) {
        this.ticks = ticks;
    }

    /** @return the delay in ticks */
    public long ticks() {
        return ticks;
    }

    @Override
    public void run(TriggerContext ctx) {
        // No-op without a scheduler; the scheduler path handles the actual delay.
    }
}
