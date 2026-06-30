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
 * Conformance coverage for the conditions the audit confirmed implemented. Each condition is used in
 * an {@code if}; reaching the trailing marker proves every condition parsed and evaluated without
 * error (the branch outcome itself is irrelevant here).
 */
class ConformanceConditionsTest {

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
    void implementedConditionsParseAndEvaluate() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conf_conditions.sk"), """
                command /confconditions:
                    trigger:
                        set {_n} to 5
                        set {_s} to "abc"
                        if {_n} is set:
                            set {_ok} to 1
                        if 5 is greater than 3:
                            set {_ok} to 1
                        if chance of 50 percent:
                            set {_ok} to 1
                        if "abc" contains "b":
                            set {_ok} to 1
                        if "abc" matches "a.c":
                            set {_ok} to 1
                        if "abc" starts with "a":
                            set {_ok} to 1
                        if player has permission "x":
                            set {_ok} to 1
                        if player has played before:
                            set {_ok} to 1
                        if player can fly:
                            set {_ok} to 1
                        if player is alive:
                            set {_ok} to 1
                        if player is banned:
                            set {_ok} to 1
                        if player is blocking:
                            set {_ok} to 1
                        if player is burning:
                            set {_ok} to 1
                        if player is flying:
                            set {_ok} to 1
                        if player is gliding:
                            set {_ok} to 1
                        if player is on ground:
                            set {_ok} to 1
                        if player is online:
                            set {_ok} to 1
                        if player is op:
                            set {_ok} to 1
                        if player is sleeping:
                            set {_ok} to 1
                        if player is sneaking:
                            set {_ok} to 1
                        if player is sprinting:
                            set {_ok} to 1
                        if player is swimming:
                            set {_ok} to 1
                        if player is whitelisted:
                            set {_ok} to 1
                        if player is in water:
                            set {_ok} to 1
                        if player is in a vehicle:
                            set {_ok} to 1
                        if player is leashed:
                            set {_ok} to 1
                        if player is tamed:
                            set {_ok} to 1
                        if player is holding item("diamond"):
                            set {_ok} to 1
                        if player is wearing item("diamond helmet"):
                            set {_ok} to 1
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "confconditions");

        boolean done = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("DONE")) {
                done = true;
            }
        }
        assertTrue(done, "the conditions trigger should run to completion");
    }
}
