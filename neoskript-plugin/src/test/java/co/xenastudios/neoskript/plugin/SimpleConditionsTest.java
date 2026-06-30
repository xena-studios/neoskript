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

/** Verifies hand-implemented conditions: alphanumeric, is a block, has potion effect. */
class SimpleConditionsTest {

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
    void conditions() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("sc.sk"), """
                command /sc:
                    trigger:
                        apply "speed" to player for 10 seconds
                        if "abc123" is alphanumeric:
                            send "ALNUM" to player
                        if "a b!" is alphanumeric:
                            send "BAD" to player
                        if item("stone") is a block:
                            send "BLOCK" to player
                        if player has potion effect "speed":
                            send "POT" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "sc");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("ALNUM"), "alphanumeric true for abc123");
        assertTrue(!seen.contains("BAD"), "alphanumeric false for 'a b!'");
        assertTrue(seen.contains("BLOCK"), "stone is a block");
        assertTrue(seen.contains("POT"), "has potion effect speed");
    }
}
