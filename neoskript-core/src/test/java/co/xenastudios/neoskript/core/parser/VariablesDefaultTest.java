package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariablesDefaultTest {

    private static final String SCRIPT = """
            variables:
                {coins} = 100
            on join:
                set {_c} to {coins}
            """;

    private ScriptParser parser;

    @BeforeEach
    void setUp() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", a -> {
            VariableExpression t = (VariableExpression) a.get(0);
            Expression<?> v = a.get(1);
            return ctx -> t.set(ctx, v.getSingle(ctx));
        });
        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");
        parser = new ScriptParser(registry, events, new FunctionRegistry());
    }

    private Object runWith(Map<String, Object> globals) {
        List<Trigger> triggers = parser.parse(SCRIPT);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, globals);
        triggers.forEach(trigger -> trigger.execute(ctx));
        return ctx.getLocal("c");
    }

    @Test
    void seedsDefaultWhenUnset() {
        assertEquals(100.0, runWith(new HashMap<>()));
    }

    @Test
    void keepsExistingValue() {
        Map<String, Object> globals = new HashMap<>();
        globals.put("coins", 5.0);
        assertEquals(5.0, runWith(globals));
    }
}
