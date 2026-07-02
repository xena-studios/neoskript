package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour test for equippable-component accessors. */
class EquippableTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void accessors() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("eqc.sk"), """
                command /eqc:
                    trigger:
                        set {_c} to a blank equippable component
                        send "SLOT:%equipment slot of {_c}%" to player
                        send "MODEL:%equipped model key of {_c}%" to player
                        send "SHEAR:%shear sound of {_c}%" to player
                        send "ALLOWED:%size of (allowed entities of {_c})%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "eqc");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("SLOT:head"), "a blank equippable component uses the head slot; got " + seen);
        assertTrue(seen.contains("MODEL:<none>"), "a blank component has no model/asset key");
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("SHEAR:")),
                "the shear sound accessor evaluates without error");
        assertTrue(seen.contains("ALLOWED:0"), "a blank component restricts no entities");
    }
}
