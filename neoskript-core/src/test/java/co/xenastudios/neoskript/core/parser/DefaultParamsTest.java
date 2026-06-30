package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.ReturnSignal;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultParamsTest {

    @Test
    void usesDefaultParameterWhenArgumentOmitted() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", a -> {
            VariableExpression t = (VariableExpression) a.get(0);
            Expression<?> v = a.get(1);
            return ctx -> t.set(ctx, v.getSingle(ctx));
        });
        registry.registerEffect("return %object%", a -> {
            Expression<?> v = a.get(0);
            return ctx -> {
                throw new ReturnSignal(v.getSingle(ctx));
            };
        });
        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");
        ScriptParser parser = new ScriptParser(registry, events, new FunctionRegistry());

        Trigger trigger = parser.parse("""
                function addOrDefault(a, b = 10):
                    return {_a} + {_b}
                on join:
                    set {_x} to addOrDefault(5)
                    set {_y} to addOrDefault(5, 1)
                """).get(0);

        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        trigger.execute(ctx);

        assertEquals(15.0, ctx.getLocal("x"));
        assertEquals(6.0, ctx.getLocal("y"));
    }
}
