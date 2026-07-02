package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SetsTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("st.sk"), """
                command /sets:
                    trigger:
                        broadcast "GMC:%size of (all gamemodes)%"
                        set {_g::*} to every gamemode
                        broadcast "GVALS:%{_g::*}%"
                        set {_e::*} to all entitytypes
                        broadcast "ECOUNT:%size of {_e::*}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"sets");
        Map<String,String> m=new HashMap<>();String x;
        while((x=p.nextMessage())!=null){int i=x.indexOf(':');if(i>0&&x.matches("(GMC|GVALS|ECOUNT):.*"))m.put(x.substring(0,i),x.substring(i+1));}
        assertEquals("4", m.get("GMC"), "4 gamemodes");
        String g=m.getOrDefault("GVALS","").toLowerCase();
        for(String v:new String[]{"survival","creative","adventure","spectator"}) assertTrue(g.contains(v), "gamemodes contain "+v+": "+g);
        assertTrue(Integer.parseInt(m.getOrDefault("ECOUNT","0"))>20, "many entity types: "+m.get("ECOUNT"));
    }
}
