package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScriptExprTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s.resolve("sub"));
        Files.writeString(s.resolve("sub").resolve("bar.sk"), "# a script in a subdirectory\n", StandardCharsets.UTF_8);
        Files.writeString(s.resolve("foo.sk"), """
                command /si:
                    trigger:
                        set {_c} to the current script
                        broadcast "CUR:%{_c}%"
                        set {_n} to script named "foo"
                        broadcast "NAMED:%{_n}%"
                        set {_d} to scripts in directory "sub"
                        broadcast "DIR:%{_d}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"si");
        Map<String,String> m=new HashMap<>(); String x;
        while((x=p.nextMessage())!=null){ int i=x.indexOf(':'); if(i>0&&x.matches("(CUR|NAMED|DIR):.*")) m.put(x.substring(0,i),x.substring(i+1)); }
        assertEquals("foo.sk", m.get("CUR"), "current script");
        assertEquals("foo.sk", m.get("NAMED"), "script named foo");
        assertTrue(m.getOrDefault("DIR","").replace('\\','/').contains("sub/bar.sk"), "scripts in sub: "+m.get("DIR"));
    }
}
