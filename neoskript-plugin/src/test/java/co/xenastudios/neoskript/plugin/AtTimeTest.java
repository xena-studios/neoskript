package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;import java.util.logging.*;
import static org.junit.jupiter.api.Assertions.*;
class AtTimeTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        List<String> logs=new ArrayList<>();
        plugin.getLogger().addHandler(new Handler(){public void publish(LogRecord r){logs.add(r.getMessage());}public void flush(){}public void close(){}});
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("t.sk"), """
                at 18:00:
                    broadcast "TIME18"
                at 12:00 real time:
                    broadcast "NOON-REAL"
                at 6:00 in "world":
                    broadcast "DAWN-WORLD"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        boolean anyFail=logs.stream().anyMatch(m->m!=null&&m.contains("Failed to parse"));
        assertFalse(anyFail, "all 'at time' forms parse; logs="+logs);
        PlayerMock p=server.addPlayer();
        p.getWorld().setTime(11995L); // just before 18:00 (12000 ticks)
        server.getScheduler().performTicks(10L); // advance through 12000
        Set<String> seen=new HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("TIME18"), "the 'at 18:00' trigger fires when the world reaches 12000 ticks; got "+seen);
    }
}
