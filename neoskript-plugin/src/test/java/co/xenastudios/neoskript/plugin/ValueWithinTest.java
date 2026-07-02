package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ValueWithinTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("v.sk"), """
                command /vw:
                    trigger:
                        set {_m::1} to 5
                        set {_m::2} to "hello"
                        set {_m::3} to 10
                        set {_nums::*} to numbers within {_m::*}
                        send "NUMCOUNT:%size of {_nums::*}%" to player
                        send "SUM:%{_nums::1} + {_nums::2}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();server.dispatchCommand(p,"vw");
        Set<String> seen=new HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("NUMCOUNT:2"), "only the two numbers are kept; got "+seen);
        assertTrue(seen.contains("SUM:15"), "the kept numbers are 5 and 10");
    }
}
