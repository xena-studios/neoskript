package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
/** Behaviour tests for the list/meta expressions: filter, map/transform, ternary, whether. */
class MetaExpressionTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void meta() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(s);
        Files.writeString(s.resolve("meta.sk"), """
                command /meta:
                    trigger:
                        set {_l::1} to 5
                        set {_l::2} to 15
                        set {_f::*} to {_l::*} where [input is greater than 10]
                        loop {_f::*}:
                            send "F:%loop-value%" to player
                        set {_t::*} to {_l::*} transformed using [input * 2]
                        loop {_t::*}:
                            send "T:%loop-value%" to player
                        set {_v} to "yes" if player is online else "no"
                        send "V:%{_v}%" to player
                        set {_w} to whether player is online
                        send "W:%{_w}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p, "meta");
        java.util.Set<String> seen=new java.util.HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("F:15"), "filter kept 15; got "+seen);
        assertFalse(seen.contains("F:5"), "filter dropped 5");
        assertTrue(seen.contains("V:yes"), "ternary yes");
        assertTrue(seen.contains("W:true"), "whether true");
    }
}
