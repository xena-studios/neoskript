package co.xenastudios.neoskript.plugin;

import org.bukkit.GameMode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySyntaxTest {

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
    void playerEffectsConditionsAndExpressionsWork() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("buff.sk"), """
                command /buff:
                    trigger:
                        set health of player to 5
                        feed player
                        op player
                        set gamemode of player to creative
                        if player is op:
                            send "you are op" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        player.setOp(false);
        server.dispatchCommand(player, "buff");

        assertEquals(5.0, player.getHealth());
        assertEquals(20, player.getFoodLevel());
        assertTrue(player.isOp());
        assertEquals(GameMode.CREATIVE, player.getGameMode());

        boolean told = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("you are op")) {
                told = true;
                break;
            }
        }
        assertTrue(told, "the 'player is op' condition should have passed");
    }
}
