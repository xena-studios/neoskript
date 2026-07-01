package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
/** Regression: `set {list::*} to <multi-value>` stores every value, not just the first. */
class ListAssignTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void chk() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(s);
        Files.writeString(s.resolve("la.sk"), """
                command /la:
                    trigger:
                        set {_src::1} to "a"
                        set {_src::2} to "b"
                        set {_src::3} to "c"
                        set {_copy::*} to {_src::*}
                        loop {_copy::*}:
                            send "C:%loop-value%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p, "la");
        java.util.Set<String> seen=new java.util.HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("C:a") && seen.contains("C:b") && seen.contains("C:c"),
                "set {list::*} to another list copies all elements; got "+seen);
    }
}
