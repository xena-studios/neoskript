package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.NamedLocalExpression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the iterative {@link Interpreter} matches the recursive walk for sections and control
 * flow. Uses a manual "step scheduler" to drive any delays deterministically.
 */
class InterpreterTest {

    private ScriptParser parser;

    @BeforeEach
    void setUp() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", a -> {
            VariableExpression t = (VariableExpression) a.get(0);
            Expression<?> v = a.get(1);
            return ctx -> t.set(ctx, v.getSingle(ctx));
        });
        registry.registerEffect("add %object% to %object%", a -> {
            Expression<?> v = a.get(0);
            VariableExpression t = (VariableExpression) a.get(1);
            return ctx -> {
                double cur = t.getSingle(ctx) instanceof Number n ? n.doubleValue() : 0;
                t.set(ctx, cur + ((Number) v.getSingle(ctx)).doubleValue());
            };
        });
        registry.registerEffect("exit loop", a -> ctx -> {
            throw BreakSignal.INSTANCE;
        });
        registry.registerEffect("continue", a -> ctx -> {
            throw ContinueSignal.INSTANCE;
        });
        registry.registerCondition("%object% is %object%", a -> {
            Expression<?> l = a.get(0);
            Expression<?> r = a.get(1);
            return ctx -> numericEquals(l.getSingle(ctx), r.getSingle(ctx));
        });
        registry.registerExpression("loop-number", Object.class, a -> new NamedLocalExpression("loop-index"));
        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");
        parser = new ScriptParser(registry, events, new FunctionRegistry());
    }

    private Object run(String body) {
        Trigger trigger = parser.parse("on join:\n" + body).get(0);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        new Interpreter(ctx, null).run(trigger.statements());
        return ctx.getLocal("sum");
    }

    @Test
    void interpretsNestedLoopsAndIf() {
        Object sum = run("""
                    set {_sum} to 0
                    loop 3 times:
                        loop 2 times:
                            add 1 to {_sum}
                """);
        assertEquals(6.0, sum);
    }

    @Test
    void honoursBreakInsideInterpreter() {
        Object sum = run("""
                    set {_sum} to 0
                    loop 5 times:
                        add 1 to {_sum}
                        if loop-number is 3:
                            exit loop
                """);
        assertEquals(3.0, sum);
    }

    @Test
    void honoursContinueInsideInterpreter() {
        Object sum = run("""
                    set {_sum} to 0
                    loop 5 times:
                        if loop-number is 3:
                            continue
                        add 1 to {_sum}
                """);
        assertEquals(4.0, sum);
    }

    @Test
    void resumesAfterDelayViaScheduler() {
        // A queue-based scheduler that runs the continuation only when we drain it.
        List<Runnable> pending = new ArrayList<>();
        DelayScheduler scheduler = (task, ticks) -> pending.add(task);

        Trigger trigger = parser.parse("""
                on join:
                    set {_n} to 0
                    loop 3 times:
                        add 1 to {_n}
                        wait 1 tick
                    set {done} to {_n}
                """).get(0);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        new Interpreter(ctx, scheduler).run(trigger.statements());

        // Suspended after the first add; {done} not set yet.
        assertEquals(1.0, ctx.getLocal("n"));
        assertEquals(null, ctx.getGlobal("done"));

        // Drain the scheduled continuations until the trigger finishes.
        while (!pending.isEmpty()) {
            pending.remove(0).run();
        }
        assertEquals(3.0, ctx.getLocal("n"));
        assertEquals(3.0, ctx.getGlobal("done"));
    }

    private static boolean numericEquals(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }
}
