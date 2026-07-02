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
 * Regression test: generated expressions/effects with omitted optional arguments must not throw an
 * NPE (previously {@code a.get(N).getSingle(ctx)} dereferenced a null optional slot).
 */
class OptionalArgNpeTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void omittedOptionalsDoNotCrash() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("npe.sk"), """
                command /npe:
                    trigger:
                        enable pvp
                        set {_a} to the description
                        set {_b} to cook time
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "npe");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        // If any omitted-optional expression NPE'd, the trigger would abort before this send.
        assertTrue(seen.contains("DONE"),
                "omitted-optional expressions must not crash the trigger; got " + seen);
    }
}
