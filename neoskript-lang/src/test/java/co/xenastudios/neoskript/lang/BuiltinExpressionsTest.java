package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.core.parser.ExpressionParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinExpressionsTest {

    private final DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
    private final EventRegistry events = new EventRegistry();
    private final FunctionRegistry functions = new FunctionRegistry();
    private final ExpressionParser expressions;
    private final ScriptParser scripts;

    BuiltinExpressionsTest() {
        BuiltinModule.registerAll(registry);
        BuiltinFunctions.registerAll(functions);
        BuiltinEvents.registerAll(events);
        expressions = new ExpressionParser(registry, functions);
        scripts = new ScriptParser(registry, events, functions);
    }

    private Object eval(String source, Map<String, Object> globals) {
        return expressions.parse(source).getSingle(new SimpleTriggerContext(null, globals));
    }

    private Object[] evalAll(String source, Map<String, Object> globals) {
        return expressions.parse(source).getAll(new SimpleTriggerContext(null, globals));
    }

    private boolean condition(String condition) {
        List<Trigger> triggers = scripts.parse("on join:\n    if " + condition + ":\n        set {_r} to \"Y\"\n");
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        triggers.get(0).execute(ctx);
        return "Y".equals(ctx.getLocal("r"));
    }

    @Test
    void stringExpressions() {
        Map<String, Object> g = new HashMap<>();
        assertEquals("HELLO", eval("uppercase \"hello\"", g));
        assertEquals("hello", eval("lowercase \"HELLO\"", g));
        assertEquals(5.0, eval("length of \"hello\"", g));
        assertArrayEquals(new Object[]{"a", "b", "c"}, evalAll("\"a,b,c\" split at \",\"", g));
    }

    @Test
    void vectorCoordinatesAndLength() {
        Map<String, Object> g = new HashMap<>();
        assertEquals(3.0, eval("x of vector(3, 4, 0)", g));
        assertEquals(4.0, eval("y of vector(3, 4, 0)", g));
        assertEquals(5.0, eval("length of vector(3, 4, 0)", g)); // 3-4-5 triangle
        assertEquals(3.0, eval("length of \"abc\"", g));          // strings still measured by length
    }

    @Test
    void localFunctionWithTypedParameterCoercesArgument() {
        // The string "5" is coerced to a number by the declared parameter type, so *2 yields 10.
        scripts.parse("local function double(n: number) :: number:\n    return {_n} * 2\n");
        assertEquals(10.0, eval("double(\"5\")", new HashMap<>()));
    }

    @Test
    void leadingArticleIsTolerated() {
        Map<String, Object> g = new HashMap<>();
        // "the"/"a"/"an" are stripped as a fallback so article-laden phrasing still parses.
        assertEquals(5.0, eval("the length of \"hello\"", g));
        assertEquals("HELLO", eval("uppercase \"hello\"", g));
    }

    @Test
    void listExpressions() {
        Map<String, Object> g = new HashMap<>();
        g.put("fruits::1", "banana");
        g.put("fruits::2", "apple");

        assertEquals("banana", eval("first element of {fruits::*}", g));
        assertEquals("apple", eval("last element of {fruits::*}", g));
        assertArrayEquals(new Object[]{"apple", "banana"}, evalAll("sorted {fruits::*}", g));
        assertArrayEquals(new Object[]{"apple", "banana"}, evalAll("reversed {fruits::*}", g));
        assertEquals("banana, apple", eval("join {fruits::*} with \", \"", g));
    }

    @Test
    void stringConditions() {
        assertTrue(condition("\"abc\" contains \"b\""));
        assertFalse(condition("\"abc\" contains \"z\""));
        assertTrue(condition("\"abc\" doesn't contain \"z\""));
        assertTrue(condition("\"hello world\" starts with \"hello\""));
        assertTrue(condition("\"hello world\" ends with \"world\""));
        assertTrue(condition("\"a1b2\" matches \"[a-z0-9]+\""));
        assertFalse(condition("\"abc!\" matches \"[a-z]+\""));
    }

    @Test
    void numericConditions() {
        assertTrue(condition("5 is between 1 and 10"));
        assertFalse(condition("15 is between 1 and 10"));
    }

    @Test
    void commaAndSeparatedListLiterals() {
        Map<String, Object> g = new HashMap<>();
        assertArrayEquals(new Object[]{1.0, 2.0, 3.0}, evalAll("1, 2 and 3", g));
        assertArrayEquals(new Object[]{"a", "b"}, evalAll("\"a\" and \"b\"", g));
    }

    @Test
    void nowReturnsACurrentTimestamp() {
        Object now = eval("now", new HashMap<>());
        assertTrue(now instanceof Double && (Double) now > 0, "now should be a positive timestamp");
    }

    @Test
    void chanceConditionBounds() {
        assertTrue(condition("chance of 100"));
        assertFalse(condition("chance of 0"));
    }

    @Test
    void replaceEffect() {
        List<Trigger> triggers = scripts.parse("""
                on join:
                    set {_s} to "hello"
                    replace "l" with "L" in {_s}
                """);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        triggers.get(0).execute(ctx);
        assertEquals("heLLo", ctx.getLocal("s"));
    }

    @Test
    void loopsOverListLiterals() {
        List<Trigger> triggers = scripts.parse("""
                on join:
                    set {_count} to 0
                    loop 1, 2 and 3:
                        add 1 to {_count}
                """);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        triggers.get(0).execute(ctx);
        assertEquals(3.0, ctx.getLocal("count"));
    }
}
