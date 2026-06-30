package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Condition;

import java.util.List;

/**
 * A {@code while <condition>:} loop. Guarded by an iteration cap so a runaway condition cannot hang
 * the server thread; on hitting the cap the loop stops.
 */
public final class WhileSection implements Statement {

    /** Safety cap on iterations for a single {@code while} run. */
    public static final long MAX_ITERATIONS = 10_000_000L;

    private final Condition condition;
    private final List<Statement> body;

    public WhileSection(Condition condition, List<Statement> body) {
        this.condition = condition;
        this.body = List.copyOf(body);
    }

    @Override
    public void run(TriggerContext ctx) {
        long iterations = 0;
        while (condition.check(ctx)) {
            IfSection.runAll(body, ctx);
            if (++iterations >= MAX_ITERATIONS) {
                break;
            }
        }
    }
}
