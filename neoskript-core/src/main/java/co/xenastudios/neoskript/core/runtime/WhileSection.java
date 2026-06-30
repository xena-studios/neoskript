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

    /** Wall-clock safety budget for a single {@code while} run (nanoseconds). */
    public static final long MAX_NANOS = 10_000_000_000L; // 10 seconds

    private final Condition condition;
    private final List<Statement> body;

    public WhileSection(Condition condition, List<Statement> body) {
        this.condition = condition;
        this.body = List.copyOf(body);
    }

    /** @return the loop condition */
    public Condition condition() {
        return condition;
    }

    /** @return the loop body */
    public List<Statement> body() {
        return body;
    }

    @Override
    public void run(TriggerContext ctx) {
        long iterations = 0;
        long deadline = System.nanoTime() + MAX_NANOS;
        while (condition.check(ctx)) {
            try {
                IfSection.runAll(body, ctx);
            } catch (ContinueSignal ignored) {
                // proceed to the next iteration
            } catch (BreakSignal ignored) {
                break;
            }
            // Stop on either bound so a runaway loop can't hang the server thread indefinitely.
            if (++iterations >= MAX_ITERATIONS || System.nanoTime() >= deadline) {
                break;
            }
        }
    }
}
