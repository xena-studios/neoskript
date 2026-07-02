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

/** Behaviour tests for the copy effect and slot-index expression. */
class CopySlotIndexTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void copyAndIndex() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("cs.sk"), """
                command /cs:
                    trigger:
                        set {_a::1} to 10
                        set {_a::2} to 20
                        copy {_a::*} into {_b::*}
                        set {_a::1} to 999
                        send "COPY1:%{_b::1}%" to player
                        send "COPY2:%{_b::2}%" to player
                        set {_slot} to slot 5 of player's inventory
                        send "IDX:%index of {_slot}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "cs");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("COPY1:10"), "copied list keeps original values; got " + seen);
        assertTrue(seen.contains("COPY2:20"), "copied list keeps all elements independently of the source");
        assertTrue(seen.contains("IDX:5"), "the index of slot 5 is 5");
    }
}
