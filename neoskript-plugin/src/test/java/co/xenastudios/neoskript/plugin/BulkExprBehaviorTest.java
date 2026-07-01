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

/** Behaviour-tests the MockBukkit-runnable subset of the source-grounded bulk expressions. */
class BulkExprBehaviorTest {

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
    void bulkBehaviour() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("be.sk"), """
                command /be:
                    trigger:
                        if the UUID of player is set:
                            send "UUID" to player
                        if the world of player is set:
                            send "WORLD" to player
                        set {_m} to the middle of location of player
                        if {_m} is set:
                            send "MID" to player
                        if the maximum integer value is 2147483647:
                            send "MAXI" to player
                        if the minimum integer value is -2147483648:
                            send "MINI" to player
                        set {_p} to pi
                        if {_p} is set:
                            send "PI" to player
                        set {_w} to all of the worlds
                        if {_w} is set:
                            send "WORLDS" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "be");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("UUID"), "uuid of player");
        assertTrue(seen.contains("WORLD"), "world of player");
        assertTrue(seen.contains("MID"), "middle of location");
        assertTrue(seen.contains("MAXI"), "maximum integer value");
        assertTrue(seen.contains("MINI"), "minimum integer value");
        assertTrue(seen.contains("PI"), "pi");
        assertTrue(seen.contains("WORLDS"), "all of the worlds");
    }
}
