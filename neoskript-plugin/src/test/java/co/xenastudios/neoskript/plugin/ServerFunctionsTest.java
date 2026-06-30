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

/** Verifies the server-dependent functions player(), world(), offlineplayer() run. */
class ServerFunctionsTest {

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
    void serverFunctionsResolve() throws IOException {
        server.addSimpleWorld("world");
        PlayerMock steve = server.addPlayer("Steve");

        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("fns.sk"), """
                command /fns:
                    trigger:
                        set {_p} to player("Steve")
                        set {_o} to offlineplayer("Steve")
                        set {_w} to world("world")
                        if {_p} is set:
                            send "P" to player
                        if {_o} is set:
                            send "O" to player
                        if {_w} is set:
                            send "W" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        server.dispatchCommand(steve, "fns");

        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = steve.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("P"), "player() resolves the online player");
        assertTrue(seen.contains("O"), "offlineplayer() resolves");
        assertTrue(seen.contains("W"), "world() resolves the world");
    }
}
