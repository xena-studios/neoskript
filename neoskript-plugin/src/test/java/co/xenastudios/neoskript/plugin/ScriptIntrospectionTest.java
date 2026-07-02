package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScriptIntrospectionTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void scripts() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("other.sk"), "# a second script\n", StandardCharsets.UTF_8);
        Files.writeString(s.resolve("main.sk"), """
                command /sc:
                    trigger:
                        send "COUNT:%size of (all scripts)%" to player
                        if script "main.sk" is loaded:
                            send "MAIN-LOADED" to player
                        if script "ghost.sk" is not loaded:
                            send "GHOST-ABSENT" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();server.dispatchCommand(p,"sc");
        Set<String> seen=new HashSet<>(); String m;
        while((m=p.nextMessage())!=null) seen.add(m);
        int count = seen.stream().filter(x -> x.startsWith("COUNT:")).map(x -> Integer.parseInt(x.substring(6)))
                .findFirst().orElse(0);
        assertTrue(count >= 2, "at least the two written scripts are listed; got "+seen);
        assertTrue(seen.contains("MAIN-LOADED"), "main.sk is loaded");
        assertTrue(seen.contains("GHOST-ABSENT"), "ghost.sk is not loaded");
    }
}
