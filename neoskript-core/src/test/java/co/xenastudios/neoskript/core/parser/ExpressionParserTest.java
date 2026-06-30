package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.NumberLiteral;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionParserTest {

    private final ExpressionParser parser =
            new ExpressionParser(new DefaultSyntaxRegistry(), new FunctionRegistry());

    @Test
    void parsesNumberLiterals() {
        Expression<?> expr = parser.parse("3.5");
        assertInstanceOf(NumberLiteral.class, expr);
        assertEquals(3.5, expr.getSingle(context()));
    }

    @Test
    void parsesStringLiterals() {
        assertEquals("hello world", parser.parse("\"hello world\"").getSingle(context()));
    }

    @Test
    void parsesLocalAndGlobalVariables() {
        VariableExpression local = (VariableExpression) parser.parse("{_count}");
        assertTrue(local.isLocal());
        assertEquals("count", local.name());

        VariableExpression global = (VariableExpression) parser.parse("{score}");
        assertFalse(global.isLocal());
        assertEquals("score", global.name());
    }

    @Test
    void interpolatesExpressionsInStrings() {
        SimpleTriggerContext ctx = context();
        ctx.setLocal("x", 5.0);
        assertEquals("x is 5!", parser.parse("\"x is %{_x}%!\"").getSingle(ctx));
    }

    @Test
    void evaluatesArithmeticWithPrecedence() {
        assertEquals(14.0, parser.parse("2 + 3 * 4").getSingle(context()));
        assertEquals(20.0, parser.parse("(2 + 3) * 4").getSingle(context()));
        assertEquals(5.0, parser.parse("10 - 2 - 3").getSingle(context()));
        assertEquals(-5.0, parser.parse("-5").getSingle(context()));
    }

    @Test
    void arithmeticReadsVariables() {
        SimpleTriggerContext ctx = context();
        ctx.setLocal("x", 4.0);
        assertEquals(9.0, parser.parse("{_x} + 5").getSingle(ctx));
    }

    @Test
    void rejectsUnknownExpressions() {
        assertThrows(ParseException.class, () -> parser.parse("totally bogus thing"));
    }

    private static SimpleTriggerContext context() {
        return new SimpleTriggerContext(null, new HashMap<>());
    }
}
