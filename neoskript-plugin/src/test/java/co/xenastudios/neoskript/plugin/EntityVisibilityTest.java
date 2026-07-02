package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour test for hiding/revealing entities from a viewer. */
class EntityVisibilityTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void hideReveal() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("vis.sk"), """
                command /hideall:
                    trigger:
                        hide all players from player
                command /revealall:
                    trigger:
                        reveal all players from player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock viewer = server.addPlayer();
        PlayerMock target = server.addPlayer();

        server.dispatchCommand(viewer, "hideall");
        assertFalse(viewer.canSee(target), "target should be hidden from the viewer");

        server.dispatchCommand(viewer, "revealall");
        assertTrue(viewer.canSee(target), "target should be visible again after reveal");
    }
}
