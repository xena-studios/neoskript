package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ListStringTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("ls.sk"), """
                command /ls:
                    trigger:
                        set {_l::1} to "a"
                        set {_l::2} to "b"
                        set {_l::3} to "c"
                        broadcast "J:%{_l::*}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"ls");
        String got=null,x; while((x=p.nextMessage())!=null) if(x.startsWith("J:")) got=x.substring(2);
        assertEquals("a, b and c", got, "list joins as 'a, b and c'");
    }
}
