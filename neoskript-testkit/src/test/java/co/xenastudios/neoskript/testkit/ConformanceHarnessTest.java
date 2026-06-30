package co.xenastudios.neoskript.testkit;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.lexer.Token;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConformanceHarnessTest {

    private final ConformanceHarness harness = new ConformanceHarness();

    @Test
    void tokenizesSampleScript() {
        List<Token> tokens = harness.tokenize("scripts/hello.sk");
        assertFalse(tokens.isEmpty(), "expected tokens from the sample script");
        assertTrue(tokens.stream().anyMatch(t -> t.text().equals("broadcast")),
                "expected the 'broadcast' keyword to be tokenized");
    }

    @Test
    void parsesAndExecutesAFixture() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression target = (VariableExpression) arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> target.set(ctx, value.getSingle(ctx));
        });
        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");

        SimpleTriggerContext ctx = harness.run(registry, events, harness.readResource("scripts/runnable.sk"));

        assertEquals(5.0, ctx.getLocal("x"));
        assertEquals(5.0, ctx.getGlobal("result"));
    }
}
