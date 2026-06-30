package co.xenastudios.neoskript.core.type;

import co.xenastudios.neoskript.api.type.Type;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TypeRegistryTest {

    private static final Type<String> TEXT = new Type<>() {
        @Override
        public Class<String> typeClass() {
            return String.class;
        }

        @Override
        public String codeName() {
            return "text";
        }

        @Override
        public Optional<String> parse(String input) {
            return Optional.of(input);
        }

        @Override
        public String toDisplayString(String value) {
            return "<" + value + ">";
        }
    };

    @Test
    void resolvesByCodeNameCaseInsensitively() {
        TypeRegistry registry = new TypeRegistry();
        registry.register(TEXT);
        assertSame(TEXT, registry.byCodeName("TEXT"));
        assertNull(registry.byCodeName("number"));
    }

    @Test
    void rendersValuesViaTheirType() {
        TypeRegistry registry = new TypeRegistry();
        registry.register(TEXT);
        assertEquals("<hi>", registry.display("hi"));
        assertNull(registry.display(42), "no type registered for Integer");
        assertNull(registry.display(null));
    }
}
