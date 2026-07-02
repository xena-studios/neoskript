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

/** Behaviour test for the armor items of an entity. */
class ArmorTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void armorItems() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("ar.sk"), """
                command /ar:
                    trigger:
                        send "BEFORE:%size of (armor of player)%" to player
                        equip player with diamond helmet and diamond boots
                        send "AFTER:%size of (armor of player)%" to player
                        set {_armor::*} to armor of player
                        send "PIECES:%{_armor::1}% %{_armor::2}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "ar");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("BEFORE:0"), "no armor worn initially; got " + seen);
        assertTrue(seen.contains("AFTER:2"), "two armor pieces after equipping helmet and boots");
        assertTrue(seen.stream().anyMatch(x -> x.startsWith("PIECES:") && x.contains("diamond")),
                "the armor list contains the equipped diamond pieces");
    }
}
