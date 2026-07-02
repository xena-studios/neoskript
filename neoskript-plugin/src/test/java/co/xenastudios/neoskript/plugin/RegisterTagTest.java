package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RegisterTagTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("ct.sk"), """
                command /ct:
                    trigger:
                        register a custom item tag named "my_stuff" containing diamond and gold ingot
                        broadcast "CCOUNT:%size of (all custom item tags)%"
                        set {_t} to custom item tag "my_stuff"
                        broadcast "CNAME:%{_t}%"
                        set {_o::*} to custom item tags of diamond
                        broadcast "COF:%{_o::*}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"ct");
        Map<String,String> m=new HashMap<>();String x;
        while((x=p.nextMessage())!=null){int i=x.indexOf(':');if(i>0&&x.matches("(CCOUNT|CNAME|COF):.*"))m.put(x.substring(0,i),x.substring(i+1));}
        assertEquals("1", m.get("CCOUNT"), "one custom item tag");
        assertEquals("skript:my_stuff", m.get("CNAME"), "custom tag key");
        assertTrue(m.getOrDefault("COF","").contains("skript:my_stuff"), "diamond is in my_stuff: "+m.get("COF"));
    }
}
