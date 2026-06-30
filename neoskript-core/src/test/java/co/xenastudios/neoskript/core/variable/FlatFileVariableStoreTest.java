package co.xenastudios.neoskript.core.variable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatFileVariableStoreTest {

    @Test
    void roundTripsSimpleTypes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("variables.csv");
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("count", 42.0);
        in.put("name", "Steve");
        in.put("flag", true);
        in.put("greeting", "tab\there\nand newline");

        FlatFileVariableStore.save(file, in);
        Map<String, Object> out = FlatFileVariableStore.load(file);

        assertEquals(42.0, out.get("count"));
        assertEquals("Steve", out.get("name"));
        assertEquals(true, out.get("flag"));
        assertEquals("tab\there\nand newline", out.get("greeting"));
    }

    @Test
    void skipsNonPersistableValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("variables.csv");
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("ok", "value");
        in.put("weird", new Object());

        FlatFileVariableStore.save(file, in);
        Map<String, Object> out = FlatFileVariableStore.load(file);

        assertTrue(out.containsKey("ok"));
        assertFalse(out.containsKey("weird"));
    }

    @Test
    void loadingMissingFileYieldsEmpty(@TempDir Path dir) throws IOException {
        assertTrue(FlatFileVariableStore.load(dir.resolve("absent.csv")).isEmpty());
    }

    @Test
    void roundTripsCustomSerializedValues(@TempDir Path dir) throws IOException {
        ValueSerializers.register(new ValueSerializer() {
            @Override
            public String id() {
                return "char";
            }

            @Override
            public boolean handles(Object value) {
                return value instanceof Character;
            }

            @Override
            public String serialize(Object value) {
                return value.toString();
            }

            @Override
            public Object deserialize(String data) {
                return data.isEmpty() ? null : data.charAt(0);
            }
        });

        Path file = dir.resolve("variables.csv");
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("letter", 'A');
        FlatFileVariableStore.save(file, in);

        assertEquals('A', FlatFileVariableStore.load(file).get("letter"));
    }
}
