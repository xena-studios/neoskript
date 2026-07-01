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

/** Behaviour-tests the MockBukkit-runnable subset of the source-grounded bulk conditions. */
class BulkCondBehaviorTest {

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
    void bulkCondBehaviour() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("bc.sk"), """
                command /bc:
                    trigger:
                        if player is valid:
                            send "VALID" to player
                        if item("stone") is solid:
                            send "SOLID" to player
                        if item("stone") is stackable:
                            send "STACK" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "bc");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("VALID"), "player is valid");
        assertTrue(seen.contains("SOLID"), "stone is solid");
        assertTrue(seen.contains("STACK"), "stone is stackable");
    }
}
