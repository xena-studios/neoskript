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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GearSyntaxTest {

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
    void typeOfAndIsWearing() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("gear.sk"), """
                command /gear:
                    trigger:
                        set {_t} to type of item("diamond")
                        send "type is %{_t}%" to player
                        if player is wearing item("diamond_helmet"):
                            send "wearing helmet" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        server.dispatchCommand(player, "gear");

        Set<String> messages = new HashSet<>();
        String message;
        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }
        assertTrue(messages.stream().anyMatch(m -> m.contains("type is diamond")), "type of item should be 'diamond'");
        assertTrue(messages.stream().anyMatch(m -> m.contains("wearing helmet")), "is wearing should detect the helmet");
    }
}
