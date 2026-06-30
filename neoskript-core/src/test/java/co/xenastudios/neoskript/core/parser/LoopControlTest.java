package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.NamedLocalExpression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.BreakSignal;
import co.xenastudios.neoskript.core.runtime.ContinueSignal;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopControlTest {

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
        trigger.execute(ctx);
        return ctx.getLocal("sum");
    }

    @Test
    void exitLoopBreaks() {
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
    void continueSkipsIteration() {
        Object sum = run("""
                    set {_sum} to 0
                    loop 5 times:
                        if loop-number is 3:
                            continue
                        add 1 to {_sum}
                """);
        assertEquals(4.0, sum);
    }

    private static boolean numericEquals(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }
}
