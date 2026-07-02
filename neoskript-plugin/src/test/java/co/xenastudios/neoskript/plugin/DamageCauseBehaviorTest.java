package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DamageCauseBehaviorTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("dmg.sk"), """
                on damage:
                    if the damage was caused by fall:
                        broadcast "FELL"
                    if the damage was caused by fire:
                        broadcast "BURNED"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        var event=new EntityDamageEvent(p, DamageCause.FALL, 5.0);
        server.getPluginManager().callEvent(event);
        Set<String> seen=new HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        System.out.println("SEEN="+seen);
        assertTrue(seen.contains("FELL"), "damage caused by fall triggers; got "+seen);
        assertFalse(seen.contains("BURNED"), "fall damage is not fire damage");
    }
}
