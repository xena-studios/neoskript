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
    void uuidAndCalcExperience() {
        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                eval("uuid(\"00000000-0000-0000-0000-000000000001\")"));
        assertEquals(null, eval("uuid(\"not-a-uuid\")"));
        assertEquals(7.0, eval("calcExperience(1)"));   // 1 + 6
        assertEquals(160.0, eval("calcExperience(10)")); // 100 + 60
    }

    @Test
    void locationAndVectorFunctions() {
        org.bukkit.Location loc = (org.bukkit.Location) eval("location(1, 2, 3)");
        assertEquals(1.0, loc.getX());
        assertEquals(2.0, loc.getY());
        assertEquals(3.0, loc.getZ());
        org.bukkit.util.Vector v = (org.bukkit.util.Vector) eval("vector(4, 5, 6)");
        assertEquals(4.0, v.getX());
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
    void parityMathFunctions() {
        assertEquals(5.0, eval("ceiling(4.1)"));
        assertEquals(3.0, eval("clamp(5, 0, 3)"));
        assertEquals(0.0, eval("clamp(0 - 1, 0, 3)"));
        assertEquals(3.0, eval("root(27, 3)"));
        assertEquals(120.0, eval("factorial(5)"));
        assertEquals(10.0, eval("combinations(5, 2)"));
        assertEquals(20.0, eval("permutations(5, 2)"));
        assertEquals(4.0, eval("mean(2, 4, 6)"));
        assertEquals(2.5, eval("median(1, 2, 3, 4)"));
        assertEquals(2.0, eval("median(1, 2, 3)"));
    }

    @Test
    void parityUtilityFunctions() {
        assertEquals(true, eval("isNaN(sqrt(0 - 1))"));
        assertEquals(false, eval("isNaN(5)"));
        assertEquals(255.0, eval("fromBase(\"ff\", 16)"));
        assertEquals("ff", eval("toBase(255, 16)"));
        assertEquals("abc", eval("concat(\"a\", \"b\", \"c\")"));
        assertEquals(true, eval("case_equals(\"a\", \"a\")"));
        assertEquals(false, eval("case_equals(\"a\", \"A\")"));
        assertEquals("1,234,567", eval("formatNumber(1234567)"));
        assertEquals(java.io.File.separator, eval("file_separator()"));
        assertEquals(System.lineSeparator(), eval("line_separator()"));
    }

    @Test
    void trigonometricAndLogFunctions() {
        assertEquals(0.0, eval("sin(0)"));
        assertEquals(1.0, eval("cos(0)"));
        assertEquals(0.0, eval("tan(0)"));
        assertEquals(0.0, eval("asin(0)"));
        assertEquals(0.0, eval("atan(0)"));
        assertEquals(0.0, eval("atan2(0, 1)"));
        assertEquals(0.0, eval("ln(1)"));
        assertEquals(1.0, eval("exp(0)"));
        assertEquals(0.0, eval("log(1)"));
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
