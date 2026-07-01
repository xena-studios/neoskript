package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
/** Behaviour tests for reduce and except list expressions. */
class ReduceExceptTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void chk() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(s);
        Files.writeString(s.resolve("re.sk"), """
                command /re:
                    trigger:
                        set {_l::1} to 1
                        set {_l::2} to 2
                        set {_l::3} to 3
                        set {_sum} to {_l::*} reduced with [accumulator + input]
                        send "SUM:%{_sum}%" to player
                        set {_b::1} to 2
                        loop {_l::*} except {_b::*}:
                            send "E:%loop-value%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p, "re");
        java.util.Set<String> seen=new java.util.HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("SUM:6"), "reduce sums 1+2+3=6; got "+seen);
        assertTrue(seen.contains("E:1") && seen.contains("E:3"), "except keeps 1 and 3");
        assertTrue(!seen.contains("E:2"), "except removes 2");
    }
}
