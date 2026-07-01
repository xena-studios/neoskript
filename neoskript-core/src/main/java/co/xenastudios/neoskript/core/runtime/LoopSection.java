package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.List;

/**
 * A {@code loop} section in one of two forms:
 * <ul>
 *   <li>{@code loop %number% times:} — runs the body N times.</li>
 *   <li>{@code loop %objects%:} — runs the body once per value of the expression.</li>
 * </ul>
 *
 * <p>During each iteration the current element is available as {@code loop-value} and the 1-based
 * iteration counter as {@code loop-number}/{@code loop-index}, stored as locals and restored
 * afterwards so nested loops behave correctly.
 */
public final class LoopSection implements Statement {

    private final Expression<?> source;
    private final boolean times;
    private final List<Statement> body;
    private final java.util.function.BiConsumer<TriggerContext, Object> binder;

    private LoopSection(Expression<?> source, boolean times, List<Statement> body,
                        java.util.function.BiConsumer<TriggerContext, Object> binder) {
        this.source = source;
        this.times = times;
        this.body = List.copyOf(body);
        this.binder = binder;
    }

    /** Creates a {@code loop N times} section. */
    public static LoopSection times(Expression<?> count, List<Statement> body) {
        return new LoopSection(count, true, body, null);
    }

    /** Creates a {@code loop <values>} section. */
    public static LoopSection over(Expression<?> values, List<Statement> body) {
        return new LoopSection(values, false, body, null);
    }

    /**
     * Creates a {@code for each %var% in <values>} section: like {@link #over}, but each iteration
     * also binds the current element via {@code binder} (in addition to {@code loop-value}).
     */
    public static LoopSection forEach(Expression<?> values, List<Statement> body,
                                      java.util.function.BiConsumer<TriggerContext, Object> binder) {
        return new LoopSection(values, false, body, binder);
    }

    /** @return the per-iteration binder for a for-each loop, or {@code null} for a plain loop. */
    public java.util.function.BiConsumer<TriggerContext, Object> binder() {
        return binder;
    }

    /** Wall-clock safety budget for a single loop run (nanoseconds). */
    public static final long MAX_NANOS = 10_000_000_000L; // 10 seconds

    /** @return {@code true} for {@code loop N times}, {@code false} for {@code loop <values>} */
    public boolean isTimes() {
        return times;
    }

    /** @return the count expression (times mode) or values expression (over mode) */
    public Expression<?> source() {
        return source;
    }

    /** @return the loop body */
    public List<Statement> body() {
        return body;
    }

    @Override
    public void run(TriggerContext ctx) {
        Object previousValue = ctx.getLocal("loop-value");
        Object previousIndex = ctx.getLocal("loop-index");
        long deadline = System.nanoTime() + MAX_NANOS;
        try {
            if (times) {
                long count = toLong(source.getSingle(ctx));
                for (long i = 1; i <= count; i++) {
                    ctx.setLocal("loop-value", (double) i);
                    ctx.setLocal("loop-index", (double) i);
                    if (runIteration(ctx) || System.nanoTime() >= deadline) {
                        break;
                    }
                }
            } else {
                Object[] values = source.getAll(ctx);
                for (int i = 0; i < values.length; i++) {
                    ctx.setLocal("loop-value", values[i]);
                    ctx.setLocal("loop-index", (double) (i + 1));
                    if (binder != null) {
                        binder.accept(ctx, values[i]);
                    }
                    if (runIteration(ctx) || System.nanoTime() >= deadline) {
                        break;
                    }
                }
            }
        } finally {
            ctx.setLocal("loop-value", previousValue);
            ctx.setLocal("loop-index", previousIndex);
        }
    }

    /** Runs the body for one iteration; returns {@code true} if the loop should break. */
    private boolean runIteration(TriggerContext ctx) {
        try {
            IfSection.runAll(body, ctx);
        } catch (ContinueSignal ignored) {
            // proceed to the next iteration
        } catch (BreakSignal ignored) {
            return true;
        }
        return false;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? (long) Math.floor(number.doubleValue()) : 0L;
    }
}
