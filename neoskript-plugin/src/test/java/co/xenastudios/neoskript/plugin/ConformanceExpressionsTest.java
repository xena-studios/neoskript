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
 * Conformance coverage for the expressions the audit confirmed implemented. Each is evaluated via
 * {@code set {_x} to <expression>}; context-only values (event values outside an event, args without
 * input) evaluate to null without error. Reaching the marker proves all parsed and evaluated.
 */
class ConformanceExpressionsTest {

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
    void implementedExpressionsParseAndEvaluate() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conf_expr.sk"), """
                command /confexpr:
                    trigger:
                        set {_l::1} to "banana"
                        set {_l::2} to "apple"
                        set {_x} to name of player
                        set {_x} to player's name
                        set {_x} to uppercase "hi"
                        set {_x} to lowercase "HI"
                        set {_x} to length of "abc"
                        set {_x} to join {_l::*} with ", "
                        set {_x} to "a,b" split at ","
                        set {_x} to first element of {_l::*}
                        set {_x} to last element of {_l::*}
                        set {_x} to random element of {_l::*}
                        set {_x} to reversed {_l::*}
                        set {_x} to sorted {_l::*}
                        set {_x} to size of {_l::*}
                        set {_x} to amount of {_l::*}
                        set {_x} to random number from 1 to 10
                        set {_x} to now
                        set {_x} to type of player
                        set {_x} to velocity of player
                        set {_x} to x of location of player
                        set {_x} to y of location of player
                        set {_x} to z of location of player
                        set {_x} to time of world of player
                        set {_x} to gamemode of player
                        set {_x} to health of player
                        set {_x} to console
                        set {_x} to sender
                        set {_x} to arg-1
                        set {_x} to args
                        loop {_l::*}:
                            set {_lv} to loop-value
                        loop 2 times:
                            set {_li} to loop-number
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "confexpr");

        boolean done = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("DONE")) {
                done = true;
            }
        }
        assertTrue(done, "the expressions trigger should run to completion");
    }
}
