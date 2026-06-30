package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionRecursionTest {

    @Test
    void infiniteRecursionIsAbortedWithAControlledError() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");
        FunctionRegistry functions = new FunctionRegistry();
        ScriptParser parser = new ScriptParser(registry, events, functions);

        List<Trigger> triggers = parser.parse("""
                function recurse(n):
                    recurse(0)
                on join:
                    recurse(1)
                """);

        Trigger trigger = triggers.get(0);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());

        // Without the depth guard this would be a StackOverflowError; with it, a catchable exception.
        assertThrows(IllegalStateException.class, () -> trigger.execute(ctx));
    }
}
