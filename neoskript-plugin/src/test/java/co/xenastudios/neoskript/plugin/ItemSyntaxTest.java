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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSyntaxTest {

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
    void giveHasAndTakeItems() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("kit.sk"), """
                command /kit:
                    trigger:
                        give item("diamond", 3) to player
                        if player has item("diamond", 3):
                            send "has diamonds" to player
                        take item("diamond", 1) from player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "kit");

        boolean told = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("has diamonds")) {
                told = true;
                break;
            }
        }
        assertTrue(told, "the 'has item' condition should have passed after giving 3 diamonds");

        // gave 3, took 1 -> at least 2 remain, but not 3.
        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 2));
        assertFalse(player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 3));
    }
}
