package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RecursiveTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("r.sk"), """
                command /rec:
                    trigger:
                        set {_l::1} to "a"
                        set {_l::2::x} to "b"
                        set {_l::2::y} to "c"
                        set {_l::3} to "d"
                        broadcast "RSIZE:%recursive size of {_l::*}%"
                        broadcast "DSIZE:%size of {_l::*}%"
                        set {_v::*} to recursive {_l::*}
                        broadcast "RVALS:%{_v::*}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"rec");
        Map<String,String> m=new HashMap<>();String x;
        while((x=p.nextMessage())!=null){int i=x.indexOf(':');if(i>0&&x.matches("(RSIZE|DSIZE|RVALS):.*"))m.put(x.substring(0,i),x.substring(i+1));}
        assertEquals("4", m.get("RSIZE"), "recursive size counts all leaves");
        String vals=m.getOrDefault("RVALS","");
        for(String v:new String[]{"a","b","c","d"}) assertTrue(vals.contains(v), "recursive values contain "+v+": "+vals);
    }
}
