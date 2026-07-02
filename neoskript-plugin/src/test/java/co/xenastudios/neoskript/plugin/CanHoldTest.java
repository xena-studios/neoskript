package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour tests for the 'can hold' inventory-capacity condition. */
class CanHoldTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void canHold() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("ch.sk"), """
                command /ch:
                    trigger:
                        clear player's inventory
                        if player's inventory can hold diamond:
                            send "CAN1" to player
                        if player's inventory has enough space for diamond:
                            send "SPACE1" to player
                        loop 36 times:
                            set slot (loop-value - 1) of player's inventory to stone
                        if player's inventory can hold diamond:
                            send "FULL-CAN-BAD" to player
                        if player's inventory can't hold diamond:
                            send "FULL-CANT" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "ch");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("CAN1"), "empty inventory can hold a diamond; got " + seen);
        assertTrue(seen.contains("SPACE1"), "the 'has enough space for' phrasing works too");
        assertFalse(seen.contains("FULL-CAN-BAD"), "a full inventory cannot hold another diamond");
        assertTrue(seen.contains("FULL-CANT"), "the negated 'can't hold' form matches when the inventory is full");
    }
}
