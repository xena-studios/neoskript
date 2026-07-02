package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FormerFutureTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("tp.sk"), """
                on teleport:
                    broadcast "FORMER:%x of (former location of player)%"
                    broadcast "FUTURE:%x of (future location of player)%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        Location from=p.getLocation();
        Location to=from.clone().add(5,0,0);
        server.getPluginManager().callEvent(new PlayerTeleportEvent(p, from, to));
        double former=Double.NaN, future=Double.NaN; String m;
        while((m=p.nextMessage())!=null){
            if(m.startsWith("FORMER:")) former=Double.parseDouble(m.substring(7));
            if(m.startsWith("FUTURE:")) future=Double.parseDouble(m.substring(7));
        }
        assertFalse(Double.isNaN(former)||Double.isNaN(future), "both fired");
        assertEquals(5.0, future-former, 1e-6, "future location is 5 blocks east of the former (from="+former+" to="+future+")");
    }
}
