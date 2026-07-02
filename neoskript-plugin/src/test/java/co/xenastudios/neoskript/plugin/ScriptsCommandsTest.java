package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScriptsCommandsTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.createDirectories(s.resolve("sub"));
        Files.writeString(s.resolve("sub/nested.sk"), "# nested\n", StandardCharsets.UTF_8);
        Files.writeString(s.resolve("main.sk"), """
                command /scc:
                    trigger:
                        set {_names::*} to (all scripts without paths)
                        send "STRIP:%{_names::1}%|%{_names::2}%" to player
                        if all commands contains "nsk":
                            send "HAS-NSK" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();server.dispatchCommand(p,"scc");
        Set<String> seen=new HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        // "without paths" must strip the subdirectory: no "/" or "\" in any rendered name.
        String strip = seen.stream().filter(x->x.startsWith("STRIP:")).findFirst().orElse("");
        assertFalse(strip.contains("/") || strip.contains("\\"), "script names have no path component; got "+seen);
        assertTrue(strip.contains("nested.sk"), "the nested script appears by filename only; got "+seen);
        assertTrue(seen.contains("HAS-NSK"), "all commands includes the nsk command; got "+seen);
    }
}
