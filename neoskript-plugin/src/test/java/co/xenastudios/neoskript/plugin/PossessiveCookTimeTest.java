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

/**
 * Regression test: the possessive cook/burn-time patterns had a missing space
 * ({@code %object%'[s]cook[ing] time}), making them require "X'scook time" and thus unmatchable.
 */
class PossessiveCookTimeTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void possessiveCookTimeParses() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("ct.sk"), """
                command /ct:
                    trigger:
                        set block at (location of player) to furnace
                        set {_b::*} to blocks between (location of player) and (location of player)
                        set {_t} to {_b::1}'s cook time
                        send "OK" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "ct");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        // If "%block%'s cook time" failed to parse, the command would be dropped and OK never sent.
        assertTrue(seen.contains("OK"), "the possessive cook-time form must parse and run; got " + seen);
    }
}
