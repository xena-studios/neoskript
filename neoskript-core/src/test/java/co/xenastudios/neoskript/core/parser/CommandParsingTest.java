package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.CommandDefinition;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandParsingTest {

    @Test
    void parsesCommandStructureAndRunsTrigger() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression target = (VariableExpression) arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> target.set(ctx, value.getSingle(ctx));
        });
        CommandRegistry commands = new CommandRegistry();
        ScriptParser parser = new ScriptParser(registry, new EventRegistry(), new FunctionRegistry(), commands);

        parser.parse("""
                command /heal [<player>]:
                    permission: neoskript.heal
                    description: Heal someone
                    aliases: h, cure
                    trigger:
                        set {_done} to 1
                        set {ran} to 1
                """);

        assertEquals(1, commands.size());
        CommandDefinition command = commands.commands().get(0);
        assertEquals("heal", command.name());
        assertEquals("neoskript.heal", command.permission());
        assertEquals("Heal someone", command.description());
        assertEquals(List.of("h", "cure"), command.aliases());
        assertEquals(2, command.body().size());

        var globals = new HashMap<String, Object>();
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, globals);
        command.run(ctx);

        assertEquals(1.0, ctx.getLocal("done"));
        assertEquals(1.0, globals.get("ran"));
    }
}
