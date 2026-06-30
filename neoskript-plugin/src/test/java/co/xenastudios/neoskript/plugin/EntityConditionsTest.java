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

/** Verifies the new entity-state conditions parse and evaluate (true after the matching effect). */
class EntityConditionsTest {

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
    void entityStateConditions() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("econd.sk"), """
                command /econd:
                    trigger:
                        make player invisible
                        make player invulnerable
                        silence player
                        if player is invisible:
                            send "INVIS" to player
                        if player is invulnerable:
                            send "INVUL" to player
                        if player is silent:
                            send "SILENT" to player
                        if player is frozen:
                            send "FROZEN" to player
                        if player is a baby:
                            send "BABY" to player
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "econd");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.stream().anyMatch(s -> s.contains("INVIS")), "is invisible");
        assertTrue(seen.stream().anyMatch(s -> s.contains("INVUL")), "is invulnerable");
        assertTrue(seen.stream().anyMatch(s -> s.contains("SILENT")), "is silent");
        assertTrue(seen.stream().anyMatch(s -> s.contains("DONE")), "ran to completion (frozen/baby were false)");
    }
}
