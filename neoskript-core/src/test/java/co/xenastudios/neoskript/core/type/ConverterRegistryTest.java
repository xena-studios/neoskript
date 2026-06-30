package co.xenastudios.neoskript.core.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConverterRegistryTest {

    @Test
    void convertsDirectly() {
        ConverterRegistry registry = new ConverterRegistry();
        registry.register(Integer.class, String.class, i -> "n" + i);
        assertEquals("n5", registry.convert(5, String.class));
    }

    @Test
    void chainsConvertersBreadthFirst() {
        ConverterRegistry registry = new ConverterRegistry();
        registry.register(Integer.class, Long.class, Integer::longValue);
        registry.register(Long.class, String.class, l -> "L" + l);
        // Integer -> Long -> String
        assertEquals("L7", registry.convert(7, String.class));
        assertTrue(registry.canConvert(Integer.class, String.class));
    }

    @Test
    void returnsNullAndFalseWhenNoPath() {
        ConverterRegistry registry = new ConverterRegistry();
        registry.register(Integer.class, Long.class, Integer::longValue);
        assertNull(registry.convert(7, String.class));
        assertFalse(registry.canConvert(Integer.class, String.class));
    }

    @Test
    void returnsValueWhenAlreadyTargetType() {
        ConverterRegistry registry = new ConverterRegistry();
        assertEquals("x", registry.convert("x", String.class));
        assertEquals("x", registry.convert("x", CharSequence.class));
    }
}
