package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlVariableStoreTest {

    @Test
    void roundTripsPrimitivesThroughJdbc() throws SQLException {
        // H2 in-memory, kept alive for the connection lifetime of this test.
        SqlVariableStore store = new SqlVariableStore("jdbc:h2:mem:nsk_test;DB_CLOSE_DELAY=-1");

        Map<String, Object> in = new LinkedHashMap<>();
        in.put("count", 42.0);
        in.put("name", "Steve");
        in.put("flag", true);
        store.save(in);

        Map<String, Object> out = store.load();
        assertEquals(42.0, out.get("count"));
        assertEquals("Steve", out.get("name"));
        assertEquals(true, out.get("flag"));

        // Saving again replaces the whole set.
        store.save(Map.of("only", "value"));
        Map<String, Object> replaced = store.load();
        assertEquals(1, replaced.size());
        assertEquals("value", replaced.get("only"));
    }
}
