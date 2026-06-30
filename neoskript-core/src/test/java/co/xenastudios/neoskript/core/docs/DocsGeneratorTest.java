package co.xenastudios.neoskript.core.docs;

import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocsGeneratorTest {

    @Test
    void rendersRegisteredSyntax() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("broadcast %string%", arguments -> ctx -> {
        });
        registry.registerCondition("%object% is set", arguments -> ctx -> true);
        registry.registerExpression("player", Object.class, arguments -> null);

        EventRegistry events = new EventRegistry();
        events.register(Object.class, "join");

        String markdown = DocsGenerator.generate(registry, events);

        assertTrue(markdown.contains("# NeoSkript Syntax Reference"));
        assertTrue(markdown.contains("broadcast %string%"));
        assertTrue(markdown.contains("%object% is set"));
        assertTrue(markdown.contains("player"));
        assertTrue(markdown.contains("on join"));
    }
}
