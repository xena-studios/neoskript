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
 * Verifies stop sound, reset title, and make-say effects parse and register (their runtime behaviour
 * relies on Bukkit methods MockBukkit doesn't implement, so this is a parse-conformance check: if any
 * effect's syntax were invalid the whole file would be rejected and the join marker would not fire).
 */
class PlayerEffects2Test {

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
    void miscPlayerEffectsRun() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("pfx2.sk"), """
                on join:
                    send "JOINED" to player

                command /pfx2:
                    trigger:
                        stop all sounds for player
                        reset the title of player
                        make player say "hi there"
                        close inventory of player
                        make player wake up
                        despawn player
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
        assertTrue(joined, "the effects must parse (else the file is rejected and join never fires)");
    }
}
