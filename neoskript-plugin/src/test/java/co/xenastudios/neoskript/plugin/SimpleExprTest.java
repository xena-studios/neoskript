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

/** Verifies the hand-implemented simple expressions (server values, vectors, settable total exp). */
class SimpleExprTest {

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
    void simpleExpressions() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("se.sk"), """
                command /se:
                    trigger:
                        set total experience of player to 100
                        if total experience of player is 100:
                            send "EXP" to player
                        set {_m} to max players
                        if {_m} is greater than 0:
                            send "MAX" to player
                        set {_c} to online player count
                        if {_c} is greater than 0:
                            send "CNT" to player
                        set {_d} to max durability of item("diamond sword")
                        if {_d} is greater than 0:
                            send "DUR" to player
                        set {_v} to vector from location(0, 0, 0) to location(0, 5, 0)
                        if y of {_v} is 5:
                            send "VEC" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "se");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("EXP"), "total experience get+set via changer");
        assertTrue(seen.contains("MAX"), "max players");
        assertTrue(seen.contains("CNT"), "online player count");
        assertTrue(seen.contains("DUR"), "max durability of item");
        assertTrue(seen.contains("VEC"), "vector from A to B");
    }
}
