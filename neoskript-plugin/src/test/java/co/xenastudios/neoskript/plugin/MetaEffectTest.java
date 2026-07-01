package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
/** Behaviour tests for the list/meta effects: sort, transform, do-if. */
class MetaEffectTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void effects() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(s);
        Files.writeString(s.resolve("le.sk"), """
                command /le:
                    trigger:
                        set {_l::1} to 3
                        set {_l::2} to 1
                        set {_l::3} to 2
                        sort {_l::*}
                        send "SORTED:%{_l::1}%" to player
                        transform {_l::*} using [input * 10]
                        send "MAPPED:%{_l::1}%" to player
                        send "DOIF" to player if player is online
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p, "le");
        java.util.Set<String> seen=new java.util.HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("SORTED:1"), "sort ascending puts 1 first; got "+seen);
        assertTrue(seen.contains("MAPPED:10"), "transform multiplies first element to 10");
        assertTrue(seen.contains("DOIF"), "do-if runs when condition true");
    }
}
