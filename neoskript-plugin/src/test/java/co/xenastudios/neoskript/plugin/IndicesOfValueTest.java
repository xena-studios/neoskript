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

/** Behaviour test for 'index/indices of %value% in %list%'. */
class IndicesOfValueTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void indicesOfValue() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("iov.sk"), """
                command /iov:
                    trigger:
                        set {_l::1} to "a"
                        set {_l::2} to "b"
                        set {_l::3} to "b"
                        set {_target} to "b"
                        send "FIRST:%index of {_target} in {_l::*}%" to player
                        send "LAST:%last index of {_target} in {_l::*}%" to player
                        set {_all::*} to indices of {_target} in {_l::*}
                        send "ALL1:%{_all::1}%" to player
                        send "ALL2:%{_all::2}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "iov");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("FIRST:2"), "first \"b\" is at list key 2; got " + seen);
        assertTrue(seen.contains("LAST:3"), "last \"b\" is at list key 3");
        assertTrue(seen.contains("ALL1:2") && seen.contains("ALL2:3"),
                "all indices of \"b\" are keys 2 and 3");
    }
}
