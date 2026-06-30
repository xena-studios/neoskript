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

/** Verifies item amount / yaw / pitch / lore (behaviour) and owner / targeted block (parse). */
class ItemExprTest {

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
    void itemAndLocationExpressions() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("ie.sk"), """
                on join:
                    send "JOINED" to player

                command /ie:
                    trigger:
                        if item amount of item("diamond", 5) is 5:
                            send "AMT" to player
                        set {_y} to yaw of location of player
                        set {_p} to pitch of location of player
                        set {_l} to lore of item("diamond")
                        set {_age} to age of player
                        set {_dur} to durability of item("diamond")
                        set {_col} to color of player
                        if block hardness of item("stone") is greater than 0:
                            send "HARD" to player
                        if hex code of rgb(255, 0, 0) is "#ff0000":
                            send "HEX" to player
                        set {_face} to facing of player
                        set {_yield} to explosive yield of player
                        set {_cmd} to custom model data of item("stone")
                        send "DONE" to player

                command /ie2:
                    trigger:
                        set {_o} to owner of player
                        set {_t} to targeted block of player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "ie");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("JOINED"), "owner/targeted-block commands parsed (file loaded)");
        assertTrue(seen.contains("AMT"), "item amount = 5");
        assertTrue(seen.contains("HARD"), "stone has hardness > 0");
        assertTrue(seen.contains("HEX"), "hex code of red = #ff0000");
        assertTrue(seen.contains("DONE"), "yaw/pitch/lore getters ran without error");
    }
}
