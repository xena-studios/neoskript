package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for bugs found during the audit. */
class AuditFixesTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void auditFixes() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("audit.sk"), """
                command /audit:
                    trigger:
                        set {_loc} to location at (1, 2, 3) in (world of player)
                        send "LOC:%{_loc}%" to player
                        if player is not poisoned:
                            send "NOT-POISONED" to player
                        if player is poisoned:
                            send "POISONED-BAD" to player
                        spawn a zombie at location of player
                        set {_z} to last spawned zombie
                        set {_f} to food level of {_z}
                        send "CAST-SAFE" to player
                        set display name of player to "Boss"
                        send "DN:%player's display name%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "audit");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.stream().anyMatch(x -> x.startsWith("LOC:") && x.contains("1") && x.contains("2") && x.contains("3")),
                "location with literal parens now parses and builds a location (\\( escape); got " + seen);
        assertTrue(seen.contains("NOT-POISONED"), "'is not poisoned' is true when the player isn't poisoned");
        assertFalse(seen.contains("POISONED-BAD"), "'is poisoned' is false when the player isn't poisoned");
        assertTrue(seen.contains("CAST-SAFE"),
                "player-only properties on a non-player entity return null instead of throwing a CCE");
        assertTrue(seen.contains("DN:Boss"), "player's display name reflects the set display name");
    }
}
