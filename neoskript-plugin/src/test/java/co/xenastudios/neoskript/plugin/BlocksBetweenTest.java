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

/** Behaviour test for 'blocks between %location% and %location%' (a cuboid of blocks). */
class BlocksBetweenTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void blocksBetween() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("bl.sk"), """
                command /bl:
                    trigger:
                        set {_a} to location of player
                        set {_b} to 5 blocks above location of player
                        set {_col::*} to blocks between {_a} and {_b}
                        send "COL:%size of {_col::*}%" to player
                        set {_c} to 1 block east of location of player
                        set {_box::*} to blocks between {_a} and (1 block above (1 block east of location of player))
                        send "BOX:%size of {_box::*}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "bl");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("COL:6"), "a 5-block vertical span is 6 blocks inclusive; got " + seen);
        assertTrue(seen.contains("BOX:4"), "a 2x2x1 cuboid (dx=1, dy=1, dz=0) is 4 blocks");
    }
}
