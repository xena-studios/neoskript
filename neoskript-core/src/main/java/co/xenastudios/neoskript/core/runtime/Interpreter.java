package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * An iterative, resumable interpreter for a trigger body. Unlike the recursive synchronous walk
 * (used for the delay-free fast path), this maintains an explicit frame stack so a {@code wait} can
 * suspend execution <em>anywhere</em> — including inside {@code if}/{@code while}/{@code loop} — and
 * resume on a later tick via the {@link DelayScheduler}.
 *
 * <p>One instance per trigger execution; it owns the frame stack and context.
 */
public final class Interpreter {

    private final TriggerContext ctx;
    private final DelayScheduler scheduler;
    private final Deque<Frame> stack = new ArrayDeque<>();

    public Interpreter(TriggerContext ctx, DelayScheduler scheduler) {
        this.ctx = ctx;
        this.scheduler = scheduler;
    }

    /** Starts executing the given top-level statements (and pumps until completion or a delay). */
    public void run(List<Statement> statements) {
        stack.push(new SeqFrame(statements));
        pump();
    }

    private void pump() {
        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            if (frame instanceof SeqFrame seq) {
                if (!seq.hasNext()) {
                    stack.pop();
                    continue;
                }
                if (!handle(seq.next())) {
                    return; // suspended on a delay; will resume via the scheduler
                }
            } else if (frame instanceof WhileFrame loop) {
                if (loop.shouldContinue(ctx)) {
                    stack.push(new SeqFrame(loop.body));
                } else {
                    stack.pop();
                }
            } else if (frame instanceof LoopFrame loop) {
                if (loop.advance(ctx)) {
                    stack.push(new SeqFrame(loop.body));
                } else {
                    loop.restore(ctx);
                    stack.pop();
                }
            }
        }
    }

    /** Processes one statement. Returns {@code false} if execution suspended on a delay. */
    private boolean handle(Statement statement) {
        if (statement instanceof DelayStatement delay) {
            if (scheduler == null) {
                return true; // no scheduler: behave synchronously (ignore the delay)
            }
            scheduler.runLater(this::pump, delay.ticks());
            return false;
        }
        if (statement instanceof IfSection branch) {
            List<Statement> taken = branch.condition().check(ctx) ? branch.thenBranch() : branch.elseBranch();
            if (taken != null && !taken.isEmpty()) {
                stack.push(new SeqFrame(taken));
            }
            return true;
        }
        if (statement instanceof WhileSection loop) {
            stack.push(new WhileFrame(loop));
            return true;
        }
        if (statement instanceof LoopSection loop) {
            stack.push(new LoopFrame(loop, ctx));
            return true;
        }
        // Leaf statement (effect, etc.) — run it, honouring control-flow signals.
        try {
            statement.run(ctx);
        } catch (StopSignal ignored) {
            stack.clear();
        } catch (BreakSignal ignored) {
            unwindLoop(true);
        } catch (ContinueSignal ignored) {
            unwindLoop(false);
        }
        return true;
    }

    /** Pops frames up to the nearest loop; for break, removes the loop frame too. */
    private void unwindLoop(boolean breakOut) {
        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            if (frame instanceof WhileFrame || frame instanceof LoopFrame) {
                if (breakOut) {
                    if (frame instanceof LoopFrame loop) {
                        loop.restore(ctx);
                    }
                    stack.pop();
                }
                return;
            }
            stack.pop();
        }
    }

    private interface Frame {
    }

    private static final class SeqFrame implements Frame {
        private final List<Statement> statements;
        private int index;

        SeqFrame(List<Statement> statements) {
            this.statements = statements;
        }

        boolean hasNext() {
            return index < statements.size();
        }

        Statement next() {
            return statements.get(index++);
        }
    }

    private static final class WhileFrame implements Frame {
        private final co.xenastudios.neoskript.api.syntax.Condition condition;
        private final List<Statement> body;
        private final long deadline = System.nanoTime() + WhileSection.MAX_NANOS;
        private long iterations;

        WhileFrame(WhileSection section) {
            this.condition = section.condition();
            this.body = section.body();
        }

        boolean shouldContinue(TriggerContext ctx) {
            if (iterations >= WhileSection.MAX_ITERATIONS || System.nanoTime() >= deadline) {
                return false;
            }
            if (!condition.check(ctx)) {
                return false;
            }
            iterations++;
            return true;
        }
    }

    private static final class LoopFrame implements Frame {
        private final List<Statement> body;
        private final boolean times;
        private final long count;
        private final Object[] values;
        private final java.util.function.BiConsumer<TriggerContext, Object> binder;
        private final long deadline = System.nanoTime() + LoopSection.MAX_NANOS;
        private final Object previousValue;
        private final Object previousIndex;
        private long position;

        LoopFrame(LoopSection section, TriggerContext ctx) {
            this.body = section.body();
            this.times = section.isTimes();
            this.binder = section.binder();
            Expression<?> source = section.source();
            if (times) {
                Object value = source.getSingle(ctx);
                this.count = value instanceof Number n ? (long) Math.floor(n.doubleValue()) : 0L;
                this.values = null;
            } else {
                this.count = 0L;
                this.values = source.getAll(ctx);
            }
            this.previousValue = ctx.getLocal("loop-value");
            this.previousIndex = ctx.getLocal("loop-index");
        }

        boolean advance(TriggerContext ctx) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            if (times) {
                if (position >= count) {
                    return false;
                }
                position++;
                ctx.setLocal("loop-value", (double) position);
                ctx.setLocal("loop-index", (double) position);
                return true;
            }
            if (position >= values.length) {
                return false;
            }
            ctx.setLocal("loop-value", values[(int) position]);
            ctx.setLocal("loop-index", (double) (position + 1));
            if (binder != null) {
                binder.accept(ctx, values[(int) position]);
            }
            position++;
            return true;
        }

        void restore(TriggerContext ctx) {
            ctx.setLocal("loop-value", previousValue);
            ctx.setLocal("loop-index", previousIndex);
        }
    }
}
