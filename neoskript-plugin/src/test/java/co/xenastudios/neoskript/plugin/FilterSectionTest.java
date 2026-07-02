package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FilterSectionTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    Set<String> nums(String csv){ Set<String> s=new TreeSet<>(); for(String t:csv.split(",|\\band\\b")){t=t.trim();if(!t.isEmpty())s.add(t.replace(".0",""));} return s; }
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("f.sk"), """
                command /flt:
                    trigger:
                        set {_l::1} to 5
                        set {_l::2} to 12
                        set {_l::3} to 3
                        set {_l::4} to 20
                        filter {_l::*} to match:
                            input is greater than 4
                        broadcast "ALL1:%{_l::*}%"
                        set {_a::1} to 5
                        set {_a::2} to 30
                        set {_a::3} to 100
                        filter {_a::*} to match all:
                            input is greater than 10
                            input is less than 50
                        broadcast "ALL2:%{_a::*}%"
                        set {_y::1} to 5
                        set {_y::2} to 30
                        set {_y::3} to 100
                        filter {_y::*} to match any:
                            input is greater than 50
                            input is less than 10
                        broadcast "ANY:%{_y::*}%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"flt");
        Map<String,String> m=new HashMap<>();String x;
        while((x=p.nextMessage())!=null){int i=x.indexOf(':');if(i>0&&x.matches("(ALL1|ALL2|ANY):.*"))m.put(x.substring(0,i),x.substring(i+1));}
        assertEquals(Set.of("5","12","20"), nums(m.getOrDefault("ALL1","")), "keep >4: "+m.get("ALL1"));
        assertEquals(Set.of("30"), nums(m.getOrDefault("ALL2","")), "keep >10 and <50: "+m.get("ALL2"));
        assertEquals(Set.of("5","100"), nums(m.getOrDefault("ANY","")), "keep >50 or <10: "+m.get("ANY"));
    }
}
