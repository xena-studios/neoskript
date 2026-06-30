package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.ReturnSignal;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptParserTest {

    private DefaultSyntaxRegistry registry;
    private EventRegistry events;
    private FunctionRegistry functions;
    private ScriptParser parser;
    private Map<String, Object> globals;

    @BeforeEach
    void setUp() {
        registry = new DefaultSyntaxRegistry();
        registerCommonSyntax(registry);

        events = new EventRegistry();
        events.register(Object.class, "join"); // event class is irrelevant to core parsing

        functions = new FunctionRegistry();
        parser = new ScriptParser(registry, events, functions);
        globals = new HashMap<>();
    }

    @Test
    void parsesAndExecutesAnEventTrigger() {
        Trigger trigger = parseOne("""
                on join:
                    set {_x} to 10
                    set {y} to {_x}
                """);
        assertEquals("join", trigger.eventName());
        assertEquals(2, trigger.statements().size());

        SimpleTriggerContext ctx = run(trigger);
        assertEquals(10.0, ctx.getLocal("x"));
        assertEquals(10.0, globals.get("y"));
    }

    @Test
    void executesWhileLoopsAndIfElse() {
        SimpleTriggerContext ctx = run(parseOne("""
                on join:
                    set {_x} to 0
                    while {_x} is less than 3:
                        add 1 to {_x}
                    if {_x} is 3:
                        set {_ok} to "yes"
                    else:
                        set {_ok} to "no"
                """));
        assertEquals(3.0, ctx.getLocal("x"));
        assertEquals("yes", ctx.getLocal("ok"));
    }

    @Test
    void executesTimedLoops() {
        SimpleTriggerContext ctx = run(parseOne("""
                on join:
                    set {_sum} to 0
                    loop 5 times:
                        add 1 to {_sum}
                """));
        assertEquals(5.0, ctx.getLocal("sum"));
    }

    @Test
    void iteratesListVariables() {
        SimpleTriggerContext ctx = run(parseOne("""
                on join:
                    set {fruits::1} to "apple"
                    set {fruits::2} to "pear"
                    set {_count} to 0
                    loop {fruits::*}:
                        add 1 to {_count}
                """));
        assertEquals(2.0, ctx.getLocal("count"));
        assertEquals("apple", globals.get("fruits::1"));
    }

    @Test
    void callsUserFunctions() {
        SimpleTriggerContext ctx = run(parseOne("""
                function double(n):
                    return {_n} * 2
                on join:
                    set {_r} to double(21)
                """));
        assertEquals(42.0, ctx.getLocal("r"));
        assertEquals(1, functions.size());
    }

    @Test
    void reportsUnknownEvents() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse("on explode:\n    set {_x} to 1\n"));
        assertEquals(1, error.line());
    }

    @Test
    void reportsUnknownStatements() {
        assertThrows(ParseException.class, () -> parser.parse("on join:\n    do a barrel roll\n"));
    }

    @Test
    void rejectsEmptyEventBody() {
        assertThrows(ParseException.class, () -> parser.parse("on join:\n"));
    }

    private Trigger parseOne(String source) {
        List<Trigger> triggers = parser.parse(source);
        assertEquals(1, triggers.size());
        return triggers.get(0);
    }

    private SimpleTriggerContext run(Trigger trigger) {
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, globals);
        trigger.execute(ctx);
        return ctx;
    }

    /** Bukkit-free syntax so core control flow can be tested without the language module. */
    private static void registerCommonSyntax(DefaultSyntaxRegistry registry) {
        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression target = (VariableExpression) arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> target.set(ctx, value.getSingle(ctx));
        });
        registry.registerEffect("add %object% to %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            VariableExpression target = (VariableExpression) arguments.get(1);
            return ctx -> {
                Object item = value.getSingle(ctx);
                if (target.isList()) {
                    target.addToList(ctx, item);
                } else {
                    double current = target.getSingle(ctx) instanceof Number n ? n.doubleValue() : 0;
                    target.set(ctx, current + ((Number) item).doubleValue());
                }
            };
        });
        registry.registerEffect("return %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            return ctx -> {
                throw new ReturnSignal(value.getSingle(ctx));
            };
        });
        // Register "less than" before equality so the more specific pattern matches first.
        registry.registerCondition("%object% is less than %object%", arguments -> {
            Expression<?> left = arguments.get(0);
            Expression<?> right = arguments.get(1);
            return ctx -> ((Number) left.getSingle(ctx)).doubleValue() < ((Number) right.getSingle(ctx)).doubleValue();
        });
        registry.registerCondition("%object% (is|=) %object%", arguments -> {
            Expression<?> left = arguments.get(0);
            Expression<?> right = arguments.get(1);
            return ctx -> numericEquals(left.getSingle(ctx), right.getSingle(ctx));
        });
    }

    private static boolean numericEquals(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }
}
