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
 * Conformance coverage for the events the audit confirmed implemented. All event names are declared
 * in one file alongside an {@code on join} marker; if any name failed to resolve the whole file would
 * be rejected and the marker would never fire — so receiving it proves every event name registered.
 */
class ConformanceEventsTest {

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
    void implementedEventsRegister() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conf_events.sk"), """
                on join:
                    send "JOINED" to player

                on level change:
                    set {_x} to 1
                on lightning strike:
                    set {_x} to 1
                on player move:
                    set {_x} to 1
                on item pickup:
                    set {_x} to 1
                on block place:
                    set {_x} to 1
                on world change:
                    set {_x} to 1
                on sneak toggle:
                    set {_x} to 1
                on creature spawn:
                    set {_x} to 1
                on sprint toggle:
                    set {_x} to 1
                on tame:
                    set {_x} to 1
                on entity target:
                    set {_x} to 1
                on teleport:
                    set {_x} to 1
                on vehicle enter:
                    set {_x} to 1
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        boolean joined = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("JOINED")) {
                joined = true;
            }
        }
        assertTrue(joined, "all event names must resolve (else the file is rejected and join never fires)");
    }
}
