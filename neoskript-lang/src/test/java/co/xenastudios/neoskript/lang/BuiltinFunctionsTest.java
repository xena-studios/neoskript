package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.parser.ExpressionParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltinFunctionsTest {

    private final ExpressionParser parser = newParser();

    private static ExpressionParser newParser() {
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        return new ExpressionParser(new DefaultSyntaxRegistry(), functions);
    }

    private Object eval(String source) {
        Expression<?> expr = parser.parse(source);
        return expr.getSingle(new SimpleTriggerContext(null, new HashMap<>()));
    }

    @Test
    void unaryMathFunctions() {
        assertEquals(5.0, eval("abs(0 - 5)"));
        assertEquals(4.0, eval("floor(4.9)"));
        assertEquals(5.0, eval("ceil(4.1)"));
        assertEquals(3.0, eval("round(3.4)"));
        assertEquals(3.0, eval("sqrt(9)"));
    }

    @Test
    void variadicMathFunctions() {
        assertEquals(1.0, eval("min(3, 1, 2)"));
        assertEquals(3.0, eval("max(3, 1, 2)"));
        assertEquals(6.0, eval("sum(1, 2, 3)"));
        assertEquals(24.0, eval("product(2, 3, 4)"));
    }

    @Test
    void modAndNesting() {
        assertEquals(1.0, eval("mod(10, 3)"));
        assertEquals(7.0, eval("abs(0 - max(3, 7))"));
    }

    @Test
    void dateBuildsCalendarDate() {
        java.util.Date date = (java.util.Date) eval("date(2026, 6, 30)");
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        assertEquals(2026, calendar.get(java.util.Calendar.YEAR));
        assertEquals(6, calendar.get(java.util.Calendar.MONTH) + 1); // month is 0-based
        assertEquals(30, calendar.get(java.util.Calendar.DAY_OF_MONTH));
    }

    @Test
    void rgbBuildsColour() {
        org.bukkit.Color colour = (org.bukkit.Color) eval("rgb(10, 20, 30)");
        assertEquals(10, colour.getRed());
        assertEquals(20, colour.getGreen());
        assertEquals(30, colour.getBlue());
        // out-of-range channels are clamped
        org.bukkit.Color clamped = (org.bukkit.Color) eval("rgb(300, 0 - 5, 128)");
        assertEquals(255, clamped.getRed());
        assertEquals(0, clamped.getGreen());
    }
}
