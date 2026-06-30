package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptParserTest {

    private DefaultSyntaxRegistry registry;
    private EventRegistry events;
    private ScriptParser parser;

    @BeforeEach
    void setUp() {
        registry = new DefaultSyntaxRegistry();
        // A Bukkit-free "set" effect so core can be tested without the language module.
        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression target = (VariableExpression) arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> target.set(ctx, value.getSingle(ctx));
        });

        events = new EventRegistry();
        events.register(Object.class, "join"); // event class is irrelevant to core parsing

        parser = new ScriptParser(registry, events);
    }

    @Test
    void parsesAndExecutesAnEventTrigger() {
        List<Trigger> triggers = parser.parse("""
                # set a local then copy it to a global
                on join:
                    set {_x} to 10
                    set {y} to {_x}
                """);

        assertEquals(1, triggers.size());
        Trigger trigger = triggers.get(0);
        assertEquals("join", trigger.eventName());
        assertEquals(Object.class, trigger.eventClass());
        assertEquals(2, trigger.effects().size());

        Map<String, Object> globals = new HashMap<>();
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, globals);
        trigger.execute(ctx);

        assertEquals(10.0, ctx.getLocal("x"));
        assertEquals(10.0, globals.get("y"));
    }

    @Test
    void reportsUnknownEvents() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse("on explode:\n    set {_x} to 1\n"));
        assertEquals(1, error.line());
    }

    @Test
    void reportsUnknownStatements() {
        assertThrows(ParseException.class,
                () -> parser.parse("on join:\n    do a barrel roll\n"));
    }

    @Test
    void rejectsEmptyEventBody() {
        assertThrows(ParseException.class, () -> parser.parse("on join:\n"));
    }
}
