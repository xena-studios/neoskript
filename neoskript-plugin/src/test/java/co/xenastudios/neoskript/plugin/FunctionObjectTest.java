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

/** Behaviour test for function objects: function named, run, and result of. */
class FunctionObjectTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void functionObjects() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("fn.sk"), """
                function triple(n):
                    return {_n} * 3

                function markit():
                    set {marked} to 77

                command /fn:
                    trigger:
                        set {_ref} to function named "triple"
                        set {_r} to result of running {_ref} with arguments 4
                        send "RESULT:%{_r}%" to player
                        run (function named "markit")
                        send "SIDE:%{marked}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "fn");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("RESULT:12"), "result of running triple(4) is 12; got " + seen);
        assertTrue(seen.contains("SIDE:77"), "running markit() sets the global via its side effect");
    }
}
