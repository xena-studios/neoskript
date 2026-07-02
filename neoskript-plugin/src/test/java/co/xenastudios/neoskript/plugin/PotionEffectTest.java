package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for potion-effect modifiers: ambient, infinite, icon, particles. Verifies against
 * the rebuilt {@link org.bukkit.potion.PotionEffect}'s own field rendering (the read-back conditions
 * like "is ambient" are shadowed by the generic equality comparison, so we assert on the value).
 */
class PotionEffectTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void modifiers() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("pe.sk"), """
                command /pe:
                    trigger:
                        set {_pe} to a potion effect of speed for 10 seconds
                        make {_pe} ambient
                        send "A:%{_pe}%" to player
                        make {_pe} not ambient
                        send "B:%{_pe}%" to player
                        hide the potion icon of {_pe}
                        send "C:%{_pe}%" to player
                        hide the potion particles of {_pe}
                        send "D:%{_pe}%" to player
                        make {_pe} infinite
                        send "E:%{_pe}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "pe");
        List<String> seen = new ArrayList<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(line(seen, "A:").contains("ambient=true"), "make ... ambient sets ambient; got " + seen);
        assertTrue(line(seen, "B:").contains("ambient=false"), "make ... not ambient clears ambient");
        assertTrue(line(seen, "C:").contains("icon=false"), "hiding the icon clears the icon flag");
        assertTrue(line(seen, "D:").contains("particles=false"), "hiding particles clears the particles flag");
        assertTrue(line(seen, "E:").contains("duration=-1"), "make ... infinite sets an infinite (-1) duration");
    }

    private static String line(List<String> seen, String prefix) {
        return seen.stream().filter(x -> x.startsWith(prefix)).findFirst().orElse("");
    }
}
