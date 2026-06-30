package co.xenastudios.neoskript.plugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSyntaxTest {

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
    void worldTimeInventoryAndAliveSyntax() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("wt.sk"), """
                command /wt:
                    trigger:
                        set time of world of player to 6000
                        clear inventory of player
                        if player is alive:
                            send "you are alive" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND));
        server.dispatchCommand(player, "wt");

        assertEquals(6000L, player.getWorld().getTime());
        assertFalse(player.getInventory().contains(Material.DIAMOND), "inventory should be cleared");

        boolean alive = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("you are alive")) {
                alive = true;
                break;
            }
        }
        assertTrue(alive, "the 'player is alive' condition should pass");
    }
}
