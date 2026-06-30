package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the new entity-state effects actually mutate the entity. */
class EntityEffectsTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void entityStateEffectsApply() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("entityfx.sk"), """
                command /efx:
                    trigger:
                        make player invisible
                        make player invulnerable
                        silence player
                        disable gravity for player
                        push player with velocity vector(0, 1, 0)
                        make player fly
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "efx");

        assertTrue(player.isInvisible(), "make invisible");
        assertTrue(player.isInvulnerable(), "make invulnerable");
        assertTrue(player.isSilent(), "silence");
        assertFalse(player.hasGravity(), "disable gravity");
        assertTrue(player.getVelocity().getY() > 0, "push adds upward velocity");
        assertTrue(player.isFlying(), "make fly");
    }
}
