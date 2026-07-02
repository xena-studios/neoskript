package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TagTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("t.sk"), """
                command /tag:
                    trigger:
                        broadcast "ALLC:%size of (all minecraft block tags)%"
                        set {_t} to minecraft block tag "wool"
                        broadcast "NAMED:%{_t}%"
                        set {_o::*} to minecraft block tags of white_wool
                        broadcast "OF:%{_o::*}%"
                        broadcast "DPACK:%size of (all datapack block tags)%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"tag");
        Map<String,String> m=new HashMap<>();String x;
        while((x=p.nextMessage())!=null){int i=x.indexOf(':');if(i>0&&x.matches("(ALLC|NAMED|OF|DPACK):.*"))m.put(x.substring(0,i),x.substring(i+1));}
        assertTrue(Integer.parseInt(m.getOrDefault("ALLC","0"))>100, "many block tags: "+m.get("ALLC"));
        assertEquals("minecraft:wool", m.get("NAMED"), "named tag renders key");
        assertTrue(m.getOrDefault("OF","").contains("minecraft:wool"), "wool tags of white_wool: "+m.get("OF"));
        assertEquals("0", m.get("DPACK"), "no datapack tags in MockBukkit");
    }
}
