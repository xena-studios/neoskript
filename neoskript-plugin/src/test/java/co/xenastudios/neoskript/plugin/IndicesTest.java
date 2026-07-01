package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
class IndicesTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void chk() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(s);
        Files.writeString(s.resolve("ix.sk"), """
                command /ix:
                    trigger:
                        set {_m::apple} to 1
                        set {_m::banana} to 2
                        loop indices of {_m::*}:
                            send "K:%loop-value%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p, "ix");
        java.util.Set<String> seen=new java.util.HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        assertTrue(seen.contains("K:apple") && seen.contains("K:banana"), "indices of yields the keys; got "+seen);
    }
}
