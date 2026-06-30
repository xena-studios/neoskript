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

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionExtrasTest {

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
    void canFlyConditionReflectsFlightAllowance() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("fly.sk"), """
                command /letfly:
                    trigger:
                        allow flight for player
                        if player can fly:
                            send "you can fly" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "letfly");

        assertTrue(player.getAllowFlight());
        boolean told = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("you can fly")) {
                told = true;
                break;
            }
        }
        assertTrue(told, "the 'can fly' condition should pass after allowing flight");
    }
}
