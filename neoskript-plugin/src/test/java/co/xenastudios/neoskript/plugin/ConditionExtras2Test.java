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

class ConditionExtras2Test {

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
    void environmentalConditionsParseAndEvaluate() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        // A survival player can build but is not in water; the messages confirm each condition evaluated.
        Files.writeString(scripts.resolve("env.sk"), """
                command /check:
                    trigger:
                        if player can build:
                            send "can build" to player
                        if player is in water:
                            send "wet" to player
                        send "done" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        server.dispatchCommand(player, "check");

        boolean canBuild = false;
        boolean wet = false;
        boolean done = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("can build")) canBuild = true;
            if (message.contains("wet")) wet = true;
            if (message.contains("done")) done = true;
        }
        assertTrue(canBuild, "a survival player can build");
        assertTrue(done, "the trigger should complete");
        // 'wet' must NOT have been sent (player isn't in water), proving the condition was false.
        assertTrue(!wet, "a freshly-spawned player is not in water");
    }
}
