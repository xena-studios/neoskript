package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour test for the persistent data value expression (read/write on an entity's PDC). */
class PersistentDataTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void persistentData() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("pd.sk"), """
                command /pd:
                    trigger:
                        set {_k} to "score"
                        set {_t} to "title"
                        send "EMPTY:%persistent data value {_k} of player%" to player
                        set persistent data value {_k} of player to 42
                        set persistent data value {_t} of player to "champion"
                        send "NUM:%persistent data value {_k} of player%" to player
                        send "STR:%persistent data value {_t} of player%" to player
                        delete persistent data value {_k} of player
                        send "AFTER:%persistent data value {_k} of player%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "pd");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("EMPTY:<none>"), "no data set initially; got " + seen);
        assertTrue(seen.contains("NUM:42"), "numeric persistent data reads back");
        assertTrue(seen.contains("STR:champion"), "string persistent data reads back");
        assertTrue(seen.contains("AFTER:<none>"), "deleting persistent data clears it");
    }
}
