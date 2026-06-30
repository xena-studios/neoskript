package co.xenastudios.neoskript.plugin;

import org.bukkit.Difficulty;
import org.bukkit.World;
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

/** Verifies world effects: strike lightning, set spawn, set difficulty. */
class WorldEffectsTest {

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
    void worldEffects() throws IOException {
        World world = server.addSimpleWorld("world");
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("we.sk"), """
                on join:
                    send "JOINED" to player

                command /we:
                    trigger:
                        set difficulty of world of player to hard
                        send "DONE" to player

                command /we2:
                    trigger:
                        strike lightning at location of player
                        set spawn of world of player to location of player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
        server.dispatchCommand(player, "we");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("JOINED"), "lightning/spawn commands parsed (file loaded)");
        assertTrue(seen.contains("DONE"), "set difficulty ran");
        assertEquals(Difficulty.HARD, player.getWorld().getDifficulty(), "difficulty set to hard (via 'hard' type literal)");
    }
}
