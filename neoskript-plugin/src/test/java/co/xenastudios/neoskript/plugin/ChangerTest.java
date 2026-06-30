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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the generic changer system (set/add/remove/reset dispatched to a changeable expression)
 * against new numeric properties that have no dedicated set-effect, so the changer path is what runs.
 */
class ChangerTest {

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
    void changerSetsAddsRemovesProperties() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("changer.sk"), """
                command /sat:
                    trigger:
                        set saturation of player to 10
                        add 5 to saturation of player
                        remove 3 from saturation of player
                        set {_read} to saturation of player
                        send "SAT=%{_read}%" to player
                        set fire ticks of player to 40
                        send "FIRE=%fire ticks of player%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "sat");

        // 10 + 5 - 3 = 12
        assertEquals(12.0f, player.getSaturation(), 0.001, "changer set/add/remove on saturation");
        assertEquals(40, player.getFireTicks(), "changer set on fire ticks");
    }
}
