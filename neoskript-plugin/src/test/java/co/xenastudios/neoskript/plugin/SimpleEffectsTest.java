package co.xenastudios.neoskript.plugin;

import org.bukkit.Material;
import org.bukkit.entity.Item;
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

/** Verifies hand-implemented simple effects: swing hand, drop, kick. */
class SimpleEffectsTest {

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
    void dropAndKickAndSwing() throws IOException {
        server.addSimpleWorld("world");
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("sfx.sk"), """
                command /sfx:
                    trigger:
                        make player swing main hand
                        drop item("diamond") at location of player
                        send "DONE" to player

                command /boot:
                    trigger:
                        kick player because of "bye"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "sfx");

        boolean done = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("DONE")) {
                done = true;
            }
        }
        assertTrue(done, "swing + drop ran to completion");
        // a dropped item entity exists in the world
        assertTrue(player.getWorld().getEntitiesByClass(Item.class).stream()
                        .anyMatch(i -> i.getItemStack().getType() == Material.DIAMOND),
                "drop spawned a diamond item entity");

        // kick removes the player
        PlayerMock victim = server.addPlayer("Victim");
        server.dispatchCommand(victim, "boot");
        assertTrue(!victim.isOnline(), "kick disconnects the player");
    }
}
