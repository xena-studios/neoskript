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

/** Behaviour tests for the slot subsystem: read, set, and clear an inventory slot. */
class SlotTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void slots() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("sl.sk"), """
                command /sl:
                    trigger:
                        set {_inv} to player's inventory
                        send "EMPTY:%slot 0 of {_inv}%" to player
                        set slot 0 of {_inv} to diamond
                        send "S0:%slot 0 of {_inv}%" to player
                        set {_got} to slot 0 of {_inv}
                        send "GOT:%{_got}%" to player
                        delete slot 0 of {_inv}
                        send "AFTER:%slot 0 of {_inv}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "sl");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("EMPTY:air"), "an empty slot renders as air; got " + seen);
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("S0:") && x.contains("diamond")),
                "after set, slot 0 holds a diamond");
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("GOT:") && x.contains("diamond")),
                "a slot stored in a variable still reads its contents");
        assertTrue(seen.contains("AFTER:air"), "after delete, slot 0 is empty again");
    }
}
