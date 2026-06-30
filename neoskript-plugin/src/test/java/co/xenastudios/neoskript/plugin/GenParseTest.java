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
 * Parse-conformance for the generated property expressions and boolean conditions: the command body
 * is parsed at reload but never dispatched (their getters call Bukkit APIs MockBukkit doesn't
 * implement). If any pattern failed to parse the whole file is rejected and the join marker won't fire.
 */
class GenParseTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void generatedPatternsParse() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("genparse.sk"), """
                on join:
                    send "JOINED" to player

                command /genparse:
                    trigger:
                        set {_x} to active item of player
                        set {_x} to anvil text input of player
                        set {_x} to arrow knockback strength of player
                        set {_x} to arrow pierce level of player
                        set {_x} to attack cooldown of player
                        set {_x} to bed location of player
                        set {_x} to world border center of player
                        set {_x} to compass target of player
                        set {_x} to world border damage amount of player
                        set {_x} to world border damage buffer of player
                        set {_x} to causing entity of player
                        set {_x} to damage location of player
                        set {_x} to damage type of player
                        set {_x} to direct entity of player
                        set {_x} to food exhaustion of player
                        set {_x} to source location of player
                        set {_x} to difficulty of player
                        set {_x} to billboard of player
                        set {_x} to teleport duration of player
                        set {_x} to view range of player
                        set {_x} to uuid of dropped item owner of player
                        set {_x} to uuid of dropped item thrower of player
                        set {_x} to enchantment cost of player
                        set {_x} to ender chest of player
                        if player can duplicate:
                            set {_y} to 1
                        if player is dashing:
                            set {_y} to 1
                        if player can breed:
                            set {_y} to 1
                        if player can despawn when far away:
                            set {_y} to 1
                        if player can pick up items:
                            set {_y} to 1
                        if player is from a mob spawner:
                            set {_y} to 1
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        boolean joined = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("JOINED")) joined = true;
        }
        assertTrue(joined, "all generated patterns must parse (else file rejected, join never fires)");
    }
}
