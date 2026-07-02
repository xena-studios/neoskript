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

/** Behaviour tests for the classinfo-argument engine feature: 'parsed as %type%' and '%type% value of'. */
class ClassInfoTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void parseAndValue() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("ci.sk"), """
                command /ci:
                    trigger:
                        set {_n} to "42" parsed as number
                        send "PARSE:%{_n}%" to player
                        set {_bad} to "abc" parsed as number
                        send "BAD:%{_bad}%" to player
                        set {_v} to number value of "7"
                        send "VAL:%{_v}%" to player
                        set {_g} to gamemode value of "creative"
                        send "GM:%{_g}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "ci");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("PARSE:42"), "\"42\" parsed as number = 42; got " + seen);
        assertTrue(seen.contains("BAD:<none>"), "\"abc\" parsed as number yields nothing");
        assertTrue(seen.contains("VAL:7"), "number value of \"7\" = 7");
        assertTrue(seen.contains("GM:creative"), "gamemode value of \"creative\" = creative");
    }
}
