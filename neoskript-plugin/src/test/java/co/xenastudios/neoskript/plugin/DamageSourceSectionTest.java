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

/** Behaviour test for the damage-source section: 'created damage source' aliases the built value. */
class DamageSourceSectionTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void damageSourceSection() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("ds.sk"), """
                command /ds:
                    trigger:
                        set {_d} to a custom damage source with damage type generic:
                            set {_inner} to damage type of (created damage source)
                        send "INNER:%{_inner}%" to player
                        send "OUTER:%damage type of {_d}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "ds");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        // Inside the section body, 'created damage source' resolves to the built source, and its
        // damage type matches the outer assigned value — proving the section value is threaded through.
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("INNER:") && x.contains("generic")),
                "created damage source in the body has the generic damage type; got " + seen);
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("OUTER:") && x.contains("generic")),
                "the assigned damage source has the generic damage type");
    }
}
