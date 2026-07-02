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

/** Behaviour test for 'blocks in radius %number% of %location%'. */
class BlockSphereTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void blocksInRadius() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("bs.sk"), """
                command /bs:
                    trigger:
                        set {_r0::*} to blocks in radius 0 of (location of player)
                        send "R0:%size of {_r0::*}%" to player
                        set {_r1::*} to blocks in radius 1 around (location of player)
                        send "R1:%size of {_r1::*}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "bs");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("R0:1"), "radius 0 is just the centre block; got " + seen);
        assertTrue(seen.contains("R1:7"), "radius 1 is the centre plus its 6 face neighbours");
    }
}
