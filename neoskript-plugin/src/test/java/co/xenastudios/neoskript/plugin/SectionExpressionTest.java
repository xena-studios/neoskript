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

/** Behaviour test for section expressions: building a potion effect and customising it in the body. */
class SectionExpressionTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void potionEffectSection() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("sec.sk"), """
                command /sec:
                    trigger:
                        set {_p} to a potion effect of speed for 10 minutes:
                            hide the icon of (created potion effect)
                            make (created potion effect) ambient
                        send "SEC:%{_p}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "sec");
        List<String> seen = new ArrayList<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        String line = seen.stream().filter(x -> x.startsWith("SEC:")).findFirst().orElse("");
        assertTrue(line.contains("type=SPEED"), "the section produced a speed potion effect; got " + seen);
        assertTrue(line.contains("icon=false"), "the body hid the icon of the created effect");
        assertTrue(line.contains("ambient=true"), "the body made the created effect ambient");
    }
}
