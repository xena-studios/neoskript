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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncWaitTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;
    private final Set<String> seen = new HashSet<>();

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
    void waitInsideALoopSpreadsAcrossTicks() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("loopwait.sk"), """
                on join:
                    loop 3 times:
                        send "tick %loop-number%" to player
                        wait 2 ticks
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();

        drain(player);
        assertTrue(seen.contains("tick 1"), "first iteration runs immediately");
        assertFalse(seen.contains("tick 2"), "second iteration must wait for the delay");

        server.getScheduler().performTicks(2);
        drain(player);
        assertTrue(seen.contains("tick 2"), "second iteration runs after the first delay");
        assertFalse(seen.contains("tick 3"));

        server.getScheduler().performTicks(2);
        drain(player);
        assertTrue(seen.contains("tick 3"), "third iteration runs after the second delay");
    }

    private void drain(PlayerMock player) {
        String message;
        while ((message = player.nextMessage()) != null) {
            seen.add(message);
        }
    }
}
