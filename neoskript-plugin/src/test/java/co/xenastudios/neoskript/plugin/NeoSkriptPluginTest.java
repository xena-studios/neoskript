package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoSkriptPluginTest {

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
    void enablesAndRegistersBuiltinSyntax() {
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.registry().size() > 0, "built-in syntax should be registered");
        assertNotNull(plugin.platform());
    }

    @Test
    void runsTheJoinTriggerFromTheGeneratedExampleScript() {
        // The generated example.sk has `on join:` sending a welcome message to the player.
        PlayerMock player = server.addPlayer();

        boolean welcomed = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("Welcome")) {
                welcomed = true;
                break;
            }
        }
        assertTrue(welcomed, "joining player should receive the welcome message from example.sk");
    }
}
