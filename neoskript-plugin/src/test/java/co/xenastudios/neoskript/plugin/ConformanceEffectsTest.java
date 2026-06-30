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

/**
 * Conformance coverage for the built-in effects the audit confirmed implemented. Each effect is
 * exercised in its valid context (trigger / loop / function / event); reaching the trailing marker
 * proves the whole batch parsed and executed without error.
 */
class ConformanceEffectsTest {

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

    private boolean sawMessage(PlayerMock player, String needle) {
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void implementedEffectsParseAndRun() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conf_effects.sk"), """
                function conf_ret() :: number:
                    return 5

                command /confeffects:
                    trigger:
                        broadcast "b"
                        send "s" to player
                        message "m" to player
                        send actionbar "a" to player
                        send title "t" to player
                        set {_n} to 5
                        add 2 to {_n}
                        remove 1 from {_n}
                        set {_called} to conf_ret()
                        heal player
                        feed player
                        ignite player for 1 seconds
                        extinguish player
                        op player
                        deop player
                        allow flight for player
                        disallow flight for player
                        set {_s} to "banana"
                        replace all "a" with "o" in {_s}
                        play sound "entity.item.pickup" to player
                        teleport player to player
                        make player execute command "me hello"
                        loop 3 times:
                            if loop-number is 1:
                                continue
                            if loop-number is 3:
                                exit loop
                        send "DONE" to player
                        delete {_n}
                        kill player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "confeffects");
        StringBuilder seen = new StringBuilder();
        String m;
        boolean done = false;
        while ((m = player.nextMessage()) != null) {
            seen.append('[').append(m).append(']');
            if (m.contains("DONE")) {
                done = true;
            }
        }
        assertTrue(done, "the effects trigger should run to completion; messages seen: " + seen);
    }
}
